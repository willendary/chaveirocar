using System.Windows;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class RecuperacaoSenhaWindow : Window
{
    private readonly string _baseUrl;

    public RecuperacaoSenhaWindow(string baseUrl, string email)
    {
        _baseUrl = baseUrl;
        InitializeComponent();
        Email.Text = email;
    }

    private async void Solicitar_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        Solicitar.IsEnabled = false;
        try
        {
            using var api = new ClienteApi(_baseUrl);
            await api.SolicitarRedefinicaoAsync(Email.Text.Trim());
            Erro.Text = "Se o e-mail estiver cadastrado, enviaremos as instruções. Verifique também o spam.";
        }
        catch (Exception ex)
        {
            Erro.Text = $"Não foi possível solicitar a recuperação: {ex.Message}";
        }
        finally
        {
            Solicitar.IsEnabled = true;
        }
    }

    private async void Alterar_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        Alterar.IsEnabled = false;
        try
        {
            using var api = new ClienteApi(_baseUrl);
            await api.RedefinirSenhaAsync(Token.Text.Trim(), NovaSenha.Password);
            MessageBox.Show("Senha alterada. Entre novamente com a nova senha.",
                "ChaveiroCar", MessageBoxButton.OK, MessageBoxImage.Information);
            DialogResult = true;
            Close();
        }
        catch
        {
            Erro.Text = "Código inválido ou expirado, ou senha não aceita.";
        }
        finally
        {
            Alterar.IsEnabled = true;
        }
    }
}
