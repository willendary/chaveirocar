using System.Diagnostics;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Reflection;
using System.Text.Json;

namespace ChaveiroCar.Windows.Services;

public sealed record AtualizacaoDisponivel(string Versao, string Url);

public static class VerificadorAtualizacao
{
    private const string UrlApi = "https://api.github.com/repos/willendary/chaveirocar/releases/latest";
    private const string PacoteWindows = "chaveirocar-windows-x64.zip";
    private static readonly HttpClient Cliente = new() { Timeout = TimeSpan.FromSeconds(5) };

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
            using var resposta = await Cliente.GetAsync(UrlApi);
            if (!resposta.IsSuccessStatusCode) return null;
            using var json = JsonDocument.Parse(await resposta.Content.ReadAsStreamAsync());
            var raiz = json.RootElement;
            var tag = raiz.GetProperty("tag_name").GetString()?.TrimStart('v');
            if (!Version.TryParse(tag, out var versaoNova)) return null;

            var completa = Assembly.GetEntryAssembly()?.GetName().Version ?? new Version(0, 0);
            var instalada = new Version(completa.Major, completa.Minor, Math.Max(completa.Build, 0));
            var publicada = new Version(versaoNova.Major, versaoNova.Minor, Math.Max(versaoNova.Build, 0));
            if (publicada <= instalada) return null;

            if (!raiz.TryGetProperty("assets", out var ativos)) return null;
            foreach (var ativo in ativos.EnumerateArray())
            {
                if (ativo.GetProperty("name").GetString() != PacoteWindows) continue;
                var url = ativo.GetProperty("browser_download_url").GetString();
                return string.IsNullOrWhiteSpace(url) ? null : new AtualizacaoDisponivel(tag!, url);
            }
        }
        catch
        {
            // Falha de rede/GitHub não pode impedir o uso do aplicativo.
        }
        return null;
    }

    public static void AbrirDownload(AtualizacaoDisponivel atualizacao)
    {
        Process.Start(new ProcessStartInfo(atualizacao.Url) { UseShellExecute = true });
    }
}
