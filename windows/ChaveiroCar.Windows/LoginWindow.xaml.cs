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

    private async void EntrarGoogle_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        var token = TokenGoogle.Text.Trim();
        if (string.IsNullOrWhiteSpace(token))
        {
            Erro.Text = "Informe o ID Token do Google no campo acima para autenticar via Google.";
            TokenGoogle.Focus();
            return;
        }
        var botao = (Button)sender;
        botao.IsEnabled = false;
        try
        {
            using var api = new ClienteApi(BaseUrl.Text.Trim());
            var resultado = await api.EntrarComGoogleAsync(token);
            if (!resultado.Ok)
            {
                Erro.Text = "Não foi possível validar o token Google na API.";
                return;
            }
            _local.Estado.BaseUrl = BaseUrl.Text.Trim();
            _local.Estado.Acesso = api.Acesso;
            _local.Estado.Refresh = api.Refresh;
            _local.Estado.Email = resultado.Email ?? Email.Text.Trim();
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

    private void CriarConta_Click(object sender, RoutedEventArgs e)
    {
        var cadastro = new CadastroWindow(_local) { Owner = this };
        if (cadastro.ShowDialog() == true)
        {
            DialogResult = true;
            Close();
        }
    }

    private void RecuperarSenha_Click(object sender, RoutedEventArgs e)
    {
        new RecuperacaoSenhaWindow(BaseUrl.Text.Trim(), Email.Text.Trim()) { Owner = this }.ShowDialog();
    }

    private void AceitarConvite_Click(object sender, RoutedEventArgs e)
    {
        var convite = new ConviteWindow(_local) { Owner = this };
        if (convite.ShowDialog() == true)
        {
            DialogResult = true;
            Close();
        }
    }
}
