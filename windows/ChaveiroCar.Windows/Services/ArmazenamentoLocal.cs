using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Cache offline + outbox em JSON (%LocalAppData%\ChaveiroCar).
/// Offline o app lê/escreve aqui; <see cref="MotorSincronia"/> sobe quando houver rede.
/// </summary>
public sealed class ArmazenamentoLocal
{
    private static readonly JsonSerializerOptions Opcoes = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    private readonly string _arquivo;
    private readonly string _arquivoTokens;

    public EstadoLocal Estado { get; private set; } = new();

    public ArmazenamentoLocal()
    {
        var pasta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ChaveiroCar");
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "cache.json");
        _arquivoTokens = Path.Combine(pasta, "tokens.bin");
        Carregar();
    }

    private void Carregar()
    {
        try
        {
            if (!File.Exists(_arquivo)) return;
            var texto = File.ReadAllText(_arquivo);
            var estado = JsonSerializer.Deserialize<EstadoLocal>(texto, Opcoes);
            if (estado is not null) Estado = estado;
        }
        catch
        {
            // Cache corrompido não pode impedir o app de abrir.
            Estado = new EstadoLocal();
        }
        // Tokens em arquivo DPAPI separado (fora do JSON de cache).
        Estado.Acesso = null;
        Estado.Refresh = null;
        try
        {
            if (!File.Exists(_arquivoTokens)) return;
            var protegido = File.ReadAllBytes(_arquivoTokens);
            var texto = Encoding.UTF8.GetString(ProtectedData.Unprotect(protegido, null, DataProtectionScope.CurrentUser));
            var par = texto.Split('\n', 2);
            if (par.Length == 2) { Estado.Acesso = par[0]; Estado.Refresh = par[1]; }
        }
        catch { Estado.Acesso = null; Estado.Refresh = null; }
    }

    public void Salvar()
    {
        var copia = JsonSerializer.Serialize(Estado, Opcoes);
        var semTokens = JsonSerializer.Deserialize<EstadoLocal>(copia, Opcoes) ?? new EstadoLocal();
        semTokens.Acesso = null;
        semTokens.Refresh = null;
        File.WriteAllText(_arquivo, JsonSerializer.Serialize(semTokens, Opcoes));
        try
        {
            if (string.IsNullOrWhiteSpace(Estado.Acesso) && string.IsNullOrWhiteSpace(Estado.Refresh))
            {
                File.Delete(_arquivoTokens);
            }
            else
            {
                var texto = $"{Estado.Acesso ?? ""}\n{Estado.Refresh ?? ""}";
                File.WriteAllBytes(_arquivoTokens,
                    ProtectedData.Protect(Encoding.UTF8.GetBytes(texto), null, DataProtectionScope.CurrentUser));
            }
        }
        catch { /* token não persistido continua em memória */ }
    }

    public sealed class EstadoLocal
    {
        public string BaseUrl { get; set; } = "http://localhost:8787";
        // tokens protegido por DPAPI (CurrentUser); fica em tokens.bin, fora do cache.json.
        public string? Acesso { get; set; }
        public string? Refresh { get; set; }
        public string Email { get; set; } = "";
        public string EmpresaId { get; set; } = "";
        public long Cursor { get; set; }
        public List<Atendimento> Atendimentos { get; set; } = [];
        public List<Ficha> Fichas { get; set; } = [];
        public List<FotoLocal> Fotos { get; set; } = [];
        public List<OperacaoPendente> Outbox { get; set; } = [];
        public List<Conflito> Conflitos { get; set; } = [];
    }

    public sealed class OperacaoPendente
    {
        public string OpId { get; set; } = Guid.NewGuid().ToString();
        public string Entidade { get; set; } = "atendimento"; // atendimento | ficha | foto
        public string Uuid { get; set; } = "";
        public string Tipo { get; set; } = "upsert"; // upsert | exclusao
        public long BaseRevisao { get; set; }
        public Atendimento? Dados { get; set; }
        public Ficha? DadosFicha { get; set; }
        public FotoLocal? DadosFoto { get; set; }
    }

    public sealed class Conflito
    {
        public string Uuid { get; set; } = "";
        public string Entidade { get; set; } = "atendimento";
        public long RevisaoServidor { get; set; }
        public string Placa { get; set; } = "";
    }

    public sealed class Ficha
    {
        public string Uuid { get; set; } = "";
        public string Equipamento { get; set; } = "";
        public string Marca { get; set; } = "";
        public string Modelo { get; set; } = "";
        public string Ano { get; set; } = "";
        public string Sistema { get; set; } = "";
        public string Descricao { get; set; } = "";
        public string Chip { get; set; } = "";
        public string Chave { get; set; } = "";
        public string Telecomando { get; set; } = "";
        public string Frequencia { get; set; } = "";
        public string Avisos { get; set; } = "";
        public string Funcoes { get; set; } = "";
        public string Manual { get; set; } = "";
        public bool Confirmado { get; set; }
        public string Origem { get; set; } = "";
        public long Revisao { get; set; }
        public long? ExcluidoEm { get; set; }
        [JsonIgnore] public bool Pendente { get; set; }
    }

    public sealed class FotoLocal
    {
        public string Uuid { get; set; } = "";
        public string AtendimentoUuid { get; set; } = "";
        public string Categoria { get; set; } = "CARRO";
        public string Legenda { get; set; } = "";
        public int Ordem { get; set; }
        public long Revisao { get; set; }
        [JsonIgnore] public bool Pendente { get; set; }
    }
}
