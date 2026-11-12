using System.Windows;
using System.Windows.Input;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class MainWindow : Window
{
    private readonly ClienteApi _api = new();
    private string _empresaId = "";

    public MainWindow()
    {
        InitializeComponent();
    }

    private async void Pesquisar_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_empresaId))
        {
            MessageBox.Show("Entre na sua conta primeiro (login ainda não vinculado).");
            return;
        }
        Grade.ItemsSource = await _api.ListarAtendimentosAsync(_empresaId, Busca.Text);
    }

    private async void Sincronizar_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_empresaId))
        {
            MessageBox.Show("Informe a empresa e entre na conta.");
            return;
        }
        var lote = await _api.SincronizarAsync(_empresaId, cursor: 0);
        MessageBox.Show($"Cursor {lote.Cursor}: {lote.TemMais}");
    }

    private void Busca_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) Pesquisar_Click(sender, e);
    }
}
