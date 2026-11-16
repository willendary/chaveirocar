using System.IO;
using System.Net.Http;
using System.Text.Json;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Push (outbox) + pull (cursor) sobre <see cref="ClienteApi"/>.
/// Divergência vira conflito explícito, nunca sobrescrita silenciosa.
/// Fotos só sobem após o atendimento; downloads são guardados em disco.
/// </summary>
public static class MotorSincronia
{
    public sealed record Resumo(int Enviadas, int Recebidas, int Conflitos, int Falhas);

    private static string PastaFotos()
    {
        var pasta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ChaveiroCar", "fotos");
        Directory.CreateDirectory(pasta);
        return pasta;
    }

    public static async Task<Resumo> SincronizarAsync(
        ArmazenamentoLocal local, ClienteApi api, string empresaId)
    {
        var enviadas = 0;
        var recebidas = 0;
        var conflitos = 0;
        var falhas = 0;

        await CurarConflitosLegadosAsync(local, api, empresaId);

        // PUSH atendimentos + fichas (lote idempotente) e exclusões.
        if (local.Estado.Outbox.Count > 0)
        {
            var loteEnviado = local.Estado.Outbox.ToList();
            var operacoes = loteEnviado.Select(op =>
            {
                var entidade = op.Entidade;
                if (op.Tipo == "exclusao")
                {
                    return (object)new { opId = op.OpId, entidade, tipo = "exclusao", id = op.Uuid, baseRevisao = op.BaseRevisao };
                }
                if (entidade == "ficha")
                {
                    return (object)new { opId = op.OpId, entidade, tipo = "upsert", id = op.Uuid, baseRevisao = op.BaseRevisao, dados = Converter.Dados(op.DadosFicha!) };
                }
                return (object)new { opId = op.OpId, entidade, tipo = "upsert", id = op.Uuid, baseRevisao = op.BaseRevisao, dados = Converter.Dados(op.Dados!) };
            }).ToList();
            var resposta = await api.EnviarLoteAsync(empresaId, operacoes);
            foreach (var resultado in resposta.Resultados)
            {
                var opEnviada = loteEnviado.FirstOrDefault(o => o.OpId == resultado.OpId);
                if (opEnviada is null) continue;
                var opAtual = local.Estado.Outbox.FirstOrDefault(o => o.Uuid == opEnviada.Uuid && o.Tipo == opEnviada.Tipo);
                switch (resultado.Status)
                {
                    case "aplicado":
                        enviadas++;
                        var novaRevisao = resultado.Revisao > 0 ? resultado.Revisao : ExtrairRevisao(resultado.Registro);
                        if (opAtual is not null && opAtual.OpId != resultado.OpId)
                        {
                            // Edição mais recente foi enfileirada enquanto este lote estava em trânsito:
                            // atualiza a revisão base da próxima tentativa sem descartar a pendência local.
                            AtualizarRevisaoNoItem(local, opEnviada.Entidade, opEnviada.Uuid, novaRevisao, pendente: true);
                            opAtual.BaseRevisao = Math.Max(opAtual.BaseRevisao, novaRevisao);
                            if (opAtual.Dados is not null) opAtual.Dados.Revisao = opAtual.BaseRevisao;
                            if (opAtual.DadosFicha is not null) opAtual.DadosFicha.Revisao = opAtual.BaseRevisao;
                        }
                        else
                        {
                            AtualizarRevisaoNoItem(local, opEnviada.Entidade, opEnviada.Uuid, novaRevisao, pendente: false);
                            local.Estado.Conflitos.RemoveAll(c => c.Uuid == opEnviada.Uuid);
                            if (opAtual is not null) local.Estado.Outbox.Remove(opAtual);
                        }
                        break;
                    case "conflito":
                        var excluidoRemoto = RegistroExcluido(resultado.Registro);
                        var revisaoServidor = resultado.Revisao > 0 ? resultado.Revisao : ExtrairRevisao(resultado.Registro);
                        if (opAtual is not null && opAtual.OpId != resultado.OpId && !excluidoRemoto && revisaoServidor > 0)
                        {
                            opAtual.BaseRevisao = Math.Max(opAtual.BaseRevisao, revisaoServidor);
                            AtualizarRevisaoNoItem(local, opEnviada.Entidade, opEnviada.Uuid, revisaoServidor, pendente: true);
                            falhas++;
                        }
                        else
                        {
                            conflitos++;
                            RegistrarConflito(local, opEnviada.Entidade, opEnviada.Uuid, revisaoServidor, excluidoRemoto);
                            local.Estado.Outbox.RemoveAll(o => o.OpId == resultado.OpId);
                        }
                        break;
                    default:
                        falhas++;
                        break;
                }
            }
            local.Salvar();
        }

        // Uploads de foto começam só depois que o atendimento já foi enviado.
        foreach (var foto in local.Estado.Fotos.Where(f => f.Pendente).ToList())
        {
            try
            {
                if (!File.Exists(foto.CaminhoLocal)) continue;
                if (string.IsNullOrWhiteSpace(foto.AtendimentoUuid) == string.IsNullOrWhiteSpace(foto.FichaUuid))
                    continue;
                var resposta = await api.EnviarFotoAsync(empresaId, foto);
                foto.Revisao = resposta.GetProperty("revisao").GetInt64();
                foto.Pendente = false;
                local.Salvar();
                enviadas++;
            }
            catch
            {
                // Falha de storage/rede não pode impedir o restante da sincronização.
                // A foto continua pendente e é reenviada na próxima execução.
                falhas++;
            }
        }

        // PULL
        var cursor = local.Estado.CursoresPorEmpresa.GetValueOrDefault(empresaId,
            local.Estado.EmpresaId == empresaId ? local.Estado.Cursor : 0);
        var temMais = true;
        while (temMais)
        {
            var lote = await api.PuxarAsync(empresaId, cursor);
            cursor = lote.Cursor;
            local.Estado.CursoresPorEmpresa[empresaId] = cursor;
            if (local.Estado.EmpresaId == empresaId) local.Estado.Cursor = cursor;
            temMais = lote.TemMais;
            foreach (var remoto in lote.Atendimentos)
            {
                ReceberAtendimento(local, remoto, ref recebidas, ref conflitos);
            }
            foreach (var remota in lote.Fichas)
            {
                ReceberFicha(local, remota, ref recebidas, ref conflitos);
            }
            foreach (var remota in lote.Fotos)
            {
                recebidas += await ReceberFoto(local, api, remota);
            }
        }
        foreach (var pendenteDownload in local.Estado.Fotos
            .Where(f => !f.Pendente
                && !string.IsNullOrWhiteSpace(f.UrlRemota)
                && (string.IsNullOrWhiteSpace(f.CaminhoLocal) || !File.Exists(f.CaminhoLocal)))
            .ToList())
        {
            recebidas += await BaixarFotoPendente(local, api, pendenteDownload);
        }
        local.Salvar();
        return new Resumo(enviadas, recebidas, conflitos, falhas);
    }

    public static void EnfileirarUpsert(ArmazenamentoLocal local, Atendimento item)
    {
        var existente = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == item.Uuid);
        var opExistente = local.Estado.Outbox.FirstOrDefault(o => o.Uuid == item.Uuid && o.Tipo == "upsert");
        item.Revisao = Math.Max(item.Revisao, Math.Max(existente?.Revisao ?? 0, opExistente?.BaseRevisao ?? 0));
        item.Pendente = true;
        if (existente is null) local.Estado.Atendimentos.Add(item);
        else
        {
            var indice = local.Estado.Atendimentos.IndexOf(existente);
            local.Estado.Atendimentos[indice] = item;
        }
        local.Estado.Conflitos.RemoveAll(c => c.Uuid == item.Uuid);
        local.Estado.Outbox.RemoveAll(o => o.Uuid == item.Uuid && o.Tipo == "upsert");
        local.Estado.Outbox.Add(new ArmazenamentoLocal.OperacaoPendente
        {
            Entidade = "atendimento",
            Uuid = item.Uuid,
            Tipo = "upsert",
            BaseRevisao = item.Revisao,
            Dados = item,
        });
        local.Salvar();
    }

    public static void EnfileirarFicha(ArmazenamentoLocal local, ArmazenamentoLocal.Ficha ficha)
    {
        var existente = local.Estado.Fichas.FirstOrDefault(f => f.Uuid == ficha.Uuid);
        var opExistente = local.Estado.Outbox.FirstOrDefault(o => o.Uuid == ficha.Uuid && o.Tipo == "upsert");
        ficha.Revisao = Math.Max(ficha.Revisao, Math.Max(existente?.Revisao ?? 0, opExistente?.BaseRevisao ?? 0));
        ficha.Pendente = true;
        if (existente is null) local.Estado.Fichas.Add(ficha);
        else local.Estado.Fichas[local.Estado.Fichas.IndexOf(existente)] = ficha;
        local.Estado.Conflitos.RemoveAll(c => c.Uuid == ficha.Uuid);
        local.Estado.Outbox.RemoveAll(o => o.Uuid == ficha.Uuid && o.Tipo == "upsert");
        local.Estado.Outbox.Add(new ArmazenamentoLocal.OperacaoPendente
        {
            Entidade = "ficha",
            Uuid = ficha.Uuid,
            Tipo = "upsert",
            BaseRevisao = ficha.Revisao,
            DadosFicha = ficha,
        });
        local.Salvar();
    }

    public static void EnfileirarExclusao(
        ArmazenamentoLocal local, string uuid, long baseRevisao, string entidade = "atendimento")
    {
        local.Estado.Atendimentos.RemoveAll(a => a.Uuid == uuid);
        local.Estado.Fichas.RemoveAll(f => f.Uuid == uuid);
        local.Estado.Outbox.RemoveAll(o => o.Uuid == uuid && o.Tipo == "upsert");
        // Registro criado só neste PC nunca chegou ao servidor: basta remover localmente.
        if (baseRevisao > 0)
        {
            local.Estado.Outbox.Add(new ArmazenamentoLocal.OperacaoPendente
            {
                Entidade = entidade,
                Uuid = uuid,
                Tipo = "exclusao",
                BaseRevisao = baseRevisao,
            });
        }
        local.Salvar();
    }

    public static void ManterLocal(ArmazenamentoLocal local, ArmazenamentoLocal.Conflito conflito)
    {
        local.Estado.Conflitos.RemoveAll(c => c.Uuid == conflito.Uuid);
        if (conflito.Entidade == "ficha")
        {
            var ficha = local.Estado.Fichas.FirstOrDefault(f => f.Uuid == conflito.Uuid)
                ?? throw new InvalidOperationException("Ficha local não encontrada.");
            if (conflito.ServidorExcluido)
            {
                var anterior = ficha.Uuid;
                local.Estado.Outbox.RemoveAll(o => o.Uuid == anterior);
                ficha.Uuid = Guid.NewGuid().ToString();
                ficha.Revisao = 0;
                foreach (var foto in local.Estado.Fotos.Where(f => f.FichaUuid == anterior))
                {
                    foto.Uuid = Guid.NewGuid().ToString();
                    foto.FichaUuid = ficha.Uuid;
                    foto.Revisao = 0;
                    foto.Pendente = true;
                }
            }
            else ficha.Revisao = Math.Max(1, Math.Max(conflito.RevisaoServidor, ficha.Revisao));
            EnfileirarFicha(local, ficha);
            return;
        }

        var atendimento = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == conflito.Uuid)
            ?? throw new InvalidOperationException("Atendimento local não encontrado.");
        if (conflito.ServidorExcluido)
        {
            var anterior = atendimento.Uuid;
            local.Estado.Outbox.RemoveAll(o => o.Uuid == anterior);
            atendimento.Uuid = Guid.NewGuid().ToString();
            atendimento.Revisao = 0;
            foreach (var foto in local.Estado.Fotos.Where(f => f.AtendimentoUuid == anterior))
            {
                foto.Uuid = Guid.NewGuid().ToString();
                foto.AtendimentoUuid = atendimento.Uuid;
                foto.Revisao = 0;
                foto.Pendente = true;
            }
        }
        else atendimento.Revisao = Math.Max(1, Math.Max(conflito.RevisaoServidor, atendimento.Revisao));
        EnfileirarUpsert(local, atendimento);
    }

    public static async Task AceitarServidor(
        ArmazenamentoLocal local,
        ClienteApi api,
        string empresaId,
        ArmazenamentoLocal.Conflito conflito)
    {
        local.Estado.Outbox.RemoveAll(o => o.Uuid == conflito.Uuid);
        if (conflito.ServidorExcluido)
        {
            RemoverLocal(local, conflito.Entidade, conflito.Uuid);
        }
        else if (conflito.Entidade == "ficha")
        {
            var remota = await api.ObterFichaAsync(empresaId, conflito.Uuid);
            var indice = local.Estado.Fichas.FindIndex(f => f.Uuid == conflito.Uuid);
            if (indice < 0) local.Estado.Fichas.Add(remota);
            else local.Estado.Fichas[indice] = remota;
        }
        else
        {
            var remota = await api.ObterAtendimentoAsync(empresaId, conflito.Uuid);
            var indice = local.Estado.Atendimentos.FindIndex(a => a.Uuid == conflito.Uuid);
            if (indice < 0) local.Estado.Atendimentos.Add(remota);
            else local.Estado.Atendimentos[indice] = remota;
        }
        local.Estado.Conflitos.RemoveAll(c => c.Uuid == conflito.Uuid);
        local.Salvar();
    }

    private static void RemoverLocal(ArmazenamentoLocal local, string entidade, string uuid)
    {
        if (entidade == "ficha") local.Estado.Fichas.RemoveAll(f => f.Uuid == uuid);
        else local.Estado.Atendimentos.RemoveAll(a => a.Uuid == uuid);
        foreach (var foto in local.Estado.Fotos.Where(f =>
                     entidade == "ficha" ? f.FichaUuid == uuid : f.AtendimentoUuid == uuid).ToList())
        {
            if (!string.IsNullOrWhiteSpace(foto.CaminhoLocal)) File.Delete(foto.CaminhoLocal);
            local.Estado.Fotos.Remove(foto);
        }
    }

    private static void RegistrarConflito(
        ArmazenamentoLocal local,
        string entidade,
        string uuid,
        long revisao,
        bool servidorExcluido = false)
    {
        var atendimento = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == uuid);
        var identificacao = atendimento is not null
            ? (!string.IsNullOrWhiteSpace(atendimento.Placa)
                ? atendimento.Placa
                : (!string.IsNullOrWhiteSpace(atendimento.Modelo) ? atendimento.Modelo : atendimento.TipoLocal))
            : local.Estado.Fichas.FirstOrDefault(f => f.Uuid == uuid)?.Equipamento ?? "";
        local.Estado.Conflitos.RemoveAll(c => c.Uuid == uuid);
        local.Estado.Conflitos.Add(new ArmazenamentoLocal.Conflito
        {
            Uuid = uuid,
            Entidade = entidade,
            RevisaoServidor = revisao,
            Placa = identificacao,
            ServidorExcluido = servidorExcluido,
        });
    }

    private static async Task CurarConflitosLegadosAsync(
        ArmazenamentoLocal local,
        ClienteApi api,
        string empresaId)
    {
        var presos = local.Estado.Conflitos
            .Where(c => c.RevisaoServidor <= 0 && !c.ServidorExcluido)
            .ToList();
        if (presos.Count == 0) return;

        foreach (var conflito in presos)
        {
            try
            {
                if (conflito.Entidade == "ficha")
                {
                    var remota = await api.ObterFichaAsync(empresaId, conflito.Uuid);
                    conflito.RevisaoServidor = Math.Max(1, remota.Revisao);
                    var fichaLocal = local.Estado.Fichas.FirstOrDefault(f => f.Uuid == conflito.Uuid);
                    if (fichaLocal is not null && fichaLocal.Revisao == 0 && remota.Revisao <= 1)
                    {
                        ManterLocal(local, conflito);
                    }
                }
                else
                {
                    var remoto = await api.ObterAtendimentoAsync(empresaId, conflito.Uuid);
                    conflito.RevisaoServidor = Math.Max(1, remoto.Revisao);
                    var atendLocal = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == conflito.Uuid);
                    if (atendLocal is not null && atendLocal.Revisao == 0 && remoto.Revisao <= 1)
                    {
                        ManterLocal(local, conflito);
                    }
                }
            }
            catch (HttpRequestException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                local.Estado.Conflitos.RemoveAll(c => c.Uuid == conflito.Uuid);
            }
            catch
            {
                // Sem conexão: mantém até a próxima sincronização.
            }
        }
        local.Salvar();
    }

    private static void AtualizarRevisaoNoItem(
        ArmazenamentoLocal local,
        string entidade,
        string uuid,
        long novaRevisao,
        bool pendente)
    {
        if (entidade == "ficha")
        {
            var ficha = local.Estado.Fichas.FirstOrDefault(f => f.Uuid == uuid);
            if (ficha is not null)
            {
                if (novaRevisao > 0) ficha.Revisao = Math.Max(ficha.Revisao, novaRevisao);
                ficha.Pendente = pendente;
            }
            return;
        }
        if (entidade == "foto")
        {
            var foto = local.Estado.Fotos.FirstOrDefault(f => f.Uuid == uuid);
            if (foto is not null)
            {
                if (novaRevisao > 0) foto.Revisao = Math.Max(foto.Revisao, novaRevisao);
                foto.Pendente = pendente;
            }
            return;
        }
        var atendimento = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == uuid);
        if (atendimento is not null)
        {
            if (novaRevisao > 0) atendimento.Revisao = Math.Max(atendimento.Revisao, novaRevisao);
            atendimento.Pendente = pendente;
        }
    }

    private static long ExtrairRevisao(string? registro)
    {
        if (string.IsNullOrWhiteSpace(registro)) return 0;
        try
        {
            using var json = JsonDocument.Parse(registro);
            return json.RootElement.TryGetProperty("revisao", out var rev) &&
                   rev.ValueKind == JsonValueKind.Number
                ? rev.GetInt64()
                : 0;
        }
        catch
        {
            return 0;
        }
    }

    private static bool RegistroExcluido(string? registro)
    {
        if (string.IsNullOrWhiteSpace(registro)) return false;
        try
        {
            using var json = JsonDocument.Parse(registro);
            return json.RootElement.TryGetProperty("excluidoEm", out var excluido) &&
                excluido.ValueKind != JsonValueKind.Null;
        }
        catch
        {
            return false;
        }
    }

    private static void ReceberAtendimento(
        ArmazenamentoLocal local, Atendimento remoto, ref int recebidas, ref int conflitos)
    {
        var opPendente = local.Estado.Outbox.FirstOrDefault(o => o.Uuid == remoto.Uuid);
        var pendente = opPendente is not null;
        var existente = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == remoto.Uuid);

        if (remoto.ExcluidoEm is not null)
        {
            if (existente is null) return;
            if (pendente)
            {
                RegistrarConflito(local, "atendimento", remoto.Uuid, remoto.Revisao, servidorExcluido: true);
                conflitos++;
                return;
            }
            local.Estado.Atendimentos.Remove(existente);
            recebidas++;
            return;
        }

        if (pendente && existente is not null)
        {
            var baseConhecida = Math.Max(existente.Revisao, opPendente!.BaseRevisao);
            if (remoto.Revisao <= baseConhecida)
            {
                existente.Revisao = Math.Max(existente.Revisao, remoto.Revisao);
                opPendente.BaseRevisao = Math.Max(opPendente.BaseRevisao, remoto.Revisao);
                if (opPendente.Dados is not null)
                    opPendente.Dados.Revisao = Math.Max(opPendente.Dados.Revisao, remoto.Revisao);
                return;
            }
            RegistrarConflito(local, "atendimento", remoto.Uuid, remoto.Revisao);
            conflitos++;
            return;
        }

        if (existente is null)
        {
            local.Estado.Atendimentos.Add(remoto);
        }
        else if (remoto.Revisao >= existente.Revisao)
        {
            var indice = local.Estado.Atendimentos.IndexOf(existente);
            remoto.Pendente = existente.Pendente;
            local.Estado.Atendimentos[indice] = remoto;
        }
        recebidas++;
    }

    private static void ReceberFicha(
        ArmazenamentoLocal local, ArmazenamentoLocal.Ficha remota, ref int recebidas, ref int conflitos)
    {
        var opPendente = local.Estado.Outbox.FirstOrDefault(o => o.Uuid == remota.Uuid);
        var pendente = opPendente is not null;
        var existente = local.Estado.Fichas.FirstOrDefault(a => a.Uuid == remota.Uuid);

        if (remota.ExcluidoEm is not null)
        {
            if (existente is null) return;
            if (pendente)
            {
                RegistrarConflito(local, "ficha", remota.Uuid, remota.Revisao, servidorExcluido: true);
                conflitos++;
                return;
            }
            local.Estado.Fichas.Remove(existente);
            recebidas++;
            return;
        }

        if (pendente && existente is not null)
        {
            var baseConhecida = Math.Max(existente.Revisao, opPendente!.BaseRevisao);
            if (remota.Revisao <= baseConhecida)
            {
                existente.Revisao = Math.Max(existente.Revisao, remota.Revisao);
                opPendente.BaseRevisao = Math.Max(opPendente.BaseRevisao, remota.Revisao);
                if (opPendente.DadosFicha is not null)
                    opPendente.DadosFicha.Revisao = Math.Max(opPendente.DadosFicha.Revisao, remota.Revisao);
                return;
            }
            RegistrarConflito(local, "ficha", remota.Uuid, remota.Revisao);
            conflitos++;
            return;
        }

        if (existente is null) local.Estado.Fichas.Add(remota);
        else if (remota.Revisao >= existente.Revisao)
        {
            var indice = local.Estado.Fichas.IndexOf(existente);
            remota.Pendente = existente.Pendente;
            local.Estado.Fichas[indice] = remota;
        }
        recebidas++;
    }

    private static async Task<int> ReceberFoto(
        ArmazenamentoLocal local, ClienteApi api, ClienteApi.FotoRemota remota)
    {
        if (remota.Excluido)
        {
            foreach (var foto in local.Estado.Fotos.Where(f => f.Uuid == remota.Uuid).ToList())
            {
                if (!string.IsNullOrWhiteSpace(foto.CaminhoLocal) && File.Exists(foto.CaminhoLocal))
                {
                    File.Delete(foto.CaminhoLocal);
                }
            }
            local.Estado.Fotos.RemoveAll(f => f.Uuid == remota.Uuid);
            return 1;
        }
        var existente = local.Estado.Fotos.FirstOrDefault(f => f.Uuid == remota.Uuid);
        if (existente is not null
            && !existente.Pendente
            && !string.IsNullOrWhiteSpace(existente.CaminhoLocal)
            && File.Exists(existente.CaminhoLocal)
            && remota.Revisao <= existente.Revisao)
        {
            return 0;
        }
        var caminho = await TentarBaixarFotoAsync(api, remota.Uuid, remota.Url, remota.MimeType);
        if (caminho is null)
        {
            // Preserva metadados remotos para que o avanço do cursor não perca a foto offline/em falha.
            if (existente is null)
            {
                local.Estado.Fotos.Add(new ArmazenamentoLocal.FotoLocal
                {
                    Uuid = remota.Uuid,
                    AtendimentoUuid = remota.AtendimentoUuid,
                    FichaUuid = remota.FichaUuid,
                    Categoria = remota.Categoria,
                    Legenda = remota.Legenda,
                    Anotacoes = remota.Anotacoes,
                    Ordem = remota.Ordem,
                    Revisao = 0,
                    CaminhoLocal = string.Empty,
                    UrlRemota = remota.Url,
                    MimeType = remota.MimeType,
                    Pendente = false,
                });
            }
            else
            {
                existente.UrlRemota = remota.Url;
                existente.MimeType = remota.MimeType;
            }
            return 0;
        }
        var meta = new ArmazenamentoLocal.FotoLocal
        {
            Uuid = remota.Uuid,
            AtendimentoUuid = remota.AtendimentoUuid,
            FichaUuid = remota.FichaUuid,
            Categoria = remota.Categoria,
            Legenda = remota.Legenda,
            Anotacoes = remota.Anotacoes,
            Ordem = remota.Ordem,
            Revisao = remota.Revisao,
            CaminhoLocal = caminho,
            UrlRemota = remota.Url,
            MimeType = remota.MimeType,
            Pendente = false,
        };
        if (existente is null) local.Estado.Fotos.Add(meta);
        else
        {
            var indice = local.Estado.Fotos.IndexOf(existente);
            local.Estado.Fotos[indice] = meta;
        }
        return 1;
    }

    private static async Task<int> BaixarFotoPendente(
        ArmazenamentoLocal local, ClienteApi api, ArmazenamentoLocal.FotoLocal foto)
    {
        var caminho = await TentarBaixarFotoAsync(api, foto.Uuid, foto.UrlRemota, foto.MimeType);
        if (string.IsNullOrWhiteSpace(caminho)) return 0;
        foto.CaminhoLocal = caminho;
        if (foto.Revisao <= 0) foto.Revisao = 1;
        return 1;
    }

    private static async Task<string?> TentarBaixarFotoAsync(
        ClienteApi api, string uuid, string? url, string? mimeType)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        var extensao = mimeType switch
        {
            "image/png" => ".png",
            "image/webp" => ".webp",
            _ => ".jpg",
        };
        var caminho = Path.Combine(PastaFotos(), $"{uuid}{extensao}");
        try
        {
            var bytes = await api.BaixarFotoAsync(url);
            await File.WriteAllBytesAsync(caminho, bytes);
            return caminho;
        }
        catch
        {
            return null;
        }
    }
}
