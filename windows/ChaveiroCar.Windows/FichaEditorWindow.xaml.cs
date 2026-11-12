using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using ChaveiroCar.Windows.Services;
using Microsoft.Win32;

namespace ChaveiroCar.Windows;

public partial class FichaEditorWindow : Window
{
    private readonly ArmazenamentoLocal.Ficha _original;
    private readonly List<ArmazenamentoLocal.FotoLocal> _fotosExistentes;
    private readonly List<string> _novasCopias = [];
    private readonly ObservableCollection<ArmazenamentoLocal.FotoLocal> _fotosNovas = [];

    public ArmazenamentoLocal.Ficha? Resultado { get; private set; }
    public IReadOnlyList<ArmazenamentoLocal.FotoLocal> ResultadoFotos { get; private set; } = [];

    public FichaEditorWindow(
        ArmazenamentoLocal.Ficha original,
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
        Equipamento.Text = _original.Equipamento;
        Marca.Text = _original.Marca;
        Modelo.Text = _original.Modelo;
        Ano.Text = _original.Ano;
        Sistema.Text = _original.Sistema;
        Origem.Text = _original.Origem;
        Confirmado.IsChecked = _original.Confirmado;
        Chip.Text = _original.Chip;
        Chave.Text = _original.Chave;
        Telecomando.Text = _original.Telecomando;
        Frequencia.Text = _original.Frequencia;
        Descricao.Text = _original.Descricao;
        Funcoes.Text = _original.Funcoes;
        Avisos.Text = _original.Avisos;
        Manual.Text = _original.Manual;
    }

    private void AdicionarFotos_Click(object sender, RoutedEventArgs e)
    {
        var seletor = new OpenFileDialog
        {
            Title = "Selecionar fotos da ficha",
            Filter = "Imagens JPEG, PNG ou WebP|*.jpg;*.jpeg;*.png;*.webp",
            Multiselect = true,
        };
        if (seletor.ShowDialog(this) != true) return;
        try
        {
            var categoria = (CategoriaFoto.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "PROCEDIMENTO";
            var novas = FotosLocais.Copiar(
                seletor.FileNames,
                _original.Uuid,
                true,
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
        if (string.IsNullOrWhiteSpace(Equipamento.Text) &&
            string.IsNullOrWhiteSpace(Marca.Text) && string.IsNullOrWhiteSpace(Modelo.Text))
        {
            Erro.Text = "Informe equipamento, marca ou modelo.";
            return;
        }

        Resultado = new ArmazenamentoLocal.Ficha
        {
            Uuid = _original.Uuid,
            Equipamento = Equipamento.Text.Trim(),
            Marca = Marca.Text.Trim(),
            Modelo = Modelo.Text.Trim(),
            Ano = Ano.Text.Trim(),
            Sistema = Sistema.Text.Trim(),
            Origem = Origem.Text.Trim(),
            Confirmado = Confirmado.IsChecked == true,
            Chip = Chip.Text.Trim(),
            Chave = Chave.Text.Trim(),
            Telecomando = Telecomando.Text.Trim(),
            Frequencia = Frequencia.Text.Trim(),
            Descricao = Descricao.Text.Trim(),
            Funcoes = Funcoes.Text.Trim(),
            Avisos = Avisos.Text.Trim(),
            Manual = Manual.Text.Trim(),
            Revisao = _original.Revisao,
            ExcluidoEm = _original.ExcluidoEm,
        };
        ResultadoFotos = _fotosExistentes.Concat(_fotosNovas).ToList();
        DialogResult = true;
    }

    private static ArmazenamentoLocal.Ficha Copiar(ArmazenamentoLocal.Ficha ficha) => new()
    {
        Uuid = ficha.Uuid,
        Equipamento = ficha.Equipamento,
        Marca = ficha.Marca,
        Modelo = ficha.Modelo,
        Ano = ficha.Ano,
        Sistema = ficha.Sistema,
        Descricao = ficha.Descricao,
        Chip = ficha.Chip,
        Chave = ficha.Chave,
        Telecomando = ficha.Telecomando,
        Frequencia = ficha.Frequencia,
        Avisos = ficha.Avisos,
        Funcoes = ficha.Funcoes,
        Manual = ficha.Manual,
        Confirmado = ficha.Confirmado,
        Origem = ficha.Origem,
        Revisao = ficha.Revisao,
        ExcluidoEm = ficha.ExcluidoEm,
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
