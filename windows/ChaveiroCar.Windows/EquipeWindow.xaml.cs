using System.Windows;
using System.Windows.Controls;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class EquipeWindow : Window
{
    private readonly ClienteApi _api;
    private readonly string _empresaId;
    private readonly bool _podeGerenciar;

    public EquipeWindow(ClienteApi api, string empresaId, bool podeGerenciar)
    {
        _api = api;
        _empresaId = empresaId;
        _podeGerenciar = podeGerenciar;
        InitializeComponent();
        PainelConvite.Visibility = _podeGerenciar ? Visibility.Visible : Visibility.Collapsed;
        AcoesMembro.Visibility = _podeGerenciar ? Visibility.Visible : Visibility.Collapsed;
        AbaConvites.Visibility = _podeGerenciar ? Visibility.Visible : Visibility.Collapsed;
    }

    private async void Window_Loaded(object sender, RoutedEventArgs e) => await Atualizar();

    private async Task Atualizar()
    {
        try
        {
            Membros.ItemsSource = await _api.MembrosAsync(_empresaId);
            if (_podeGerenciar) Convites.ItemsSource = await _api.ConvitesAsync(_empresaId);
            Status.Text = "Membros e convites atualizados.";
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível atualizar a equipe: {ex.Message}";
        }
    }

    private async void Convidar_Click(object sender, RoutedEventArgs e)
    {
        var email = EmailConvite.Text.Trim();
        if (email.Length == 0)
        {
            Status.Text = "Informe o e-mail do colaborador.";
            return;
        }
        Convidar.IsEnabled = false;
        try
        {
            var papel = (PapelNovo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "colaborador";
            var enviado = await _api.ConvidarAsync(_empresaId, email, papel);
            Status.Text = enviado
                ? "Convite enviado por e-mail."
                : "Convite criado, mas o e-mail não foi enviado. Confira a configuração do serviço de e-mail.";
            if (enviado) EmailConvite.Clear();
            await Atualizar();
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível criar o convite: {ex.Message}";
        }
        finally
        {
            Convidar.IsEnabled = true;
        }
    }

    private async void AlterarPapel_Click(object sender, RoutedEventArgs e)
    {
        if (Membros.SelectedItem is not ClienteApi.Membro membro) return;
        var papel = (PapelMembro.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "colaborador";
        try
        {
            await _api.AlterarPapelAsync(_empresaId, membro.UsuarioId, papel);
            await Atualizar();
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível alterar a permissão: {ex.Message}";
        }
    }

    private async void RemoverMembro_Click(object sender, RoutedEventArgs e)
    {
        if (Membros.SelectedItem is not ClienteApi.Membro membro) return;
        if (MessageBox.Show($"Remover acesso de {membro.Email}?", "Equipe ChaveiroCar",
                MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        try
        {
            await _api.RemoverMembroAsync(_empresaId, membro.UsuarioId);
            await Atualizar();
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível remover o membro: {ex.Message}";
        }
    }

    private async void CancelarConvite_Click(object sender, RoutedEventArgs e)
    {
        if (Convites.SelectedItem is not ClienteApi.Convite convite) return;
        if (convite.Aceito || convite.Cancelado || convite.Expirado)
        {
            Status.Text = "Só é possível cancelar convites pendentes.";
            return;
        }
        try
        {
            await _api.CancelarConviteAsync(_empresaId, convite.Id);
            await Atualizar();
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível cancelar o convite: {ex.Message}";
        }
    }
}
