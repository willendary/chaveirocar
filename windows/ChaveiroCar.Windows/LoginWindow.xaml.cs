using System.Windows;
using System.Windows.Controls;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class LoginWindow : Window
{
    private readonly ArmazenamentoLocal _local;

    public LoginWindow(ArmazenamentoLocal local)
    {
        _local = local;
        InitializeComponent();
        BaseUrl.Text = _local.Estado.BaseUrl;
        Email.Text = _local.Estado.Email;
        Email.Focus();
    }

    private async void Entrar_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        var botao = (Button)sender;
        botao.IsEnabled = false;
        try
        {
            using var api = new ClienteApi(BaseUrl.Text.Trim());
            var resultado = await api.EntrarAsync(Email.Text.Trim(), Senha.Password);
            if (!resultado.Ok)
            {
                Erro.Text = "E-mail ou senha inválidos, ou API inalcançável.";
                return;
            }
            _local.Estado.BaseUrl = BaseUrl.Text.Trim();
            _local.Estado.Acesso = api.Acesso;
            _local.Estado.Refresh = api.Refresh;
            _local.Estado.Email = Email.Text.Trim();
            if (resultado.Empresas.Count > 0) _local.Estado.EmpresaId = resultado.Empresas[0].Id;
            _local.Salvar();
            DialogResult = true;
            Close();
        }
        catch (Exception ex)
        {
            Erro.Text = $"Falha de conexão: {ex.Message}";
        }
        finally
        {
            botao.IsEnabled = true;
        }
    }
}
