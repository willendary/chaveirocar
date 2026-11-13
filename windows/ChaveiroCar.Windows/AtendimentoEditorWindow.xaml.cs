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
    private static readonly (string Rotulo, string Tag)[] TiposAutomotivos =
    [
        ("Chave codificada", "CHAVE_CODIFICADA"),
        ("Transponder", "TRANSPONDER"),
        ("Telecomando", "TELECOMANDO"),
        ("Abertura", "ABERTURA"),
        ("Ignição", "IGNICAO"),
        ("Conserto", "CONSERTO"),
        ("Programação de chave", "PROGRAMACAO_CHAVE"),
        ("Outro", "OUTRO"),
    ];

    private static readonly (string Rotulo, string Tag)[] TiposResidenciais =
    [
        ("Abertura residencial", "ABERTURA_RESIDENCIAL"),
        ("Cópia de chave", "COPIA_CHAVE"),
        ("Troca de segredo", "TROCA_SEGREDO"),
        ("Instalação de fechadura", "INSTALACAO_FECHADURA"),
        ("Cilindro / fechadura", "TROCA_CILINDRO"),
        ("Fechadura digital", "FECHADURA_DIGITAL"),
        ("Reparo de fechadura", "REPARO_FECHADURA"),
        ("Outro", "OUTRO"),
    ];

    private readonly Atendimento _original;
    private readonly List<ArmazenamentoLocal.FotoLocal> _fotosExistentes;
    private readonly List<string> _novasCopias = [];
    private readonly ObservableCollection<ArmazenamentoLocal.FotoLocal> _fotosNovas = [];
    private bool _carregando;

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
            foreach (var caminho in _novasCopias)
            {
                File.Delete(caminho);
                var orig = FotosLocais.CaminhoOriginal(caminho);
                if (File.Exists(orig)) File.Delete(orig);
            }
        };
    }

    private string SegmentoAtual() =>
        (Segmento.SelectedItem as ComboBoxItem)?.Tag?.ToString() == "RESIDENCIAL"
            ? "RESIDENCIAL"
            : "AUTOMOTIVO";

    private void Preencher()
    {
        _carregando = true;
        Segmento.SelectedIndex = _original.Segmento == "RESIDENCIAL" ? 1 : 0;
        Placa.Text = _original.Placa;
        Marca.Text = _original.Marca;
        Modelo.Text = _original.Modelo;
        Ano.Text = _original.Ano;
        Chassi.Text = _original.Chassi;
        TipoLocal.Text = _original.TipoLocal;
        AmbientePorta.Text = _original.AmbientePorta;
        MarcaFechadura.Text = _original.MarcaFechadura;
        ModeloFechadura.Text = _original.ModeloFechadura;
        TipoCilindro.Text = _original.TipoCilindro;
        EnderecoLocal.Text = _original.EnderecoLocal;
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
        AtualizarCamposPorSegmento(_original.TiposServico);
        _carregando = false;
    }

    private void Segmento_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_carregando || PainelAutomotivo is null || PainelResidencial is null || TiposServico is null) return;
        var selecionados = TiposServico.SelectedItems.OfType<ListBoxItem>()
            .Select(item => item.Tag?.ToString() ?? "")
            .Where(s => s.Length > 0)
            .ToList();
        AtualizarCamposPorSegmento(selecionados);
    }

    private void AtualizarCamposPorSegmento(IReadOnlyCollection<string> selecionadosAtuais)
    {
        var residencial = SegmentoAtual() == "RESIDENCIAL";
        PainelAutomotivo.Visibility = residencial ? Visibility.Collapsed : Visibility.Visible;
        PainelResidencial.Visibility = residencial ? Visibility.Visible : Visibility.Collapsed;
        PainelChipTransponder.Visibility = residencial ? Visibility.Collapsed : Visibility.Visible;

        var opcoes = residencial ? TiposResidenciais : TiposAutomotivos;
        var padrao = residencial ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA";
        TiposServico.Items.Clear();
        var marcouAlgum = false;
        foreach (var (rotulo, tag) in opcoes)
        {
            var item = new ListBoxItem
            {
                Content = rotulo,
                Tag = tag,
                IsSelected = selecionadosAtuais.Contains(tag),
            };
            if (item.IsSelected) marcouAlgum = true;
            TiposServico.Items.Add(item);
        }
        if (!marcouAlgum && TiposServico.Items.Count > 0)
        {
            foreach (var item in TiposServico.Items.OfType<ListBoxItem>())
            {
                if (item.Tag?.ToString() == padrao)
                {
                    item.IsSelected = true;
                    break;
                }
            }
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
        var orig = FotosLocais.CaminhoOriginal(foto.CaminhoLocal);
        if (File.Exists(orig)) File.Delete(orig);
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

    private void AnotarFotoExistente_Click(object sender, RoutedEventArgs e)
    {
        if (FotosExistentes.SelectedItem is not ArmazenamentoLocal.FotoLocal foto)
        {
            Erro.Text = "Selecione uma foto sincronizada para anotar.";
            return;
        }
        AnotarFoto(foto);
    }

    private void AnotarFotoNova_Click(object sender, RoutedEventArgs e)
    {
        if (FotosNovas.SelectedItem is not ArmazenamentoLocal.FotoLocal foto)
        {
            Erro.Text = "Selecione uma foto nova para anotar.";
            return;
        }
        AnotarFoto(foto);
    }

    private void AnotarFoto(ArmazenamentoLocal.FotoLocal foto)
    {
        if (!File.Exists(foto.CaminhoLocal))
        {
            Erro.Text = "Foto indisponível localmente.";
            return;
        }
        var editor = new FotoAnotadorWindow(foto.CaminhoLocal, foto.Anotacoes) { Owner = this };
        if (editor.ShowDialog() == true)
        {
            foto.Anotacoes = editor.ResultadoAnotacoes;
            foto.Pendente = true;
            Erro.Text = "";
        }
    }

    private void Salvar_Click(object sender, RoutedEventArgs e)
    {
        var segmento = SegmentoAtual();
        var placa = Placa.Text.Trim().ToUpperInvariant();
        var modelo = Modelo.Text.Trim();
        var tipoLocal = TipoLocal.Text.Trim();
        var ambientePorta = AmbientePorta.Text.Trim();
        var marcaFechadura = MarcaFechadura.Text.Trim();
        var modeloFechadura = ModeloFechadura.Text.Trim();

        if (segmento == "AUTOMOTIVO")
        {
            if (modelo.Length == 0)
            {
                Erro.Text = "Informe o modelo do veículo (a placa é opcional).";
                Modelo.Focus();
                return;
            }
        }
        else
        {
            if (tipoLocal.Length == 0 && ambientePorta.Length == 0 && marcaFechadura.Length == 0 && modeloFechadura.Length == 0)
            {
                Erro.Text = "Informe ao menos o tipo de local ou fechadura.";
                TipoLocal.Focus();
                return;
            }
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
            Segmento = segmento,
            Placa = placa,
            Marca = Marca.Text.Trim(),
            Modelo = modelo,
            Ano = Ano.Text.Trim(),
            Chassi = Chassi.Text.Trim(),
            TipoLocal = tipoLocal,
            AmbientePorta = ambientePorta,
            MarcaFechadura = marcaFechadura,
            ModeloFechadura = modeloFechadura,
            TipoCilindro = TipoCilindro.Text.Trim(),
            EnderecoLocal = EnderecoLocal.Text.Trim(),
            DataServico = new DateTimeOffset(data).ToUnixTimeMilliseconds(),
            TiposServico = tipos,
            Defeito = Defeito.Text.Trim(),
            Solucao = Solucao.Text.Trim(),
            Abertura = Abertura.Text.Trim(),
            CodigoChave = CodigoChave.Text.Trim(),
            CodigoConfiguracao = CodigoConfiguracao.Text.Trim(),
            ChipTransponder = segmento == "RESIDENCIAL" ? "" : ChipTransponder.Text.Trim(),
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
        Segmento = item.Segmento,
        Placa = item.Placa,
        Marca = item.Marca,
        Modelo = item.Modelo,
        Ano = item.Ano,
        Chassi = item.Chassi,
        TipoLocal = item.TipoLocal,
        AmbientePorta = item.AmbientePorta,
        MarcaFechadura = item.MarcaFechadura,
        ModeloFechadura = item.ModeloFechadura,
        TipoCilindro = item.TipoCilindro,
        EnderecoLocal = item.EnderecoLocal,
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
        Anotacoes = foto.Anotacoes,
        Ordem = foto.Ordem,
        Revisao = foto.Revisao,
        CaminhoLocal = foto.CaminhoLocal,
        Pendente = foto.Pendente,
    };
}
