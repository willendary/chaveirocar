using System.Windows;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class ConviteWindow : Window
{
    private readonly ArmazenamentoLocal _local;
    private ClienteApi? _api;
    private ClienteApi.ConvitePreview? _convite;

    public ConviteWindow(ArmazenamentoLocal local)
    {
        _local = local;
        InitializeComponent();
        _api = new ClienteApi(_local.Estado.BaseUrl);
        DadosContaNova.Visibility = Visibility.Visible;
    }

    private async void Verificar_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        Verificar.IsEnabled = false;
        try
        {
            _convite = await _api!.PrevisualizarConviteAsync(Token.Text.Trim());
            ConviteInfo.Text = $"Empresa: {_convite.EmpresaNome}\nE-mail convidado: {_convite.Email}\n" +
                $"Papel: {_convite.Papel}\nExpira: {DateTimeOffset.FromUnixTimeMilliseconds(_convite.ExpiraEm).ToLocalTime():g}";
            Email.Text = _convite.Email;
            Modos.Visibility = Visibility.Visible;
            Aceitar.IsEnabled = true;
            Modo_Checked(this, new RoutedEventArgs());
        }
        catch
        {
            Erro.Text = "Convite inválido, cancelado ou expirado. Confira o código do link.";
        }
        finally
        {
            Verificar.IsEnabled = true;
        }
    }

    private void Modo_Checked(object sender, RoutedEventArgs e)
    {
        if (DadosContaNova is null || DadosContaExistente is null) return;
        var nova = CriarConta.IsChecked == true;
        DadosContaNova.Visibility = nova ? Visibility.Visible : Visibility.Collapsed;
        DadosContaExistente.Visibility = nova ? Visibility.Collapsed : Visibility.Visible;
    }

    private async void Aceitar_Click(object sender, RoutedEventArgs e)
    {
        if (_convite is null || _api is null) return;
        Erro.Text = "";
        Aceitar.IsEnabled = false;
        try
        {
            string empresaId;
            if (UsarConta.IsChecked == true)
            {
                var login = await _api.EntrarAsync(Email.Text.Trim(), SenhaExistente.Password);
                if (!login.Ok || !string.Equals(login.Email, _convite.Email, StringComparison.OrdinalIgnoreCase))
                {
                    Erro.Text = "Entre com a conta que recebeu o convite.";
                    return;
                }
                empresaId = await _api.AceitarConviteAsync(Token.Text.Trim());
            }
            else
            {
                empresaId = await _api.AceitarConviteAsync(Token.Text.Trim(), Nome.Text.Trim(), SenhaNova.Password);
            }

            _local.Estado.Acesso = _api.Acesso;
            _local.Estado.Refresh = _api.Refresh;
            _local.Estado.BaseUrl = _local.Estado.BaseUrl.TrimEnd('/');
            _local.Estado.Email = _convite.Email;
            _local.Estado.EmpresaId = empresaId;
            _local.Salvar();
            DialogResult = true;
            Close();
        }
        catch (Exception ex)
        {
            Erro.Text = $"Não foi possível aceitar o convite: {ex.Message}";
        }
        finally
        {
            Aceitar.IsEnabled = true;
        }
    }
}
