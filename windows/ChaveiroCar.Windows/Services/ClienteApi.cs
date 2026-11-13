using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Cliente HTTP da API ChaveiroCar (contracts/openapi.json).
/// Cache/outbox ficam em <see cref="ArmazenamentoLocal"/>; aqui só rede.
/// </summary>
public sealed class ClienteApi : IDisposable
{
    private readonly HttpClient _http = new();
    private string? _acesso;
    private string? _refresh;

    public string? Acesso => _acesso;
    public string? Refresh => _refresh;

    public ClienteApi(string? baseUrl = null)
    {
        var endereco = string.IsNullOrWhiteSpace(baseUrl)
            ? "https://chaveirocar-api.onrender.com"
            : baseUrl.Trim().TrimEnd('/');
        _http.BaseAddress = new Uri(endereco + "/");
    }

    public void DefinirTokens(string? acesso, string? refresh)
    {
        _acesso = acesso;
        _refresh = refresh;
    }

    public async Task<ResultadoLogin> EntrarAsync(string email, string senha)
    {
        using var resposta = await PostAsync("/api/v1/auth/login", new { email, senha });
        return await CriarSessaoAsync(resposta);
    }

    public async Task<ResultadoLogin> CadastrarAsync(string nome, string email, string senha, string empresa)
    {
        using var resposta = await PostAsync("/api/v1/auth/cadastro", new { nome, email, senha, empresa });
        return await CriarSessaoAsync(resposta);
    }

    public async Task<ResultadoLogin> EntrarComGoogleAsync(string idToken, string? empresa = null)
    {
        object corpo = string.IsNullOrWhiteSpace(empresa)
            ? new { idToken }
            : new { idToken, empresa = empresa.Trim() };
        using var resposta = await PostAsync("/api/v1/auth/google-login", corpo);
        return await CriarSessaoAsync(resposta);
    }

    private async Task<ResultadoLogin> CriarSessaoAsync(HttpResponseMessage resposta)
    {
        if (!resposta.IsSuccessStatusCode) return new ResultadoLogin(false, null, []);
        var doc = await LerJson(resposta);
        var tokens = doc.GetProperty("tokens");
        DefinirTokens(
            tokens.GetProperty("acesso").GetString(),
            tokens.GetProperty("refresh").GetString());
        var empresas = new List<Empresa>();
        foreach (var item in doc.GetProperty("empresas").EnumerateArray())
        {
            empresas.Add(new Empresa(
                item.GetProperty("id").GetString() ?? "",
                item.GetProperty("nome").GetString() ?? "",
                item.GetProperty("papel").GetString() ?? ""));
        }
        var usuario = doc.GetProperty("usuario");
        return new ResultadoLogin(
            true,
            usuario.GetProperty("email").GetString(),
            empresas,
            usuario.TryGetProperty("emailVerificado", out var verificado) && verificado.GetBoolean(),
            !doc.TryGetProperty("emailEnviado", out var enviado) || enviado.GetBoolean());
    }

    public async Task<IReadOnlyList<Membro>> MembrosAsync(string empresaId)
    {
        using var resposta = await GetAsync($"/api/v1/empresas/{empresaId}/membros");
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        return doc.GetProperty("itens").EnumerateArray().Select(item => new Membro(
            item.GetProperty("usuarioId").GetString() ?? "",
            item.GetProperty("nome").GetString() ?? "",
            item.GetProperty("email").GetString() ?? "",
            item.GetProperty("papel").GetString() ?? "",
            item.GetProperty("desde").GetInt64())).ToList();
    }

    public async Task<IReadOnlyList<Convite>> ConvitesAsync(string empresaId)
    {
        using var resposta = await GetAsync($"/api/v1/empresas/{empresaId}/convites");
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        return doc.GetProperty("itens").EnumerateArray().Select(item => new Convite(
            item.GetProperty("id").GetString() ?? "",
            item.GetProperty("email").GetString() ?? "",
            item.GetProperty("papel").GetString() ?? "",
            item.GetProperty("expirado").GetBoolean(),
            item.GetProperty("aceitoEm").ValueKind != JsonValueKind.Null,
            item.GetProperty("canceladoEm").ValueKind != JsonValueKind.Null)).ToList();
    }

    public async Task<(bool EmailEnviado, string? Token)> ConvidarAsync(
        string empresaId, string email, string papel)
    {
        using var resposta = await PostAsync($"/api/v1/empresas/{empresaId}/convites", new { email, papel });
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        var token = doc.TryGetProperty("token", out var t) && t.ValueKind == JsonValueKind.String
            ? t.GetString()
            : null;
        return (doc.GetProperty("emailEnviado").GetBoolean(), token);
    }

    public async Task<ConvitePreview> PrevisualizarConviteAsync(string token)
    {
        using var resposta = await GetAsync($"/api/v1/convites/{Uri.EscapeDataString(token)}");
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        return new ConvitePreview(
            doc.GetProperty("empresaNome").GetString() ?? "",
            doc.GetProperty("email").GetString() ?? "",
            doc.GetProperty("papel").GetString() ?? "",
            doc.GetProperty("expiraEm").GetInt64());
    }

    public async Task<string> AceitarConviteAsync(string token, string? nome = null, string? senha = null)
    {
        var corpo = new Dictionary<string, string>();
        if (!string.IsNullOrWhiteSpace(nome)) corpo["nome"] = nome;
        if (!string.IsNullOrWhiteSpace(senha)) corpo["senha"] = senha;
        using var resposta = await PostAsync(
            $"/api/v1/convites/{Uri.EscapeDataString(token)}/aceitar", corpo);
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        var tokens = doc.GetProperty("tokens");
        DefinirTokens(
            tokens.GetProperty("acesso").GetString(),
            tokens.GetProperty("refresh").GetString());
        return doc.GetProperty("empresaId").GetString() ?? "";
    }

    public async Task CancelarConviteAsync(string empresaId, string conviteId)
    {
        using var resposta = await DeleteAsync($"/api/v1/empresas/{empresaId}/convites/{conviteId}");
        resposta.EnsureSuccessStatusCode();
    }

    public async Task AlterarPapelAsync(string empresaId, string usuarioId, string papel)
    {
        using var resposta = await PatchAsync(
            $"/api/v1/empresas/{empresaId}/membros/{usuarioId}", new { papel });
        resposta.EnsureSuccessStatusCode();
    }

    public async Task RemoverMembroAsync(string empresaId, string usuarioId)
    {
        using var resposta = await DeleteAsync($"/api/v1/empresas/{empresaId}/membros/{usuarioId}");
        resposta.EnsureSuccessStatusCode();
    }

    public async Task SolicitarRedefinicaoAsync(string email)
    {
        using var resposta = await PostAsync("/api/v1/auth/esqueci-senha", new { email });
        resposta.EnsureSuccessStatusCode();
    }

    public async Task RedefinirSenhaAsync(string token, string novaSenha)
    {
        using var resposta = await PostAsync("/api/v1/auth/redefinir-senha", new { token, senha = novaSenha });
        resposta.EnsureSuccessStatusCode();
    }

    public async Task<bool> RenovarAsync()
    {
        if (string.IsNullOrWhiteSpace(_refresh)) return false;
        using var resposta = await PostAsync("/api/v1/auth/renovar", new { refresh = _refresh });
        if (!resposta.IsSuccessStatusCode) return false;
        var doc = await LerJson(resposta);
        var tokens = doc.GetProperty("tokens");
        DefinirTokens(
            tokens.GetProperty("acesso").GetString(),
            tokens.GetProperty("refresh").GetString());
        return true;
    }

    public async Task SairAsync()
    {
        await PostAsync("/api/v1/auth/sair", new { refresh = _refresh });
        DefinirTokens(null, null);
    }

    public async Task<IReadOnlyList<Empresa>> EmpresasAsync()
    {
        using var resposta = await GetAsync("/api/v1/empresas");
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        var lista = new List<Empresa>();
        foreach (var item in doc.GetProperty("itens").EnumerateArray())
        {
            lista.Add(new Empresa(
                item.GetProperty("id").GetString() ?? "",
                item.GetProperty("nome").GetString() ?? "",
                item.GetProperty("papel").GetString() ?? ""));
        }
        return lista;
    }

    public async Task<RespostaSync> EnviarLoteAsync(string empresaId, object operacoes)
    {
        using var resposta = await PostAsync(
            $"/api/v1/empresas/{empresaId}/sync", new { operacoes });
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        var lista = new List<ResultadOperacao>();
        foreach (var item in doc.GetProperty("resultados").EnumerateArray())
        {
            lista.Add(new ResultadOperacao
            {
                OpId = item.GetProperty("opId").GetString() ?? "",
                Status = item.GetProperty("status").GetString() ?? "",
                Revisao = item.TryGetProperty("revisao", out var r) ? r.GetInt64() : 0,
                Registro = item.TryGetProperty("registro", out var reg)
                    ? JsonSerializer.Serialize(reg) : null,
            });
        }
        return new RespostaSync(lista, doc.GetProperty("cursor").GetInt64());
    }

    public async Task<LotePullPublico> PuxarAsync(string empresaId, long cursor)
    {
        var resposta = await GetAsync($"/api/v1/empresas/{empresaId}/sync?cursor={cursor}");
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        var lista = new List<Atendimento>();
        foreach (var item in doc.GetProperty("atendimentos").EnumerateArray())
        {
            lista.Add(Converter.AtendimentoDeJson(item));
        }
        var fichas = new List<ArmazenamentoLocal.Ficha>();
        foreach (var item in doc.GetProperty("fichas").EnumerateArray())
        {
            fichas.Add(Converter.FichaDeJson(item));
        }
        var fotos = new List<FotoRemota>();
        foreach (var item in doc.GetProperty("fotos").EnumerateArray())
        {
            fotos.Add(new FotoRemota
            {
                Uuid = item.GetProperty("id").GetString() ?? "",
                AtendimentoUuid = item.GetProperty("atendimentoId").GetString() ?? "",
                FichaUuid = item.GetProperty("fichaId").ValueKind == JsonValueKind.Null
                    ? "" : item.GetProperty("fichaId").GetString() ?? "",
                Categoria = item.GetProperty("categoria").GetString() ?? "CARRO",
                Legenda = item.GetProperty("legenda").GetString() ?? "",
                Anotacoes = item.TryGetProperty("anotacoes", out var anot) && anot.ValueKind == JsonValueKind.String
                    ? anot.GetString() ?? "[]" : "[]",
                Ordem = item.GetProperty("ordem").GetInt32(),
                Revisao = item.GetProperty("revisao").GetInt64(),
                Url = item.GetProperty("url").GetString() ?? "",
                MimeType = item.GetProperty("mimeType").GetString() ?? "image/jpeg",
                Excluido = item.TryGetProperty("excluidoEm", out var x) && x.ValueKind != JsonValueKind.Null,
            });
        }
        return new LotePullPublico
        {
            Cursor = doc.GetProperty("cursor").GetInt64(),
            TemMais = doc.GetProperty("temMais").GetBoolean(),
            Atendimentos = lista,
            Fichas = fichas,
            Fotos = fotos,
        };
    }

    public async Task<Atendimento> ObterAtendimentoAsync(string empresaId, string id)
    {
        var resposta = await GetAsync($"/api/v1/empresas/{empresaId}/atendimentos/{id}");
        resposta.EnsureSuccessStatusCode();
        return Converter.AtendimentoDeJson(await LerJson(resposta));
    }

    public async Task<ArmazenamentoLocal.Ficha> ObterFichaAsync(string empresaId, string id)
    {
        var resposta = await GetAsync($"/api/v1/empresas/{empresaId}/fichas/{id}");
        resposta.EnsureSuccessStatusCode();
        return Converter.FichaDeJson(await LerJson(resposta));
    }

    public async Task<byte[]> BaixarFotoAsync(string urlRelativa)
    {
        for (int tentativa = 0; tentativa < 2; tentativa++)
        {
            using var requisicao = new HttpRequestMessage(HttpMethod.Get, urlRelativa);
            if (_acesso is not null) requisicao.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
            var resposta = await _http.SendAsync(requisicao);
            if (resposta.StatusCode != System.Net.HttpStatusCode.Unauthorized || tentativa > 0)
            {
                using (resposta)
                {
                    resposta.EnsureSuccessStatusCode();
                    return await resposta.Content.ReadAsByteArrayAsync();
                }
            }
            if (!await RenovarAsync())
            {
                resposta.Dispose();
                resposta.EnsureSuccessStatusCode();
            }
            resposta.Dispose();
        }
        throw new InvalidOperationException("Não foi possível baixar a foto.");
    }

    public async Task<JsonElement> EnviarFotoAsync(string empresaId, ArmazenamentoLocal.FotoLocal foto)
    {
        var bytes = await File.ReadAllBytesAsync(foto.CaminhoLocal);
        var tipo = Path.GetExtension(foto.CaminhoLocal).ToLowerInvariant() switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".webp" => "image/webp",
            _ => throw new InvalidOperationException("Formato de foto não suportado."),
        };

        for (int tentativa = 0; tentativa < 2; tentativa++)
        {
        using var formulario = new MultipartFormDataContent();
            formulario.Add(new StringContent(foto.Uuid), "id");
            formulario.Add(new StringContent(foto.Categoria), "categoria");
            formulario.Add(new StringContent(foto.Legenda), "legenda");
            formulario.Add(new StringContent(string.IsNullOrWhiteSpace(foto.Anotacoes) ? "[]" : foto.Anotacoes), "anotacoes");
            formulario.Add(new StringContent(foto.Ordem.ToString()), "ordem");
            var arquivo = new ByteArrayContent(bytes);
            arquivo.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(tipo);
            formulario.Add(arquivo, "arquivo", Path.GetFileName(foto.CaminhoLocal));

            var caminho = string.IsNullOrWhiteSpace(foto.FichaUuid)
                ? $"/api/v1/empresas/{empresaId}/atendimentos/{foto.AtendimentoUuid}/fotos"
                : $"/api/v1/empresas/{empresaId}/fichas/{foto.FichaUuid}/fotos";
            using var requisicao = new HttpRequestMessage(HttpMethod.Post, caminho)
            { Content = formulario };
            if (_acesso is not null) requisicao.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
            using var resposta = await _http.SendAsync(requisicao);
            if (resposta.StatusCode == System.Net.HttpStatusCode.Unauthorized && tentativa == 0 && await RenovarAsync())
                continue;
            resposta.EnsureSuccessStatusCode();
            return await LerJson(resposta);
        }
        throw new InvalidOperationException("Não foi possível enviar a foto.");
    }

    private async Task<HttpResponseMessage> GetAsync(string caminho)
    {
        for (int tentativa = 0; tentativa < 2; tentativa++)
        {
            using var requisicao = new HttpRequestMessage(HttpMethod.Get, caminho);
            if (_acesso is not null) requisicao.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
            var resposta = await _http.SendAsync(requisicao);
            if (resposta.StatusCode != System.Net.HttpStatusCode.Unauthorized || tentativa > 0) return resposta;
            if (!await RenovarAsync()) return resposta;
            resposta.Dispose();
        }
        throw new InvalidOperationException();
    }

    private async Task<HttpResponseMessage> PostAsync(string caminho, object corpo)
    {
        for (int tentativa = 0; tentativa < 2; tentativa++)
        {
            using var conteudo = new StringContent(
                JsonSerializer.Serialize(corpo), Encoding.UTF8, "application/json");
            using var requisicao = new HttpRequestMessage(HttpMethod.Post, caminho) { Content = conteudo };
            if (_acesso is not null) requisicao.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
            var resposta = await _http.SendAsync(requisicao);
            if (resposta.StatusCode != System.Net.HttpStatusCode.Unauthorized || tentativa > 0) return resposta;
            if (!await RenovarAsync()) return resposta;
            resposta.Dispose();
        }
        throw new InvalidOperationException();
    }

    private async Task<HttpResponseMessage> PatchAsync(string caminho, object corpo)
    {
        for (int tentativa = 0; tentativa < 2; tentativa++)
        {
            using var conteudo = new StringContent(
                JsonSerializer.Serialize(corpo), Encoding.UTF8, "application/json");
            using var requisicao = new HttpRequestMessage(HttpMethod.Patch, caminho) { Content = conteudo };
            if (_acesso is not null) requisicao.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
            var resposta = await _http.SendAsync(requisicao);
            if (resposta.StatusCode != System.Net.HttpStatusCode.Unauthorized || tentativa > 0) return resposta;
            if (!await RenovarAsync()) return resposta;
            resposta.Dispose();
        }
        throw new InvalidOperationException();
    }

    private async Task<HttpResponseMessage> DeleteAsync(string caminho)
    {
        for (int tentativa = 0; tentativa < 2; tentativa++)
        {
            using var requisicao = new HttpRequestMessage(HttpMethod.Delete, caminho);
            if (_acesso is not null) requisicao.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
            var resposta = await _http.SendAsync(requisicao);
            if (resposta.StatusCode != System.Net.HttpStatusCode.Unauthorized || tentativa > 0) return resposta;
            if (!await RenovarAsync()) return resposta;
            resposta.Dispose();
        }
        throw new InvalidOperationException();
    }

    private static async Task<JsonElement> LerJson(HttpResponseMessage resposta)
    {
        await using var fluxo = await resposta.Content.ReadAsStreamAsync();
        using var doc = await JsonDocument.ParseAsync(fluxo);
        return doc.RootElement.Clone();
    }

    public void Dispose() => _http.Dispose();

    public sealed record Empresa(string Id, string Nome, string Papel = "");
    public sealed record ConvitePreview(string EmpresaNome, string Email, string Papel, long ExpiraEm);
    public sealed record Membro(string UsuarioId, string Nome, string Email, string Papel, long Desde)
    {
        public string PapelFormatado => Papel switch
        {
            "dono" => "Dono",
            "admin" => "Administrador",
            _ => "Colaborador",
        };
        public string DesdeFormatado => DateTimeOffset.FromUnixTimeMilliseconds(Desde).LocalDateTime.ToString("dd/MM/yyyy");
    }
    public sealed record Convite(string Id, string Email, string Papel, bool Expirado, bool Aceito, bool Cancelado)
    {
        public string PapelFormatado => Papel switch
        {
            "dono" => "Dono",
            "admin" => "Administrador",
            _ => "Colaborador",
        };
        public string SituacaoFormatada => Aceito ? "Aceito" : Cancelado ? "Cancelado" : Expirado ? "Expirado" : "Pendente";
    }
    public sealed record ResultadoLogin(
        bool Ok,
        string? Email,
        List<Empresa> Empresas,
        bool EmailVerificado = false,
        bool EmailEnviado = true);

    public sealed class ResultadOperacao
    {
        public string OpId { get; set; } = "";
        public string Status { get; set; } = "";
        public long Revisao { get; set; }
        public string? Registro { get; set; }
    }

    public sealed record RespostaSync(List<ResultadOperacao> Resultados, long Cursor);

    public sealed class LotePullPublico
    {
        public long Cursor { get; set; }
        public bool TemMais { get; set; }
        public List<Atendimento> Atendimentos { get; set; } = [];
        public List<ArmazenamentoLocal.Ficha> Fichas { get; set; } = [];
        public List<FotoRemota> Fotos { get; set; } = [];
    }

    public sealed class FotoRemota
    {
        public string Uuid { get; set; } = "";
        public string AtendimentoUuid { get; set; } = "";
        public string FichaUuid { get; set; } = "";
        public string Categoria { get; set; } = "CARRO";
        public string Legenda { get; set; } = "";
        public string Anotacoes { get; set; } = "[]";
        public int Ordem { get; set; }
        public long Revisao { get; set; }
        public string Url { get; set; } = "";
        public string MimeType { get; set; } = "image/jpeg";
        public bool Excluido { get; set; }
    }
}

public sealed class Atendimento
{
    public string Uuid { get; set; } = "";
    public string Segmento { get; set; } = "AUTOMOTIVO";
    public string Placa { get; set; } = "";
    public string Marca { get; set; } = "";
    public string Modelo { get; set; } = "";
    public string Ano { get; set; } = "";
    public string Chassi { get; set; } = "";
    public string TipoLocal { get; set; } = "";
    public string AmbientePorta { get; set; } = "";
    public string MarcaFechadura { get; set; } = "";
    public string ModeloFechadura { get; set; } = "";
    public string TipoCilindro { get; set; } = "";
    public string EnderecoLocal { get; set; } = "";
    public long DataServico { get; set; }
    public List<string> TiposServico { get; set; } = [];
    public string Defeito { get; set; } = "";
    public string Solucao { get; set; } = "";
    public string Abertura { get; set; } = "";
    public string CodigoChave { get; set; } = "";
    public string CodigoConfiguracao { get; set; } = "";
    public string ChipTransponder { get; set; } = "";
    public string Ferramenta { get; set; } = "";
    public long ValorCentavos { get; set; }
    public string ClienteNome { get; set; } = "";
    public string ClienteTelefone { get; set; } = "";
    public bool Pago { get; set; } = true;
    public long Revisao { get; set; }
    public long? ExcluidoEm { get; set; }

    [JsonIgnore] public bool Pendente { get; set; }

    [JsonIgnore]
    public string DataFormatada =>
        DateTimeOffset.FromUnixTimeMilliseconds(DataServico).LocalDateTime.ToString("dd/MM/yyyy");

    [JsonIgnore]
    public string SegmentoFormatado =>
        Segmento == "RESIDENCIAL" ? "Residencial" : "Automotivo";

    [JsonIgnore]
    public string IdentificacaoPrincipal =>
        Segmento == "RESIDENCIAL"
            ? $"{TipoLocal} {(string.IsNullOrWhiteSpace(AmbientePorta) ? "" : "· " + AmbientePorta)}".Trim()
            : $"{Marca} {Modelo} {Ano}".Trim();

    [JsonIgnore]
    public string PlacaOuFechadura =>
        Segmento == "RESIDENCIAL"
            ? $"{MarcaFechadura} {ModeloFechadura}".Trim() is { Length: > 0 } f ? f : "—"
            : string.IsNullOrWhiteSpace(Placa) ? "—" : Placa;

    [JsonIgnore]
    public string TiposFormatados =>
        TiposServico.Count > 0
            ? string.Join(", ", TiposServico.Select(t => t.Replace('_', ' ')))
            : "—";

    [JsonIgnore]
    public string ValorFormatado =>
        (ValorCentavos / 100m).ToString("C2", new System.Globalization.CultureInfo("pt-BR"));

    [JsonIgnore]
    public string PagoFormatado => Pago ? "Pago" : "A receber";

    [JsonIgnore]
    public string SincroniaFormatada => Pendente ? "Pendente" : "Sincronizado";

    public string Resumo => Segmento == "RESIDENCIAL"
        ? $"[Residencial] {TipoLocal} {AmbientePorta}".Trim() + $" · {MarcaFechadura} {ModeloFechadura}".TrimEnd(' ', '·') + $" · {DateTimeOffset.FromUnixTimeMilliseconds(DataServico):dd/MM/yyyy}"
        : $"{(string.IsNullOrWhiteSpace(Placa) ? "Sem placa" : Placa)} · {Marca} {Modelo} · {DateTimeOffset.FromUnixTimeMilliseconds(DataServico):dd/MM/yyyy}";
}

internal static class Converter
{
    public static Atendimento AtendimentoDeJson(JsonElement e) => new()
    {
        Uuid = e.GetProperty("id").GetString() ?? "",
        Segmento = e.TryGetProperty("segmento", out var seg) && seg.ValueKind == JsonValueKind.String
            ? seg.GetString() ?? "AUTOMOTIVO" : "AUTOMOTIVO",
        Placa = e.GetProperty("placa").GetString() ?? "",
        Marca = e.GetProperty("marca").GetString() ?? "",
        Modelo = e.GetProperty("modelo").GetString() ?? "",
        Ano = e.GetProperty("ano").GetString() ?? "",
        Chassi = e.GetProperty("chassi").GetString() ?? "",
        TipoLocal = e.TryGetProperty("tipoLocal", out var tl) && tl.ValueKind == JsonValueKind.String ? tl.GetString() ?? "" : "",
        AmbientePorta = e.TryGetProperty("ambientePorta", out var ap) && ap.ValueKind == JsonValueKind.String ? ap.GetString() ?? "" : "",
        MarcaFechadura = e.TryGetProperty("marcaFechadura", out var mf) && mf.ValueKind == JsonValueKind.String ? mf.GetString() ?? "" : "",
        ModeloFechadura = e.TryGetProperty("modeloFechadura", out var modf) && modf.ValueKind == JsonValueKind.String ? modf.GetString() ?? "" : "",
        TipoCilindro = e.TryGetProperty("tipoCilindro", out var tc) && tc.ValueKind == JsonValueKind.String ? tc.GetString() ?? "" : "",
        EnderecoLocal = e.TryGetProperty("enderecoLocal", out var el) && el.ValueKind == JsonValueKind.String ? el.GetString() ?? "" : "",
        DataServico = e.GetProperty("dataServico").GetInt64(),
        TiposServico = e.TryGetProperty("tiposServico", out var t)
            ? t.EnumerateArray().Select(x => x.GetString() ?? "").ToList()
            : [],
        Defeito = e.GetProperty("defeito").GetString() ?? "",
        Solucao = e.GetProperty("solucao").GetString() ?? "",
        Abertura = e.GetProperty("abertura").GetString() ?? "",
        CodigoChave = e.GetProperty("codigoChave").GetString() ?? "",
        CodigoConfiguracao = e.GetProperty("codigoConfiguracao").GetString() ?? "",
        ChipTransponder = e.GetProperty("chipTransponder").GetString() ?? "",
        Ferramenta = e.GetProperty("ferramenta").GetString() ?? "",
        ValorCentavos = e.GetProperty("valorCentavos").GetInt64(),
        ClienteNome = e.GetProperty("clienteNome").GetString() ?? "",
        ClienteTelefone = e.GetProperty("clienteTelefone").GetString() ?? "",
        Pago = e.GetProperty("pago").GetBoolean(),
        Revisao = e.GetProperty("revisao").GetInt64(),
        ExcluidoEm = e.TryGetProperty("excluidoEm", out var x) && x.ValueKind != JsonValueKind.Null
            ? x.GetInt64() : null,
    };

    public static object Dados(Atendimento a) => new
    {
        segmento = string.IsNullOrWhiteSpace(a.Segmento) ? "AUTOMOTIVO" : a.Segmento,
        placa = a.Placa,
        marca = a.Marca,
        modelo = a.Modelo,
        ano = a.Ano,
        chassi = a.Chassi,
        tipoLocal = a.TipoLocal,
        ambientePorta = a.AmbientePorta,
        marcaFechadura = a.MarcaFechadura,
        modeloFechadura = a.ModeloFechadura,
        tipoCilindro = a.TipoCilindro,
        enderecoLocal = a.EnderecoLocal,
        dataServico = a.DataServico,
        tiposServico = a.TiposServico.Count > 0 ? a.TiposServico : new List<string> { "OUTRO" },
        defeito = a.Defeito,
        solucao = a.Solucao,
        abertura = a.Abertura,
        codigoChave = a.CodigoChave,
        codigoConfiguracao = a.CodigoConfiguracao,
        chipTransponder = a.ChipTransponder,
        ferramenta = a.Ferramenta,
        valorCentavos = a.ValorCentavos,
        clienteNome = a.ClienteNome,
        clienteTelefone = a.ClienteTelefone,
        pago = a.Pago,
    };

    public static ArmazenamentoLocal.Ficha FichaDeJson(JsonElement e) => new()
    {
        Uuid = e.GetProperty("id").GetString() ?? "",
        Equipamento = e.GetProperty("equipamento").GetString() ?? "",
        Marca = e.GetProperty("marca").GetString() ?? "",
        Modelo = e.GetProperty("modelo").GetString() ?? "",
        Ano = e.GetProperty("ano").GetString() ?? "",
        Sistema = e.GetProperty("sistema").GetString() ?? "",
        Descricao = e.GetProperty("descricao").GetString() ?? "",
        Chip = e.GetProperty("chip").GetString() ?? "",
        Chave = e.GetProperty("chave").GetString() ?? "",
        Telecomando = e.GetProperty("telecomando").GetString() ?? "",
        Frequencia = e.GetProperty("frequencia").GetString() ?? "",
        Avisos = e.GetProperty("avisos").GetString() ?? "",
        Funcoes = e.GetProperty("funcoes").GetString() ?? "",
        Manual = e.GetProperty("manual").GetString() ?? "",
        Confirmado = e.GetProperty("confirmado").GetBoolean(),
        Origem = e.GetProperty("origem").GetString() ?? "",
        Revisao = e.GetProperty("revisao").GetInt64(),
        ExcluidoEm = e.TryGetProperty("excluidoEm", out var x) && x.ValueKind != JsonValueKind.Null
            ? x.GetInt64() : null,
    };

    public static object Dados(ArmazenamentoLocal.Ficha f) => new
    {
        equipamento = f.Equipamento,
        marca = f.Marca,
        modelo = f.Modelo,
        ano = f.Ano,
        sistema = f.Sistema,
        descricao = f.Descricao,
        chip = f.Chip,
        chave = f.Chave,
        telecomando = f.Telecomando,
        frequencia = f.Frequencia,
        avisos = f.Avisos,
        funcoes = f.Funcoes,
        manual = f.Manual,
        confirmado = f.Confirmado,
        origem = f.Origem,
    };
}
