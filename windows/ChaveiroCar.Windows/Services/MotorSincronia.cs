using System.IO;
using System.Text.Json;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Push (outbox) + pull (cursor) sobre <see cref="ClienteApi"/>.
/// Divergência vira conflito explícito, nunca sobrescrita silenciosa.
/// Fotos só sobem após o atendimento; downloads são guardados em disco.
/// </summary>
public static class MotorSincronia
{
    public sealed record Resumo(int Enviadas, int Recebidas, int Conflitos);

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

        // PUSH atendimentos + fichas (lote idempotente) e exclusões.
        if (local.Estado.Outbox.Count > 0)
        {
            var operacoes = local.Estado.Outbox.Select(op =>
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
                var op = local.Estado.Outbox.FirstOrDefault(o => o.OpId == resultado.OpId);
                if (op is null) continue;
                switch (resultado.Status)
                {
                    case "aplicado":
                        enviadas++;
                        if (op.Dados is not null)
                        {
                            op.Dados.Revisao = resultado.Revisao;
                            op.Dados.Pendente = false;
                        }
                        if (op.DadosFicha is not null)
                        {
                            op.DadosFicha.Revisao = resultado.Revisao;
                            op.DadosFicha.Pendente = false;
                        }
                        if (op.DadosFoto is not null)
                        {
                            op.DadosFoto.Revisao = resultado.Revisao;
                            op.DadosFoto.Pendente = false;
                        }
                        local.Estado.Conflitos.RemoveAll(c => c.Uuid == op.Uuid);
                        local.Estado.Outbox.Remove(op);
                        break;
                    case "conflito":
                        conflitos++;
                        RegistrarConflito(local, op.Entidade, op.Uuid, resultado.Revisao,
                            RegistroExcluido(resultado.Registro));
                        local.Estado.Outbox.Remove(op);
                        break;
                }
            }
            local.Salvar();
        }

        // Uploads de foto começam só depois que o atendimento já foi enviado.
        foreach (var foto in local.Estado.Fotos.Where(f => f.Pendente).ToList())
        {
            if (!File.Exists(foto.CaminhoLocal))
                throw new FileNotFoundException("A foto pendente não está mais no aparelho.", foto.CaminhoLocal);
            if (string.IsNullOrWhiteSpace(foto.AtendimentoUuid) == string.IsNullOrWhiteSpace(foto.FichaUuid))
                throw new InvalidDataException("A foto precisa pertencer a um atendimento ou a uma ficha.");
            var resposta = await api.EnviarFotoAsync(empresaId, foto);
            foto.Revisao = resposta.GetProperty("revisao").GetInt64();
            foto.Pendente = false;
            local.Salvar();
            enviadas++;
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
        local.Salvar();
        return new Resumo(enviadas, recebidas, conflitos);
    }

    public static void EnfileirarUpsert(ArmazenamentoLocal local, Atendimento item)
    {
        item.Pendente = true;
        var existente = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == item.Uuid);
        if (existente is null) local.Estado.Atendimentos.Add(item);
        else
        {
            var indice = local.Estado.Atendimentos.IndexOf(existente);
            local.Estado.Atendimentos[indice] = item;
        }
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
        ficha.Pendente = true;
        var existente = local.Estado.Fichas.FirstOrDefault(f => f.Uuid == ficha.Uuid);
        if (existente is null) local.Estado.Fichas.Add(ficha);
        else local.Estado.Fichas[local.Estado.Fichas.IndexOf(existente)] = ficha;
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

    public static void EnfileirarExclusao(ArmazenamentoLocal local, string uuid, string entidade = "atendimento")
    {
        local.Estado.Atendimentos.RemoveAll(a => a.Uuid == uuid);
        local.Estado.Fichas.RemoveAll(f => f.Uuid == uuid);
        local.Estado.Outbox.RemoveAll(o => o.Uuid == uuid && o.Tipo == "upsert");
        local.Estado.Outbox.Add(new ArmazenamentoLocal.OperacaoPendente
        {
            Entidade = entidade,
            Uuid = uuid,
            Tipo = "exclusao",
            BaseRevisao = 0,
        });
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
            else ficha.Revisao = conflito.RevisaoServidor;
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
        else atendimento.Revisao = conflito.RevisaoServidor;
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
        var placa = local.Estado.Atendimentos.FirstOrDefault(a => a.Uuid == uuid)?.Placa
            ?? local.Estado.Fichas.FirstOrDefault(f => f.Uuid == uuid)?.Equipamento
            ?? "";
        local.Estado.Conflitos.RemoveAll(c => c.Uuid == uuid);
        local.Estado.Conflitos.Add(new ArmazenamentoLocal.Conflito
        {
            Uuid = uuid,
            Entidade = entidade,
            RevisaoServidor = revisao,
            Placa = placa,
            ServidorExcluido = servidorExcluido,
        });
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
        var pendente = local.Estado.Outbox.Any(o => o.Uuid == remoto.Uuid);
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

        if (pendente && existente is not null && remoto.Revisao != existente.Revisao)
        {
            RegistrarConflito(local, "atendimento", remoto.Uuid, remoto.Revisao);
            conflitos++;
            return;
        }

        if (existente is null)
        {
            local.Estado.Atendimentos.Add(remoto);
        }
        else
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
        var pendente = local.Estado.Outbox.Any(o => o.Uuid == remota.Uuid);
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

        if (pendente && existente is not null && remota.Revisao != existente.Revisao)
        {
            RegistrarConflito(local, "ficha", remota.Uuid, remota.Revisao);
            conflitos++;
            return;
        }

        if (existente is null) local.Estado.Fichas.Add(remota);
        else
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
        if (remota.Excluido) {
            foreach (var foto in local.Estado.Fotos.Where(f => f.Uuid == remota.Uuid).ToList())
            {
                if (!string.IsNullOrWhiteSpace(foto.CaminhoLocal)) File.Delete(foto.CaminhoLocal);
            }
            local.Estado.Fotos.RemoveAll(f => f.Uuid == remota.Uuid);
            return 1;
        }
        var existente = local.Estado.Fotos.FirstOrDefault(f => f.Uuid == remota.Uuid);
        if (existente is not null && !existente.Pendente && File.Exists(existente.CaminhoLocal)) return 0;
        var extensao = remota.MimeType switch
        {
            "image/png" => ".png",
            "image/webp" => ".webp",
            _ => ".jpg",
        };
        var caminho = Path.Combine(PastaFotos(), $"{remota.Uuid}{extensao}");
        try
        {
            var bytes = await api.BaixarFotoAsync(remota.Url);
            await File.WriteAllBytesAsync(caminho, bytes);
        }
        catch
        {
            // Sem rede, ignora o download; metadados entram na próxima sync.
            if (existente is null) return 0;
        }
        var meta = new ArmazenamentoLocal.FotoLocal
        {
            Uuid = remota.Uuid,
            AtendimentoUuid = remota.AtendimentoUuid,
            FichaUuid = remota.FichaUuid,
            Categoria = remota.Categoria,
            Legenda = remota.Legenda,
            Ordem = remota.Ordem,
            Revisao = remota.Revisao,
            CaminhoLocal = caminho,
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
}
