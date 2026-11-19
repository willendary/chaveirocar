using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using ChaveiroCar.Windows.Services;
using Microsoft.Win32;

namespace ChaveiroCar.Windows;

public partial class AtendimentoEditorWindow : Window
{
    private static readonly Regex RegexPlacaValida = new(@"^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$", RegexOptions.Compiled);

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
    private readonly Action<Atendimento>? _aoVerFicha;
    private readonly ClienteApi? _api;
    private readonly string? _empresaId;
    private readonly IReadOnlyList<Atendimento> _historicoLocal;
    private readonly List<string> _novasCopias = [];
    private readonly ObservableCollection<ArmazenamentoLocal.FotoLocal> _fotosNovas = [];
    private readonly List<CheckBox> _checksTipos = [];
    private CancellationTokenSource? _ctsPlaca;
    private string _ultimaPlacaConsultada = "";
    private bool _carregando;

    public Atendimento? Resultado { get; private set; }
    public IReadOnlyList<ArmazenamentoLocal.FotoLocal> ResultadoFotos { get; private set; } = [];

    public AtendimentoEditorWindow(
        Atendimento original,
        IEnumerable<ArmazenamentoLocal.FotoLocal> fotos,
        bool segmentoFixo = false,
        Action<Atendimento>? aoVerFicha = null,
        ClienteApi? api = null,
        string? empresaId = null,
        IReadOnlyList<Atendimento>? historicoLocal = null)
    {
        _original = Copiar(original);
        _fotosExistentes = fotos.Select(Copiar).ToList();
        _aoVerFicha = aoVerFicha;
        _api = api;
        _empresaId = empresaId;
        _historicoLocal = historicoLocal ?? [];
        InitializeComponent();
        IconeApp.Aplicar(this);
        Segmento.IsEnabled = !segmentoFixo;
        BtnVerFicha.Visibility = _aoVerFicha is not null ? Visibility.Visible : Visibility.Collapsed;
        FotosExistentes.ItemsSource = _fotosExistentes;
        FotosNovas.ItemsSource = _fotosNovas;
        Preencher();
        Placa.TextChanged += Placa_TextChanged;
        ClienteTelefone.LostFocus += ClienteTelefone_LostFocus;
        Valor.LostFocus += Valor_LostFocus;
        Closed += (_, _) =>
        {
            _ctsPlaca?.Cancel();
            _ctsPlaca?.Dispose();
            if (DialogResult == true) return;
            foreach (var caminho in _novasCopias)
            {
                if (File.Exists(caminho)) File.Delete(caminho);
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
        _ultimaPlacaConsultada = NormalizarPlaca(_original.Placa);
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
        if (_carregando || PainelAutomotivo is null || PainelResidencial is null || PainelTiposServico is null) return;
        AtualizarCamposPorSegmento(ObterTiposSelecionados());
    }

    private List<string> ObterTiposSelecionados() =>
        _checksTipos
            .Where(c => c.IsChecked == true && c.Tag is string s && s.Length > 0)
            .Select(c => (string)c.Tag)
            .ToList();

    private void AtualizarCamposPorSegmento(IReadOnlyCollection<string> selecionadosAtuais)
    {
        var residencial = SegmentoAtual() == "RESIDENCIAL";
        PainelAutomotivo.Visibility = residencial ? Visibility.Collapsed : Visibility.Visible;
        PainelResidencial.Visibility = residencial ? Visibility.Visible : Visibility.Collapsed;
        PainelChipTransponder.Visibility = residencial ? Visibility.Collapsed : Visibility.Visible;
        TxtSeloSegmento.Text = residencial ? "Área Residencial" : "Área Veicular";

        var opcoes = residencial ? TiposResidenciais : TiposAutomotivos;
        var padrao = residencial ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA";
        PainelTiposServico.Children.Clear();
        _checksTipos.Clear();

        var marcouAlgum = false;
        foreach (var (rotulo, tag) in opcoes)
        {
            var check = new CheckBox
            {
                Content = rotulo,
                Tag = tag,
                IsChecked = selecionadosAtuais.Contains(tag),
                Margin = new Thickness(0, 4, 10, 4),
            };
            if (check.IsChecked == true) marcouAlgum = true;
            _checksTipos.Add(check);
            PainelTiposServico.Children.Add(check);
        }
        if (!marcouAlgum)
        {
            var padraoCheck = _checksTipos.FirstOrDefault(c => (string?)c.Tag == padrao) ?? _checksTipos.FirstOrDefault();
            if (padraoCheck is not null) padraoCheck.IsChecked = true;
        }
    }

    // ==================== CONSULTA AUTOMÁTICA DE VEÍCULO PELA PLACA ====================

    private static string NormalizarPlaca(string valor) =>
        new((valor ?? "").Trim().ToUpperInvariant().Where(char.IsLetterOrDigit).ToArray());

    private void Placa_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (_carregando) return;
        var limpo = NormalizarPlaca(Placa.Text);
        if (limpo.Length > 7) limpo = limpo[..7];
        string formatado = limpo;
        if (limpo.Length == 7 &&
            char.IsLetter(limpo[0]) && char.IsLetter(limpo[1]) && char.IsLetter(limpo[2]) &&
            char.IsDigit(limpo[3]) && char.IsDigit(limpo[4]) && char.IsDigit(limpo[5]) && char.IsDigit(limpo[6]))
        {
            formatado = $"{limpo[..3]}-{limpo[3..]}";
        }
        if (Placa.Text != formatado)
        {
            var sel = Placa.SelectionStart;
            Placa.Text = formatado;
            Placa.SelectionStart = Math.Min(sel, Placa.Text.Length);
        }

        if (SegmentoAtual() == "AUTOMOTIVO" && RegexPlacaValida.IsMatch(limpo) && limpo != _ultimaPlacaConsultada)
        {
            _ = ConsultarPlacaAsync(limpo, aguardarDebounce: true);
        }
        else if (limpo.Length < 7)
        {
            _ctsPlaca?.Cancel();
            TxtStatusPlaca.Visibility = Visibility.Collapsed;
        }
    }

    private void BuscarPlaca_Click(object sender, RoutedEventArgs e)
    {
        var limpo = NormalizarPlaca(Placa.Text);
        if (!RegexPlacaValida.IsMatch(limpo))
        {
            MostrarStatusPlaca("Informe uma placa brasileira válida (antiga ou Mercosul).", erro: true);
            Placa.Focus();
            return;
        }
        _ = ConsultarPlacaAsync(limpo, aguardarDebounce: false, forcar: true);
    }

    private async Task ConsultarPlacaAsync(string placaNormalizada, bool aguardarDebounce, bool forcar = false)
    {
        _ctsPlaca?.Cancel();
        _ctsPlaca?.Dispose();
        var cts = new CancellationTokenSource();
        _ctsPlaca = cts;
        var ct = cts.Token;

        try
        {
            if (aguardarDebounce)
            {
                await Task.Delay(450, ct);
            }
            if (ct.IsCancellationRequested || NormalizarPlaca(Placa.Text) != placaNormalizada) return;

            _ultimaPlacaConsultada = placaNormalizada;

            // 1. Aproveita histórico local da empresa antes de consultar a rede
            var localMatch = _historicoLocal
                .Where(a => a.Segmento != "RESIDENCIAL" && NormalizarPlaca(a.Placa) == placaNormalizada && !string.IsNullOrWhiteSpace(a.Modelo))
                .OrderByDescending(a => a.DataServico)
                .FirstOrDefault();

            if (localMatch is not null)
            {
                AplicarDadosPlaca(localMatch.Modelo, localMatch.Marca, localMatch.Ano, forcar);
                MostrarStatusPlaca("Veículo preenchido pelo histórico local da empresa.", erro: false);
                return;
            }

            if (_api is null || string.IsNullOrWhiteSpace(_empresaId))
            {
                MostrarStatusPlaca("Sem conexão ativa. Informe o modelo manualmente.", erro: true);
                return;
            }

            MostrarStatusPlaca("Consultando dados da placa…", erro: false);
            BtnBuscarPlaca.IsEnabled = false;
            try
            {
                var res = await _api.ConsultarVeiculoPorPlacaAsync(_empresaId, placaNormalizada);
                if (ct.IsCancellationRequested || NormalizarPlaca(Placa.Text) != placaNormalizada) return;

                if (res.Encontrado)
                {
                    AplicarDadosPlaca(res.Modelo, res.Marca, res.Ano, forcar);
                    MostrarStatusPlaca("Modelo preenchido automaticamente. Confira os dados do veículo.", erro: false);
                }
                else
                {
                    MostrarStatusPlaca(res.Mensagem ?? "Placa não localizada. Informe o modelo manualmente.", erro: true);
                }
            }
            finally
            {
                BtnBuscarPlaca.IsEnabled = true;
            }
        }
        catch (OperationCanceledException) { }
        catch
        {
            if (!ct.IsCancellationRequested)
            {
                MostrarStatusPlaca("Consulta indisponível no momento. Informe o modelo manualmente.", erro: true);
            }
        }
    }

    private void AplicarDadosPlaca(string modelo, string marca, string ano, bool forcar)
    {
        if (forcar || string.IsNullOrWhiteSpace(Modelo.Text)) Modelo.Text = modelo;
        if ((forcar || string.IsNullOrWhiteSpace(Marca.Text)) && !string.IsNullOrWhiteSpace(marca)) Marca.Text = marca;
        if ((forcar || string.IsNullOrWhiteSpace(Ano.Text)) && !string.IsNullOrWhiteSpace(ano)) Ano.Text = ano;
    }

    private void MostrarStatusPlaca(string texto, bool erro)
    {
        TxtStatusPlaca.Text = texto;
        TxtStatusPlaca.Foreground = erro
            ? (Brush)FindResource("CorAlerta")
            : (Brush)FindResource("CorPrimariaEscura");
        TxtStatusPlaca.Visibility = Visibility.Visible;
    }

    // ==================== FOTOS E MÁSCARAS ====================

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
        if (File.Exists(foto.CaminhoLocal)) File.Delete(foto.CaminhoLocal);
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

    private void ClienteTelefone_LostFocus(object sender, RoutedEventArgs e)
    {
        var digitos = new string(ClienteTelefone.Text.Where(char.IsDigit).ToArray());
        if (digitos.Length == 11)
        {
            ClienteTelefone.Text = $"({digitos[..2]}) {digitos.Substring(2, 5)}-{digitos.Substring(7, 4)}";
        }
        else if (digitos.Length == 10)
        {
            ClienteTelefone.Text = $"({digitos[..2]}) {digitos.Substring(2, 4)}-{digitos.Substring(6, 4)}";
        }
    }

    private void Valor_LostFocus(object sender, RoutedEventArgs e)
    {
        if (TentarLerValor(Valor.Text, out var v))
        {
            Valor.Text = v.ToString("N2", CultureInfo.CurrentCulture);
        }
    }

    private static bool TentarLerValor(string texto, out decimal valor)
    {
        var limpo = (texto ?? "").Replace("R$", "", StringComparison.OrdinalIgnoreCase).Trim();
        if (limpo.Length == 0)
        {
            valor = 0m;
            return true;
        }
        if (decimal.TryParse(limpo, NumberStyles.Number, CultureInfo.CurrentCulture, out valor) && valor >= 0)
            return true;
        if (decimal.TryParse(limpo, NumberStyles.Number, CultureInfo.InvariantCulture, out valor) && valor >= 0)
            return true;
        return false;
    }

    private Atendimento ConstruirRascunho(long valorCentavos, IEnumerable<string> tipos)
    {
        var segmento = SegmentoAtual();
        var data = DataServico.SelectedDate ?? DateTime.Today;
        return new Atendimento
        {
            Uuid = _original.Uuid,
            Segmento = segmento,
            Placa = Placa.Text.Trim().ToUpperInvariant(),
            Marca = Marca.Text.Trim(),
            Modelo = Modelo.Text.Trim(),
            Ano = Ano.Text.Trim(),
            Chassi = Chassi.Text.Trim(),
            TipoLocal = TipoLocal.Text.Trim(),
            AmbientePorta = AmbientePorta.Text.Trim(),
            MarcaFechadura = MarcaFechadura.Text.Trim(),
            ModeloFechadura = ModeloFechadura.Text.Trim(),
            TipoCilindro = TipoCilindro.Text.Trim(),
            EnderecoLocal = EnderecoLocal.Text.Trim(),
            DataServico = new DateTimeOffset(data).ToUnixTimeMilliseconds(),
            TiposServico = tipos.ToList(),
            Defeito = Defeito.Text.Trim(),
            Solucao = Solucao.Text.Trim(),
            Abertura = Abertura.Text.Trim(),
            CodigoChave = CodigoChave.Text.Trim(),
            CodigoConfiguracao = CodigoConfiguracao.Text.Trim(),
            ChipTransponder = segmento == "RESIDENCIAL" ? "" : ChipTransponder.Text.Trim(),
            Ferramenta = Ferramenta.Text.Trim(),
            ValorCentavos = valorCentavos,
            ClienteNome = ClienteNome.Text.Trim(),
            ClienteTelefone = ClienteTelefone.Text.Trim(),
            Pago = Pago.IsChecked == true,
            Revisao = _original.Revisao,
            ExcluidoEm = _original.ExcluidoEm,
        };
    }

    private void VerFicha_Click(object sender, RoutedEventArgs e)
    {
        _aoVerFicha?.Invoke(ConstruirRascunho(_original.ValorCentavos, ObterTiposSelecionados()));
    }

    private void Salvar_Click(object sender, RoutedEventArgs e)
    {
        var segmento = SegmentoAtual();
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

        var tipos = ObterTiposSelecionados();
        if (tipos.Count == 0)
        {
            Erro.Text = "Selecione ao menos um tipo de serviço.";
            return;
        }
        if (!TentarLerValor(Valor.Text, out var valor))
        {
            Erro.Text = "Informe um valor válido, ou deixe vazio para R$ 0,00.";
            Valor.Focus();
            return;
        }

        var centavos = decimal.ToInt64(decimal.Round(valor * 100m, 0, MidpointRounding.AwayFromZero));
        Resultado = ConstruirRascunho(centavos, tipos);
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
