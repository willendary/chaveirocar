using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using ChaveiroCar.Windows.Services;
using Microsoft.Win32;

namespace ChaveiroCar.Windows;

public partial class AtendimentoEditorWindow : Window
{
    private readonly Atendimento _original;
    private readonly List<ArmazenamentoLocal.FotoLocal> _fotosExistentes;
    private readonly List<string> _novasCopias = [];
    private readonly ObservableCollection<ArmazenamentoLocal.FotoLocal> _fotosNovas = [];

    public Atendimento? Resultado { get; private set; }
    public IReadOnlyList<ArmazenamentoLocal.FotoLocal> ResultadoFotos { get; private set; } = [];

    public AtendimentoEditorWindow(
        Atendimento original,
        IEnumerable<ArmazenamentoLocal.FotoLocal> fotos)
    {
        _original = Copiar(original);
        _fotosExistentes = fotos.Select(Copiar).ToList();
        InitializeComponent();
        FotosExistentes.ItemsSource = _fotosExistentes;
        FotosNovas.ItemsSource = _fotosNovas;
        Preencher();
        Closed += (_, _) =>
        {
            if (DialogResult == true) return;
            foreach (var caminho in _novasCopias) File.Delete(caminho);
        };
    }

    private void Preencher()
    {
        Placa.Text = _original.Placa;
        Marca.Text = _original.Marca;
        Modelo.Text = _original.Modelo;
        Ano.Text = _original.Ano;
        Chassi.Text = _original.Chassi;
        ClienteNome.Text = _original.ClienteNome;
        ClienteTelefone.Text = _original.ClienteTelefone;
        DataServico.SelectedDate = DateTimeOffset.FromUnixTimeMilliseconds(_original.DataServico).LocalDateTime.Date;
        Defeito.Text = _original.Defeito;
        Solucao.Text = _original.Solucao;
        Abertura.Text = _original.Abertura;
        CodigoChave.Text = _original.CodigoChave;
        CodigoConfiguracao.Text = _original.CodigoConfiguracao;
        ChipTransponder.Text = _original.ChipTransponder;
        Ferramenta.Text = _original.Ferramenta;
        Valor.Text = (_original.ValorCentavos / 100m).ToString("N2", CultureInfo.CurrentCulture);
        Pago.IsChecked = _original.Pago;
        foreach (var item in TiposServico.Items.OfType<ListBoxItem>())
        {
            item.IsSelected = _original.TiposServico.Contains(item.Tag?.ToString() ?? "");
        }
    }

    private void AdicionarFotos_Click(object sender, RoutedEventArgs e)
    {
        var seletor = new OpenFileDialog
        {
            Title = "Selecionar fotos",
            Filter = "Imagens JPEG, PNG ou WebP|*.jpg;*.jpeg;*.png;*.webp",
            Multiselect = true,
        };
        if (seletor.ShowDialog(this) != true) return;
        try
        {
            var categoria = (CategoriaFoto.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "CARRO";
            var novas = FotosLocais.Copiar(
                seletor.FileNames,
                _original.Uuid,
                ficha: false,
                _fotosExistentes.Count + _fotosNovas.Count,
                categoria,
                LegendaFoto.Text);
            foreach (var foto in novas)
            {
                _novasCopias.Add(foto.CaminhoLocal);
                _fotosNovas.Add(foto);
            }
            Erro.Text = "";
        }
        catch (Exception ex)
        {
            Erro.Text = ex.Message;
        }
    }

    private void RemoverFoto_Click(object sender, RoutedEventArgs e)
    {
        if (FotosNovas.SelectedItem is not ArmazenamentoLocal.FotoLocal foto) return;
        _fotosNovas.Remove(foto);
        _novasCopias.Remove(foto.CaminhoLocal);
        File.Delete(foto.CaminhoLocal);
    }

    private void AbrirFoto_Click(object sender, RoutedEventArgs e)
    {
        if (FotosExistentes.SelectedItem is not ArmazenamentoLocal.FotoLocal foto) return;
        if (!File.Exists(foto.CaminhoLocal))
        {
            Erro.Text = "Foto indisponível localmente. Sincronize para baixá-la.";
            return;
        }
        Process.Start(new ProcessStartInfo(foto.CaminhoLocal) { UseShellExecute = true });
    }

    private void Salvar_Click(object sender, RoutedEventArgs e)
    {
        var placa = Placa.Text.Trim().ToUpperInvariant();
        if (placa.Length == 0)
        {
            Erro.Text = "Informe a placa.";
            Placa.Focus();
            return;
        }
        var tipos = TiposServico.SelectedItems.OfType<ListBoxItem>()
            .Select(item => item.Tag?.ToString() ?? "")
            .Where(tipo => tipo.Length > 0)
            .ToList();
        if (tipos.Count == 0)
        {
            Erro.Text = "Selecione ao menos um tipo de serviço.";
            return;
        }
        if (!decimal.TryParse(Valor.Text, NumberStyles.Number, CultureInfo.CurrentCulture, out var valor) || valor < 0)
        {
            Erro.Text = "Informe um valor válido, ou 0.";
            Valor.Focus();
            return;
        }

        var data = DataServico.SelectedDate ?? DateTime.Today;
        Resultado = new Atendimento
        {
            Uuid = _original.Uuid,
            Placa = placa,
            Marca = Marca.Text.Trim(),
            Modelo = Modelo.Text.Trim(),
            Ano = Ano.Text.Trim(),
            Chassi = Chassi.Text.Trim(),
            DataServico = new DateTimeOffset(data).ToUnixTimeMilliseconds(),
            TiposServico = tipos,
            Defeito = Defeito.Text.Trim(),
            Solucao = Solucao.Text.Trim(),
            Abertura = Abertura.Text.Trim(),
            CodigoChave = CodigoChave.Text.Trim(),
            CodigoConfiguracao = CodigoConfiguracao.Text.Trim(),
            ChipTransponder = ChipTransponder.Text.Trim(),
            Ferramenta = Ferramenta.Text.Trim(),
            ValorCentavos = decimal.ToInt64(decimal.Round(valor * 100m, 0, MidpointRounding.AwayFromZero)),
            ClienteNome = ClienteNome.Text.Trim(),
            ClienteTelefone = ClienteTelefone.Text.Trim(),
            Pago = Pago.IsChecked == true,
            Revisao = _original.Revisao,
            ExcluidoEm = _original.ExcluidoEm,
        };
        ResultadoFotos = _fotosExistentes.Concat(_fotosNovas).ToList();
        DialogResult = true;
    }

    private static Atendimento Copiar(Atendimento item) => new()
    {
        Uuid = item.Uuid,
        Placa = item.Placa,
        Marca = item.Marca,
        Modelo = item.Modelo,
        Ano = item.Ano,
        Chassi = item.Chassi,
        DataServico = item.DataServico,
        TiposServico = [.. item.TiposServico],
        Defeito = item.Defeito,
        Solucao = item.Solucao,
        Abertura = item.Abertura,
        CodigoChave = item.CodigoChave,
        CodigoConfiguracao = item.CodigoConfiguracao,
        ChipTransponder = item.ChipTransponder,
        Ferramenta = item.Ferramenta,
        ValorCentavos = item.ValorCentavos,
        ClienteNome = item.ClienteNome,
        ClienteTelefone = item.ClienteTelefone,
        Pago = item.Pago,
        Revisao = item.Revisao,
        ExcluidoEm = item.ExcluidoEm,
    };

    private static ArmazenamentoLocal.FotoLocal Copiar(ArmazenamentoLocal.FotoLocal foto) => new()
    {
        Uuid = foto.Uuid,
        AtendimentoUuid = foto.AtendimentoUuid,
        FichaUuid = foto.FichaUuid,
        Categoria = foto.Categoria,
        Legenda = foto.Legenda,
        Ordem = foto.Ordem,
        Revisao = foto.Revisao,
        CaminhoLocal = foto.CaminhoLocal,
        Pendente = foto.Pendente,
    };
}
