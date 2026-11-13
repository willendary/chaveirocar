using System.Windows;
using System.Windows.Input;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class LoginWindow : Window
{
    private readonly ArmazenamentoLocal _local;
    private CancellationTokenSource? _cancelamentoGoogle;

    public LoginWindow(ArmazenamentoLocal local)
    {
        _local = local;
        InitializeComponent();
        if (string.IsNullOrWhiteSpace(_local.Estado.BaseUrl))
        {
            _local.Estado.BaseUrl = App.UrlApiPadrao;
        }
        Email.Text = _local.Estado.Email;
        Email.Focus();
    }

    private string ObterUrlApi() =>
        string.IsNullOrWhiteSpace(_local.Estado.BaseUrl) ? App.UrlApiPadrao : _local.Estado.BaseUrl.Trim();

    private void Senha_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            Entrar_Click(BotaoEntrar, new RoutedEventArgs());
        }
    }

    private async void Entrar_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        var email = Email.Text.Trim();
        var senha = Senha.Password;
        if (string.IsNullOrWhiteSpace(email) || string.IsNullOrWhiteSpace(senha))
        {
            Erro.Text = "Informe seu e-mail e sua senha para entrar.";
            return;
        }

        BotaoEntrar.IsEnabled = false;
        BotaoGoogle.IsEnabled = false;
        BotaoEntrar.Content = "Autenticando...";
        try
        {
            using var api = new ClienteApi(ObterUrlApi());
            var resultado = await api.EntrarAsync(email, senha);
            if (!resultado.Ok)
            {
                Erro.Text = "E-mail ou senha incorretos.";
                return;
            }
            _local.Estado.Acesso = api.Acesso;
            _local.Estado.Refresh = api.Refresh;
            _local.Estado.Email = email;
            if (resultado.Empresas.Count > 0) _local.Estado.EmpresaId = resultado.Empresas[0].Id;
            _local.Salvar();
            DialogResult = true;
            Close();
        }
        catch (Exception ex)
        {
            Erro.Text = $"Não foi possível conectar ao servidor: {ex.Message}";
        }
        finally
        {
            BotaoEntrar.IsEnabled = true;
            BotaoGoogle.IsEnabled = true;
            BotaoEntrar.Content = "Entrar na conta";
        }
    }

    private async void EntrarGoogle_Click(object sender, RoutedEventArgs e)
    {
        if (_cancelamentoGoogle is not null)
        {
            _cancelamentoGoogle.Cancel();
            return;
        }

        Erro.Text = "";
        _cancelamentoGoogle = new CancellationTokenSource();
        BotaoEntrar.IsEnabled = false;
        TextoBotaoGoogle.Text = "Aguardando login no navegador... (Cancelar)";
        try
        {
            var retorno = await AutenticadorGoogleDesktop.AutenticarNoNavegadorAsync(
                ObterUrlApi(),
                _cancelamentoGoogle.Token);

            if (!retorno.Ok)
            {
                if (!string.IsNullOrWhiteSpace(retorno.Erro))
                {
                    Erro.Text = retorno.Erro;
                }
                return;
            }

            if (!string.IsNullOrWhiteSpace(retorno.Acesso) && !string.IsNullOrWhiteSpace(retorno.Refresh))
            {
                _local.Estado.Acesso = retorno.Acesso;
                _local.Estado.Refresh = retorno.Refresh;
                _local.Estado.Email = retorno.Email ?? _local.Estado.Email;
                if (!string.IsNullOrWhiteSpace(retorno.EmpresaId))
                {
                    _local.Estado.EmpresaId = retorno.EmpresaId;
                }
                _local.Salvar();
                Activate();
                DialogResult = true;
                Close();
                return;
            }

            if (!string.IsNullOrWhiteSpace(retorno.IdToken))
            {
                using var api = new ClienteApi(ObterUrlApi());
                var sessao = await api.EntrarComGoogleAsync(retorno.IdToken);
                if (!sessao.Ok)
                {
                    Erro.Text = "Não foi possível validar sua conta Google.";
                    return;
                }
                _local.Estado.Acesso = api.Acesso;
                _local.Estado.Refresh = api.Refresh;
                _local.Estado.Email = sessao.Email ?? _local.Estado.Email;
                if (sessao.Empresas.Count > 0) _local.Estado.EmpresaId = sessao.Empresas[0].Id;
                _local.Salvar();
                Activate();
                DialogResult = true;
                Close();
            }
        }
        catch (Exception ex)
        {
            Erro.Text = $"Falha ao iniciar login com Google: {ex.Message}";
        }
        finally
        {
            _cancelamentoGoogle?.Dispose();
            _cancelamentoGoogle = null;
            BotaoEntrar.IsEnabled = true;
            TextoBotaoGoogle.Text = "Continuar com Google";
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
        new RecuperacaoSenhaWindow(ObterUrlApi(), Email.Text.Trim()) { Owner = this }.ShowDialog();
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
