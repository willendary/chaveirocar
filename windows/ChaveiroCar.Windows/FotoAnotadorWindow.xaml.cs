using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class FotoAnotadorWindow : Window
{
    private readonly string _caminho;
    private readonly BitmapSource _bitmap;
    private readonly List<SetaAnotacao> _setas;
    private readonly List<SetaAnotacao> _desfeitas = [];
    private Point? _inicioArrasto;
    private Point? _atualArrasto;

    public string ResultadoAnotacoes { get; private set; } = "[]";

    public FotoAnotadorWindow(string caminho, string? anotacoesIniciais)
    {
        _caminho = caminho;
        _bitmap = FotosLocais.CarregarBaseParaEdicao(caminho);
        _setas = FotosLocais.LerAnotacoes(anotacoesIniciais);
        InitializeComponent();

        ImagemBase.Source = _bitmap;
        AreaImagem.Width = _bitmap.PixelWidth;
        AreaImagem.Height = _bitmap.PixelHeight;
        CamadaSetas.Width = _bitmap.PixelWidth;
        CamadaSetas.Height = _bitmap.PixelHeight;

        Redesenhar();
    }

    private string CorSelecionada() =>
        (SeletorCor.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "#EF4444";

    private double EspessuraSelecionada() =>
        double.TryParse((SeletorEspessura.SelectedItem as ComboBoxItem)?.Tag?.ToString(),
            NumberStyles.Number, CultureInfo.InvariantCulture, out var v) ? v : 5.0;

    private void AreaImagem_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        _inicioArrasto = e.GetPosition(AreaImagem);
        _atualArrasto = _inicioArrasto;
        AreaImagem.CaptureMouse();
    }

    private void AreaImagem_MouseMove(object sender, MouseEventArgs e)
    {
        if (_inicioArrasto is null) return;
        _atualArrasto = Limitado(e.GetPosition(AreaImagem));
        Redesenhar();
    }

    private void AreaImagem_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (_inicioArrasto is null) return;
        var ini = _inicioArrasto.Value;
        var fim = Limitado(e.GetPosition(AreaImagem));
        _inicioArrasto = null;
        _atualArrasto = null;
        AreaImagem.ReleaseMouseCapture();

        var largura = Math.Max(1.0, _bitmap.PixelWidth);
        var altura = Math.Max(1.0, _bitmap.PixelHeight);
        var x1 = Math.Clamp(ini.X / largura, 0.0, 1.0);
        var y1 = Math.Clamp(ini.Y / altura, 0.0, 1.0);
        var x2 = Math.Clamp(fim.X / largura, 0.0, 1.0);
        var y2 = Math.Clamp(fim.Y / altura, 0.0, 1.0);
        if (Math.Sqrt(Math.Pow(x2 - x1, 2) + Math.Pow(y2 - y1, 2)) >= 0.02)
        {
            _setas.Add(new SetaAnotacao(x1, y1, x2, y2, CorSelecionada(), EspessuraSelecionada()));
            _desfeitas.Clear();
        }
        Redesenhar();
    }

    private Point Limitado(Point p) => new(
        Math.Clamp(p.X, 0.0, _bitmap.PixelWidth),
        Math.Clamp(p.Y, 0.0, _bitmap.PixelHeight));

    private void Desfazer_Click(object sender, RoutedEventArgs e)
    {
        if (_setas.Count == 0) return;
        var ultima = _setas[^1];
        _setas.RemoveAt(_setas.Count - 1);
        _desfeitas.Add(ultima);
        Redesenhar();
    }

    private void Refazer_Click(object sender, RoutedEventArgs e)
    {
        if (_desfeitas.Count == 0) return;
        var recuperada = _desfeitas[^1];
        _desfeitas.RemoveAt(_desfeitas.Count - 1);
        _setas.Add(recuperada);
        Redesenhar();
    }

    private void Limpar_Click(object sender, RoutedEventArgs e)
    {
        _setas.Clear();
        _desfeitas.Clear();
        Redesenhar();
    }

    private void Redesenhar()
    {
        CamadaSetas.Children.Clear();
        var largura = Math.Max(1.0, _bitmap.PixelWidth);
        var altura = Math.Max(1.0, _bitmap.PixelHeight);
        var menorLado = Math.Max(1.0, Math.Min(largura, altura));

        foreach (var seta in _setas)
        {
            AdicionarSetaNoCanvas(seta, largura, altura, menorLado);
        }

        if (_inicioArrasto is { } ini && _atualArrasto is { } fim)
        {
            var rascunho = new SetaAnotacao(
                ini.X / largura,
                ini.Y / altura,
                fim.X / largura,
                fim.Y / altura,
                CorSelecionada(),
                EspessuraSelecionada());
            AdicionarSetaNoCanvas(rascunho, largura, altura, menorLado);
        }

        BtnDesfazer.IsEnabled = _setas.Count > 0;
        BtnRefazer.IsEnabled = _desfeitas.Count > 0;
        BtnLimpar.IsEnabled = _setas.Count > 0;
    }

    private void AdicionarSetaNoCanvas(SetaAnotacao seta, double largura, double altura, double menorLado)
    {
        var x1 = seta.X1 * largura;
        var y1 = seta.Y1 * altura;
        var x2 = seta.X2 * largura;
        var y2 = seta.Y2 * altura;
        var dx = x2 - x1;
        var dy = y2 - y1;
        if (Math.Sqrt(dx * dx + dy * dy) < 2.0) return;

        var traco = Math.Max(2.0, seta.Espessura * (menorLado / 420.0));
        var pincel = new SolidColorBrush(FotosLocais.ConverterCor(seta.Cor));

        CamadaSetas.Children.Add(new Line
        {
            X1 = x1,
            Y1 = y1,
            X2 = x2,
            Y2 = y2,
            Stroke = pincel,
            StrokeThickness = traco,
            StrokeStartLineCap = PenLineCap.Round,
            StrokeEndLineCap = PenLineCap.Round,
        });

        var angulo = Math.Atan2(dy, dx);
        var compPonta = Math.Max(14.0, traco * 3.8);
        var abertura = Math.PI / 6.0;
        var poligono = new Polygon
        {
            Fill = pincel,
            Stroke = pincel,
            StrokeThickness = Math.Max(1.0, traco * 0.4),
            Points =
            [
                new Point(x2, y2),
                new Point(x2 - compPonta * Math.Cos(angulo - abertura), y2 - compPonta * Math.Sin(angulo - abertura)),
                new Point(x2 - compPonta * Math.Cos(angulo + abertura), y2 - compPonta * Math.Sin(angulo + abertura)),
            ],
        };
        CamadaSetas.Children.Add(poligono);
    }

    private void Salvar_Click(object sender, RoutedEventArgs e)
    {
        FotosLocais.AplicarAnotacoes(_caminho, _setas);
        ResultadoAnotacoes = FotosLocais.SerializarAnotacoes(_setas);
        DialogResult = true;
    }
}
