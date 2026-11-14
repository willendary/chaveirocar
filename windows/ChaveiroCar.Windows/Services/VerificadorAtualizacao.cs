using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Reflection;
using System.Text.Json;
using System.Windows;

namespace ChaveiroCar.Windows.Services;

public sealed record AtualizacaoDisponivel(
    string Versao,
    string Url,
    string NomeArquivo,
    long TamanhoBytes,
    bool EhInstalador);

public static class VerificadorAtualizacao
{
    private const string UrlApi = "https://api.github.com/repos/willendary/chaveirocar/releases/latest";
    private const string InstaladorWindows = "ChaveiroCar-Setup.exe";
    private const string ExecutavelWindows = "ChaveiroCar.Windows.exe";
    private const string PacoteWindows = "chaveirocar-windows-x64.zip";

    private static readonly HttpClient Cliente = new() { Timeout = TimeSpan.FromMinutes(10) };

    static VerificadorAtualizacao()
    {
        Cliente.DefaultRequestHeaders.UserAgent.ParseAdd("ChaveiroCar-Windows");
        Cliente.DefaultRequestHeaders.Accept.Add(
            new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
    }

    public static async Task<AtualizacaoDisponivel?> VerificarAsync()
    {
        try
        {
            using var timeoutCts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            using var resposta = await Cliente.GetAsync(UrlApi, timeoutCts.Token);
            if (!resposta.IsSuccessStatusCode) return null;
            using var json = JsonDocument.Parse(await resposta.Content.ReadAsStreamAsync(timeoutCts.Token));
            var raiz = json.RootElement;
            var tag = raiz.GetProperty("tag_name").GetString()?.TrimStart('v');
            if (!Version.TryParse(tag, out var versaoNova)) return null;

            var completa = Assembly.GetEntryAssembly()?.GetName().Version ?? new Version(0, 0);
            var instalada = new Version(completa.Major, completa.Minor, Math.Max(completa.Build, 0));
            var publicada = new Version(versaoNova.Major, versaoNova.Minor, Math.Max(versaoNova.Build, 0));
            if (publicada <= instalada) return null;

            if (!raiz.TryGetProperty("assets", out var ativos)) return null;

            AtualizacaoDisponivel? setup = null;
            AtualizacaoDisponivel? exe = null;
            AtualizacaoDisponivel? zip = null;

            foreach (var ativo in ativos.EnumerateArray())
            {
                var nomeAtivo = ativo.GetProperty("name").GetString() ?? "";
                var url = ativo.GetProperty("browser_download_url").GetString() ?? "";
                var tamanho = ativo.TryGetProperty("size", out var s) ? s.GetInt64() : 0L;
                if (string.IsNullOrWhiteSpace(url)) continue;

                if (nomeAtivo.Equals(InstaladorWindows, StringComparison.OrdinalIgnoreCase))
                {
                    setup = new AtualizacaoDisponivel(tag!, url, nomeAtivo, tamanho, true);
                }
                else if (nomeAtivo.Equals(ExecutavelWindows, StringComparison.OrdinalIgnoreCase))
                {
                    exe = new AtualizacaoDisponivel(tag!, url, nomeAtivo, tamanho, false);
                }
                else if (nomeAtivo.Equals(PacoteWindows, StringComparison.OrdinalIgnoreCase))
                {
                    zip = new AtualizacaoDisponivel(tag!, url, nomeAtivo, tamanho, false);
                }
            }

            return setup ?? exe ?? zip;
        }
        catch
        {
            // Falha de rede/GitHub não pode impedir o uso do aplicativo.
            return null;
        }
    }

    public static async Task<string> BaixarAtualizacaoAsync(
        AtualizacaoDisponivel atualizacao,
        IProgress<(long Baixados, long? Total)> progresso,
        CancellationToken cancelamento)
    {
        var pastaTemp = Path.Combine(Path.GetTempPath(), "ChaveiroCar_Updates");
        Directory.CreateDirectory(pastaTemp);
        var arquivoDestino = Path.Combine(pastaTemp, $"{Path.GetFileNameWithoutExtension(atualizacao.NomeArquivo)}-{atualizacao.Versao}{Path.GetExtension(atualizacao.NomeArquivo)}");

        using var resposta = await Cliente.GetAsync(atualizacao.Url, HttpCompletionOption.ResponseHeadersRead, cancelamento);
        resposta.EnsureSuccessStatusCode();

        var tamanhoTotal = resposta.Content.Headers.ContentLength ?? (atualizacao.TamanhoBytes > 0 ? atualizacao.TamanhoBytes : null);

        await using var fluxoOrigem = await resposta.Content.ReadAsStreamAsync(cancelamento);
        await using var fluxoDestino = new FileStream(arquivoDestino, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);

        var buffer = new byte[81920];
        long totalLido = 0;
        int lidos;

        while ((lidos = await fluxoOrigem.ReadAsync(buffer.AsMemory(0, buffer.Length), cancelamento)) > 0)
        {
            await fluxoDestino.WriteAsync(buffer.AsMemory(0, lidos), cancelamento);
            totalLido += lidos;
            progresso.Report((totalLido, tamanhoTotal));
        }

        return arquivoDestino;
    }

    public static (string Caminho, bool EhInstalador) ExtrairExecutavelDeZip(string caminhoZip, string pastaDestino)
    {
        Directory.CreateDirectory(pastaDestino);
        ZipFile.ExtractToDirectory(caminhoZip, pastaDestino, overwriteFiles: true);
        var executaveis = Directory.GetFiles(pastaDestino, "*.exe", SearchOption.AllDirectories)
            .Where(f => new FileInfo(f).Length > 0)
            .ToList();

        var setup = executaveis.FirstOrDefault(f =>
            Path.GetFileName(f).Equals(InstaladorWindows, StringComparison.OrdinalIgnoreCase));
        if (setup is not null) return (setup, true);

        var principal = executaveis.FirstOrDefault(f =>
            Path.GetFileName(f).Equals(ExecutavelWindows, StringComparison.OrdinalIgnoreCase));
        if (principal is not null) return (principal, false);

        var qualquer = executaveis.FirstOrDefault();
        if (qualquer is not null) return (qualquer, false);

        throw new InvalidOperationException("Nenhum executável válido encontrado no pacote .zip de atualização.");
    }

    private static bool DiretorioPermiteEscrita(string caminhoArquivo)
    {
        var diretorio = Path.GetDirectoryName(caminhoArquivo);
        if (string.IsNullOrWhiteSpace(diretorio)) return false;
        try
        {
            var teste = Path.Combine(diretorio, $".chaveirocar-perm-{Guid.NewGuid():N}.tmp");
            File.WriteAllText(teste, "");
            File.Delete(teste);
            return true;
        }
        catch (UnauthorizedAccessException)
        {
            return false;
        }
    }

    public static void ExecutarAtualizacao(string caminhoArquivo, bool ehInstalador)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(caminhoArquivo) || !File.Exists(caminhoArquivo) || new FileInfo(caminhoArquivo).Length <= 0)
            {
                MessageBox.Show("O arquivo de atualização é inválido ou está vazio.",
                    "Erro na atualização", MessageBoxButton.OK, MessageBoxImage.Error);
                return;
            }

            if (caminhoArquivo.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
            {
                var pastaExtracao = Path.Combine(Path.GetTempPath(), "ChaveiroCar_Updates", $"extraido-{Guid.NewGuid():N}");
                (caminhoArquivo, ehInstalador) = ExtrairExecutavelDeZip(caminhoArquivo, pastaExtracao);
            }

            if (!File.Exists(caminhoArquivo) || new FileInfo(caminhoArquivo).Length <= 0 ||
                !caminhoArquivo.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
            {
                MessageBox.Show("Nenhum executável válido (.exe) foi encontrado para concluir a atualização.",
                    "Erro na atualização", MessageBoxButton.OK, MessageBoxImage.Error);
                return;
            }

            if (ehInstalador)
            {
                var psi = new ProcessStartInfo
                {
                    FileName = caminhoArquivo,
                    Arguments = "/CLOSEAPPLICATIONS /SILENT",
                    UseShellExecute = true,
                };
                Process.Start(psi);
            }
            else
            {
                var processoAtual = Environment.ProcessPath ?? Path.Combine(AppContext.BaseDirectory, ExecutavelWindows);
                var scriptBatch = Path.Combine(Path.GetTempPath(), $"atualizar_chaveirocar_{Guid.NewGuid():N}.cmd");
                File.WriteAllText(scriptBatch, $"""
                @echo off
                timeout /t 2 /nobreak > NUL
                copy /y "{caminhoArquivo}" "{processoAtual}"
                start "" "{processoAtual}"
                del "%~f0"
                """);

                var precisaElevacao = !DiretorioPermiteEscrita(processoAtual);
                var psi = new ProcessStartInfo("cmd.exe", $"/c \"{scriptBatch}\"")
                {
                    CreateNoWindow = true,
                    UseShellExecute = precisaElevacao,
                    WindowStyle = ProcessWindowStyle.Hidden,
                    Verb = precisaElevacao ? "runas" : "",
                };
                Process.Start(psi);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Não foi possível iniciar a atualização: {ex.Message}",
                "Erro na atualização", MessageBoxButton.OK, MessageBoxImage.Error);
            return;
        }

        Application.Current.Shutdown();
    }

    public static void AbrirDownload(AtualizacaoDisponivel atualizacao)
    {
        Process.Start(new ProcessStartInfo(atualizacao.Url) { UseShellExecute = true });
    }
}
