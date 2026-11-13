using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace ChaveiroCar.Windows.Services;

public sealed record ResultadoOAuthDesktop(
    bool Ok,
    string? Acesso,
    string? Refresh,
    string? Email,
    string? EmpresaId,
    string? IdToken,
    string? Erro);

public static class AutenticadorGoogleDesktop
{
    public static async Task<ResultadoOAuthDesktop> AutenticarNoNavegadorAsync(
        string baseUrl,
        CancellationToken cancelamento = default)
    {
        int porta = ObterPortaLivre();
        string prefixo = $"http://127.0.0.1:{porta}/callback/";
        string redirectUri = $"http://127.0.0.1:{porta}/callback";

        using var listener = new HttpListener();
        listener.Prefixes.Add(prefixo);
        listener.Start();

        string clientId = Environment.GetEnvironmentVariable("CHAVEIROCAR_GOOGLE_CLIENT_ID") ?? "";
        string urlNavegador = $"{baseUrl.TrimEnd('/')}/api/v1/auth/google/navegador?redirect_uri={Uri.EscapeDataString(redirectUri)}";
        if (!string.IsNullOrWhiteSpace(clientId))
        {
            urlNavegador += $"&client_id={Uri.EscapeDataString(clientId)}";
        }

        Process.Start(new ProcessStartInfo
        {
            FileName = urlNavegador,
            UseShellExecute = true,
        });

        using var cts = CancellationTokenSource.CreateLinkedTokenSource(cancelamento);
        cts.CancelAfter(TimeSpan.FromMinutes(3));

        await using var reg = cts.Token.Register(() =>
        {
            try { listener.Stop(); } catch { }
        });

        try
        {
            var contexto = await listener.GetContextAsync();
            var query = contexto.Request.QueryString;
            string? acesso = query["acesso"];
            string? refresh = query["refresh"];
            string? email = query["email"];
            string? empresaId = query["empresaId"];
            string? idToken = query["id_token"] ?? query["idToken"];
            string? erro = query["error"] ?? query["erro"];

            bool sucesso = (!string.IsNullOrWhiteSpace(acesso) && !string.IsNullOrWhiteSpace(refresh)) ||
                           !string.IsNullOrWhiteSpace(idToken);

            string htmlResposta = sucesso
                ? """
                  <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>ChaveiroCar</title>
                  <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f172a;color:#f8fafc;font-family:"Segoe UI",sans-serif;}
                  .box{background:#1e293b;border:1px solid #334155;border-radius:16px;padding:32px;max-width:400px;text-align:center;box-shadow:0 20px 40px rgba(0,0,0,.4);}
                  h2{color:#34d399;margin:0 0 8px;}p{color:#94a3b8;margin:0;font-size:14px;}</style></head>
                  <body><div class="box"><h2>Conectado com sucesso!</h2><p>Você já pode fechar esta aba e voltar para o aplicativo ChaveiroCar no Windows.</p></div>
                  <script>setTimeout(()=>window.close(),1500);</script></body></html>
                  """
                : """
                  <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>ChaveiroCar</title>
                  <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f172a;color:#f8fafc;font-family:"Segoe UI",sans-serif;}
                  .box{background:#1e293b;border:1px solid #334155;border-radius:16px;padding:32px;max-width:400px;text-align:center;}
                  h2{color:#f87171;margin:0 0 8px;}p{color:#94a3b8;margin:0;font-size:14px;}</style></head>
                  <body><div class="box"><h2>Autenticação não concluída</h2><p>Volte ao aplicativo ChaveiroCar e tente novamente.</p></div></body></html>
                  """;

            byte[] buffer = Encoding.UTF8.GetBytes(htmlResposta);
            contexto.Response.ContentType = "text/html; charset=utf-8";
            contexto.Response.ContentLength64 = buffer.Length;
            await contexto.Response.OutputStream.WriteAsync(buffer, CancellationToken.None);
            contexto.Response.Close();

            return new ResultadoOAuthDesktop(sucesso, acesso, refresh, email, empresaId, idToken, erro);
        }
        catch (Exception) when (cts.IsCancellationRequested)
        {
            return new ResultadoOAuthDesktop(false, null, null, null, null, null, "Tempo limite esgotado ou operação cancelada.");
        }
    }

    private static int ObterPortaLivre()
    {
        using var socket = new TcpListener(IPAddress.Loopback, 0);
        socket.Start();
        int porta = ((IPEndPoint)socket.LocalEndpoint).Port;
        socket.Stop();
        return porta;
    }
}
