using System.Windows;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class App : Application
{
    public const string UrlApiPadrao = "https://chaveirocar-api.onrender.com";

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        var local = new ArmazenamentoLocal();
        var envUrl = Environment.GetEnvironmentVariable("CHAVEIROCAR_API_URL");
        if (!string.IsNullOrWhiteSpace(envUrl))
        {
            local.Estado.BaseUrl = envUrl.TrimEnd('/');
        }
        else if (string.IsNullOrWhiteSpace(local.Estado.BaseUrl))
        {
            local.Estado.BaseUrl = UrlApiPadrao;
        }

        IniciarCicloDeSessao(local);
    }

    private void IniciarCicloDeSessao(ArmazenamentoLocal local)
    {
        while (true)
        {
            if (string.IsNullOrWhiteSpace(local.Estado.Acesso))
            {
                var login = new LoginWindow(local);
                if (login.ShowDialog() != true || string.IsNullOrWhiteSpace(local.Estado.Acesso))
                {
                    Shutdown();
                    return;
                }
            }

            var principal = new MainWindow(local);
            MainWindow = principal;
            principal.ShowDialog();

            if (!principal.SaiuDaConta)
            {
                Shutdown();
                return;
            }
        }
    }
}
