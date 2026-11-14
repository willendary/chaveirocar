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
    private readonly string _arquivoBackup;
    private readonly string _arquivoTokens;
    private readonly string _arquivoTokensBackup;

    public EstadoLocal Estado { get; private set; } = new();

    public ArmazenamentoLocal() : this(Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ChaveiroCar"))
    {
    }

    public ArmazenamentoLocal(string pasta)
    {
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "cache.json");
        _arquivoBackup = Path.Combine(pasta, "cache.json.bak");
        _arquivoTokens = Path.Combine(pasta, "tokens.bin");
        _arquivoTokensBackup = Path.Combine(pasta, "tokens.bin.bak");
        Carregar();
    }

    private static EstadoLocal? TentarLerEstado(string caminho)
    {
        try
        {
            if (!File.Exists(caminho) || new FileInfo(caminho).Length == 0) return null;
            var texto = File.ReadAllText(caminho);
            return JsonSerializer.Deserialize<EstadoLocal>(texto, Opcoes);
        }
        catch
        {
            return null;
        }
    }

    private static (string? Acesso, string? Refresh)? TentarLerTokens(string caminho)
    {
        try
        {
            if (!File.Exists(caminho) || new FileInfo(caminho).Length == 0) return null;
            var protegido = File.ReadAllBytes(caminho);
            var texto = Encoding.UTF8.GetString(ProtectedData.Unprotect(protegido, null, DataProtectionScope.CurrentUser));
            var par = texto.Split('\n', 2);
            if (par.Length == 2) return (par[0], par[1]);
            return null;
        }
        catch
        {
            return null;
        }
    }

    private static void GravarAtomico(string caminhoDestino, string caminhoBackup, byte[] dados)
    {
        var caminhoTemp = $"{caminhoDestino}.{Guid.NewGuid():N}.tmp";
        try
        {
            using (var fs = new FileStream(caminhoTemp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 4096, FileOptions.WriteThrough))
            {
                fs.Write(dados, 0, dados.Length);
                fs.Flush(flushToDisk: true);
            }

            if (File.Exists(caminhoDestino))
            {
                try
                {
                    File.Replace(caminhoTemp, caminhoDestino, caminhoBackup, ignoreMetadataErrors: true);
                }
                catch (IOException)
                {
                    if (new FileInfo(caminhoDestino).Length > 0)
                    {
                        File.Copy(caminhoDestino, caminhoBackup, overwrite: true);
                    }
                    File.Move(caminhoTemp, caminhoDestino, overwrite: true);
                }
            }
            else
            {
                File.Move(caminhoTemp, caminhoDestino, overwrite: true);
            }
        }
        finally
        {
            if (File.Exists(caminhoTemp))
            {
                try { File.Delete(caminhoTemp); } catch { }
            }
        }
    }

    private void Carregar()
    {
        var estado = TentarLerEstado(_arquivo);
        if (estado is null)
        {
            estado = TentarLerEstado(_arquivoBackup);
            if (estado is not null)
            {
                try { File.Copy(_arquivoBackup, _arquivo, overwrite: true); } catch { }
            }
        }
        if (estado is not null)
        {
            Estado = estado;
            Estado.CursoresPorEmpresa ??= [];
            if (!string.IsNullOrWhiteSpace(Estado.EmpresaId) && Estado.Cursor > 0 &&
                !Estado.CursoresPorEmpresa.ContainsKey(Estado.EmpresaId))
                Estado.CursoresPorEmpresa[Estado.EmpresaId] = Estado.Cursor;
        }
        else
        {
            Estado = new EstadoLocal();
        }

        // Tokens em arquivo DPAPI separado (fora do JSON de cache).
        Estado.Acesso = null;
        Estado.Refresh = null;
        var tokens = TentarLerTokens(_arquivoTokens);
        if (tokens is null)
        {
            tokens = TentarLerTokens(_arquivoTokensBackup);
            if (tokens is not null)
            {
                try { File.Copy(_arquivoTokensBackup, _arquivoTokens, overwrite: true); } catch { }
            }
        }
        if (tokens is { } par)
        {
            Estado.Acesso = par.Acesso;
            Estado.Refresh = par.Refresh;
        }
    }

    public void Salvar()
    {
        var copia = JsonSerializer.Serialize(Estado, Opcoes);
        var semTokens = JsonSerializer.Deserialize<EstadoLocal>(copia, Opcoes) ?? new EstadoLocal();
        semTokens.Acesso = null;
        semTokens.Refresh = null;
        var jsonBytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(semTokens, Opcoes));
        GravarAtomico(_arquivo, _arquivoBackup, jsonBytes);
        try
        {
            if (string.IsNullOrWhiteSpace(Estado.Acesso) && string.IsNullOrWhiteSpace(Estado.Refresh))
            {
                if (File.Exists(_arquivoTokens)) File.Delete(_arquivoTokens);
                if (File.Exists(_arquivoTokensBackup)) File.Delete(_arquivoTokensBackup);
            }
            else
            {
                var texto = $"{Estado.Acesso ?? ""}\n{Estado.Refresh ?? ""}";
                var bytesProtegidos = ProtectedData.Protect(Encoding.UTF8.GetBytes(texto), null, DataProtectionScope.CurrentUser);
                GravarAtomico(_arquivoTokens, _arquivoTokensBackup, bytesProtegidos);
            }
        }
        catch { /* token não persistido continua em memória */ }
    }

    public sealed class EstadoLocal
    {
        public string BaseUrl { get; set; } = "https://chaveirocar-api.onrender.com";
        // tokens protegido por DPAPI (CurrentUser); fica em tokens.bin, fora do cache.json.
        public string? Acesso { get; set; }
        public string? Refresh { get; set; }
        public string Email { get; set; } = "";
        public string EmpresaId { get; set; } = "";
        public long Cursor { get; set; }
        public Dictionary<string, long> CursoresPorEmpresa { get; set; } = [];
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
        public bool ServidorExcluido { get; set; }
        [JsonIgnore] public string SituacaoRemota => ServidorExcluido ? "Excluído no servidor" : $"Revisão #{RevisaoServidor}";
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
        [JsonIgnore] public string ConfirmadoFormatado => Confirmado ? "Sim" : "Não";
        [JsonIgnore] public string SincroniaFormatada => Pendente ? "Pendente" : "Sincronizado";
    }

    public sealed class FotoLocal
    {
        public string Uuid { get; set; } = "";
        public string AtendimentoUuid { get; set; } = "";
        public string FichaUuid { get; set; } = "";
        public string Categoria { get; set; } = "CARRO";
        public string Legenda { get; set; } = "";
        public int Ordem { get; set; }
        public long Revisao { get; set; }
        public string CaminhoLocal { get; set; } = "";
        public string UrlRemota { get; set; } = "";
        public string MimeType { get; set; } = "image/jpeg";
        public string Anotacoes { get; set; } = "[]";
        public bool Pendente { get; set; }
    }
}
