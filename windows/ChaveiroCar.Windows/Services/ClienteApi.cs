using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Cliente HTTP mínimo da API ChaveiroCar (contracts/openapi.json).
/// Token em memória; persistência segura entra na próxima iteração.
/// </summary>
public sealed class ClienteApi : IDisposable
{
    private readonly HttpClient _http = new();
    private string? _acesso;

    public ClienteApi(string? baseUrl = null)
    {
        _http.BaseAddress = new Uri(baseUrl ?? "http://localhost:8787");
    }

    public async Task<bool> EntrarAsync(string email, string senha)
    {
        using var body = new StringContent(
            JsonSerializer.Serialize(new { email, senha }), Encoding.UTF8, "application/json");
        using var resposta = await _http.PostAsync("/api/v1/auth/login", body);
        if (!resposta.IsSuccessStatusCode) return false;
        using var doc = await JsonDocument.ParseAsync(await resposta.Content.ReadAsStreamAsync());
        _acesso = doc.RootElement.GetProperty("tokens").GetProperty("acesso").GetString();
        return true;
    }

    public async Task<IReadOnlyList<Atendimento>> ListarAtendimentosAsync(string empresaId, string busca = "")
    {
        using var requisicao = new HttpRequestMessage(HttpMethod.Get,
            $"/api/v1/empresas/{empresaId}/atendimentos?busca={Uri.EscapeDataString(busca)}");
        if (_acesso is not null) requisicao.Headers.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
        using var resposta = await _http.SendAsync(requisicao);
        resposta.EnsureSuccessStatusCode();
        var lista = await JsonSerializer.DeserializeAsync<ListaResposta>(
            await resposta.Content.ReadAsStreamAsync());
        return lista?.Itens ?? new List<Atendimento>();
    }

    public async Task<LotePullPublico> SincronizarAsync(string empresaId, long cursor)
    {
        using var requisicao = new HttpRequestMessage(HttpMethod.Get,
            $"/api/v1/empresas/{empresaId}/sync?cursor={cursor}");
        if (_acesso is not null) requisicao.Headers.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _acesso);
        using var resposta = await _http.SendAsync(requisicao);
        resposta.EnsureSuccessStatusCode();
        var lote = await JsonSerializer.DeserializeAsync<LotePull>(
            await resposta.Content.ReadAsStreamAsync());
        return new LotePullPublico
        {
            Cursor = lote?.Cursor ?? 0,
            TemMais = lote?.TemMais ?? false,
            Atendimentos = lote?.Atendimentos ?? new List<Atendimento>()
        };
    }

    public void Dispose() => _http.Dispose();

    private sealed class ListaResposta
    {
        public List<Atendimento> Itens { get; set; } = [];
    }

    private sealed class LotePull
    {
        public long Cursor { get; set; }
        public bool TemMais { get; set; }
        public List<Atendimento> Atendimentos { get; set; } = [];
    }

    public sealed class LotePullPublico
    {
        public long Cursor { get; set; }
        public bool TemMais { get; set; }
        public List<Atendimento> Atendimentos { get; set; } = [];
    }
}

public sealed class Atendimento
{
    public string Id { get; set; } = "";
    public string Placa { get; set; } = "";
    public string Marca { get; set; } = "";
    public string Modelo { get; set; } = "";
    public List<string> TiposServico { get; set; } = [];
    public int Revisao { get; set; }
}
