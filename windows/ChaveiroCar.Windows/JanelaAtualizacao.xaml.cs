using System.IO;
using System.Windows;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class JanelaAtualizacao : Window
{
    private readonly AtualizacaoDisponivel _atualizacao;
    private CancellationTokenSource? _cts;
    private string? _caminhoBaixado;

    public JanelaAtualizacao(AtualizacaoDisponivel atualizacao)
    {
        InitializeComponent();
        CarregarIconeSeguro();
        _atualizacao = atualizacao;
        TxtSubtitulo.Text = $"Versão v{atualizacao.Versao} disponível";
        TxtDescricao.Text = $"Uma nova versão do ChaveiroCar (v{atualizacao.Versao}) está disponível no servidor. Deseja baixar os arquivos e atualizar sua instalação agora?";
        TxtInfoArquivo.Text = $"Pacote: {atualizacao.NomeArquivo}";
        if (atualizacao.TamanhoBytes > 0)
        {
            var mb = atualizacao.TamanhoBytes / (1024.0 * 1024.0);
            TxtInfoTamanho.Text = $"Tamanho aproximado: {mb:F1} MB";
        }
        else
        {
            TxtInfoTamanho.Text = "Tamanho aproximado: ~60 MB";
        }
    }

    private void CarregarIconeSeguro()
    {
        try
        {
            var uri = new Uri("pack://application:,,,/app.ico", UriKind.Absolute);
            Icon = System.Windows.Media.Imaging.BitmapFrame.Create(uri);
        }
        catch { }
    }

    private async void Baixar_Click(object sender, RoutedEventArgs e)
    {
        PainelInicio.Visibility = Visibility.Collapsed;
        BotoesInicio.Visibility = Visibility.Collapsed;

        PainelProgresso.Visibility = Visibility.Visible;
        BotoesProgresso.Visibility = Visibility.Visible;

        _cts = new CancellationTokenSource();

        var progresso = new Progress<(long Baixados, long? Total)>(info =>
        {
            if (info.Total.HasValue && info.Total.Value > 0)
            {
                var percentual = (double)info.Baixados / info.Total.Value * 100.0;
                BarraProgresso.Value = percentual;
                TxtProgressoPercentual.Text = $"{percentual:F0}%";
                var baixadosMb = info.Baixados / (1024.0 * 1024.0);
                var totalMb = info.Total.Value / (1024.0 * 1024.0);
                TxtProgressoDetalhe.Text = $"{baixadosMb:F1} MB de {totalMb:F1} MB";
            }
            else
            {
                BarraProgresso.IsIndeterminate = true;
                var baixadosMb = info.Baixados / (1024.0 * 1024.0);
                TxtProgressoPercentual.Text = "Baixando...";
                TxtProgressoDetalhe.Text = $"{baixadosMb:F1} MB baixados";
            }
        });

        try
        {
            _caminhoBaixado = await VerificadorAtualizacao.BaixarAtualizacaoAsync(_atualizacao, progresso, _cts.Token);

            PainelProgresso.Visibility = Visibility.Collapsed;
            BotoesProgresso.Visibility = Visibility.Collapsed;

            PainelConcluido.Visibility = Visibility.Visible;
            BotoesConcluido.Visibility = Visibility.Visible;
        }
        catch (OperationCanceledException)
        {
            PainelProgresso.Visibility = Visibility.Collapsed;
            BotoesProgresso.Visibility = Visibility.Collapsed;
            PainelInicio.Visibility = Visibility.Visible;
            BotoesInicio.Visibility = Visibility.Visible;
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Falha ao baixar atualização: {ex.Message}", "Erro no download", MessageBoxButton.OK, MessageBoxImage.Error);
            PainelProgresso.Visibility = Visibility.Collapsed;
            BotoesProgresso.Visibility = Visibility.Collapsed;
            PainelInicio.Visibility = Visibility.Visible;
            BotoesInicio.Visibility = Visibility.Visible;
        }
    }

    private void CancelarDownload_Click(object sender, RoutedEventArgs e)
    {
        _cts?.Cancel();
    }

    private void Cancelar_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void Fechar_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void InstalarAgora_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_caminhoBaixado) || !File.Exists(_caminhoBaixado))
        {
            MessageBox.Show("Arquivo de atualização não encontrado.", "Erro", MessageBoxButton.OK, MessageBoxImage.Error);
            return;
        }

        VerificadorAtualizacao.ExecutarAtualizacao(_caminhoBaixado, _atualizacao.EhInstalador);
    }
}
