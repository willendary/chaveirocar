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
        _http.BaseAddress = new Uri(baseUrl ?? "http://localhost:8787");
    }

    public void DefinirTokens(string? acesso, string? refresh)
    {
        _acesso = acesso;
        _refresh = refresh;
    }

    public async Task<ResultadoLogin> EntrarAsync(string email, string senha)
    {
        using var resposta = await PostAsync("/api/v1/auth/login", new { email, senha });
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
                item.GetProperty("nome").GetString() ?? ""));
        }
        return new ResultadoLogin(true, doc.GetProperty("usuario").GetProperty("email").GetString(), empresas);
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
        var resposta = await GetAsync("/api/v1/empresas");
        resposta.EnsureSuccessStatusCode();
        var doc = await LerJson(resposta);
        var lista = new List<Empresa>();
        foreach (var item in doc.GetProperty("itens").EnumerateArray())
        {
            lista.Add(new Empresa(
                item.GetProperty("id").GetString() ?? "",
                item.GetProperty("nome").GetString() ?? ""));
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
                Categoria = item.GetProperty("categoria").GetString() ?? "CARRO",
                Legenda = item.GetProperty("legenda").GetString() ?? "",
                Ordem = item.GetProperty("ordem").GetInt32(),
                Revisao = item.GetProperty("revisao").GetInt64(),
                Url = item.GetProperty("url").GetString() ?? "",
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
        using var requisicao = new HttpRequestMessage(HttpMethod.Get, urlRelativa);
        if (_acesso is not null) requisicao.Headers.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
        using var resposta = await _http.SendAsync(requisicao);
        resposta.EnsureSuccessStatusCode();
        return await resposta.Content.ReadAsByteArrayAsync();
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
            var resposta = await _http.PostAsync(caminho, conteudo);
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

    public sealed record Empresa(string Id, string Nome);
    public sealed record ResultadoLogin(bool Ok, string? Email, List<Empresa> Empresas);

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
        public string Categoria { get; set; } = "CARRO";
        public string Legenda { get; set; } = "";
        public int Ordem { get; set; }
        public long Revisao { get; set; }
        public string Url { get; set; } = "";
        public bool Excluido { get; set; }
    }
}

public sealed class Atendimento
{
    public string Uuid { get; set; } = "";
    public string Placa { get; set; } = "";
    public string Marca { get; set; } = "";
    public string Modelo { get; set; } = "";
    public string Ano { get; set; } = "";
    public string Chassi { get; set; } = "";
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

    public string Resumo => $"{Placa} · {Marca} {Modelo} · {DateTimeOffset.FromUnixTimeMilliseconds(DataServico):dd/MM/yyyy}";
}

internal static class Converter
{
    public static Atendimento AtendimentoDeJson(JsonElement e) => new()
    {
        Uuid = e.GetProperty("id").GetString() ?? "",
        Placa = e.GetProperty("placa").GetString() ?? "",
        Marca = e.GetProperty("marca").GetString() ?? "",
        Modelo = e.GetProperty("modelo").GetString() ?? "",
        Ano = e.GetProperty("ano").GetString() ?? "",
        Chassi = e.GetProperty("chassi").GetString() ?? "",
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
        placa = a.Placa,
        marca = a.Marca,
        modelo = a.Modelo,
        ano = a.Ano,
        chassi = a.Chassi,
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
