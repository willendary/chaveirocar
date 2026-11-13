using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace ChaveiroCar.Windows.Services;

public sealed record SetaAnotacao(
    double X1,
    double Y1,
    double X2,
    double Y2,
    string Cor = "#EF4444",
    double Espessura = 5.0);

public static class FotosLocais
{
    public static string CaminhoOriginal(string caminho)
    {
        var pasta = Path.GetDirectoryName(caminho) ?? "";
        var nome = Path.GetFileName(caminho);
        return Path.Combine(pasta, $"orig_{nome}");
    }

    public static List<SetaAnotacao> LerAnotacoes(string? json)
    {
        if (string.IsNullOrWhiteSpace(json)) return [];
        try
        {
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.ValueKind != JsonValueKind.Array) return [];
            var lista = new List<SetaAnotacao>();
            foreach (var item in doc.RootElement.EnumerateArray())
            {
                lista.Add(new SetaAnotacao(
                    X1: Math.Clamp(item.TryGetProperty("x1", out var x1) ? x1.GetDouble() : 0.2, 0.0, 1.0),
                    Y1: Math.Clamp(item.TryGetProperty("y1", out var y1) ? y1.GetDouble() : 0.2, 0.0, 1.0),
                    X2: Math.Clamp(item.TryGetProperty("x2", out var x2) ? x2.GetDouble() : 0.8, 0.0, 1.0),
                    Y2: Math.Clamp(item.TryGetProperty("y2", out var y2) ? y2.GetDouble() : 0.8, 0.0, 1.0),
                    Cor: item.TryGetProperty("cor", out var c) && c.ValueKind == JsonValueKind.String ? c.GetString() ?? "#EF4444" : "#EF4444",
                    Espessura: Math.Clamp(item.TryGetProperty("espessura", out var esp) ? esp.GetDouble() : 5.0, 1.0, 20.0)));
            }
            return lista;
        }
        catch
        {
            return [];
        }
    }

    public static string SerializarAnotacoes(IEnumerable<SetaAnotacao> setas)
    {
        var itens = setas.Select(s => new
        {
            x1 = s.X1,
            y1 = s.Y1,
            x2 = s.X2,
            y2 = s.Y2,
            cor = s.Cor,
            espessura = s.Espessura,
        });
        return JsonSerializer.Serialize(itens);
    }

    public static BitmapSource CarregarBaseParaEdicao(string caminho)
    {
        var orig = CaminhoOriginal(caminho);
        var fonte = File.Exists(orig) ? orig : caminho;
        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.UriSource = new Uri(Path.GetFullPath(fonte), UriKind.Absolute);
        bitmap.EndInit();
        bitmap.Freeze();
        return bitmap;
    }

    public static void AplicarAnotacoes(string caminho, IReadOnlyList<SetaAnotacao> setas)
    {
        if (!File.Exists(caminho)) return;
        var orig = CaminhoOriginal(caminho);
        if (setas.Count == 0)
        {
            if (File.Exists(orig))
            {
                File.Copy(orig, caminho, overwrite: true);
                File.Delete(orig);
            }
            return;
        }

        if (!File.Exists(orig))
        {
            File.Copy(caminho, orig, overwrite: true);
        }

        var baseImg = CarregarBaseParaEdicao(caminho);
        var largura = baseImg.PixelWidth;
        var altura = baseImg.PixelHeight;
        var menorLado = Math.Max(1.0, Math.Min(largura, altura));

        var visual = new DrawingVisual();
        using (var dc = visual.RenderOpen())
        {
            dc.DrawImage(baseImg, new Rect(0, 0, largura, altura));
            foreach (var seta in setas)
            {
                var cor = ConverterCor(seta.Cor);
                var traco = Math.Max(2.0, seta.Espessura * (menorLado / 420.0));
                var x1 = seta.X1 * largura;
                var y1 = seta.Y1 * altura;
                var x2 = seta.X2 * largura;
                var y2 = seta.Y2 * altura;

                var canetaSombra = new Pen(new SolidColorBrush(Color.FromArgb(140, 0, 0, 0)), traco + 2.0)
                {
                    StartLineCap = PenLineCap.Round,
                    EndLineCap = PenLineCap.Round,
                };
                var caneta = new Pen(new SolidColorBrush(cor), traco)
                {
                    StartLineCap = PenLineCap.Round,
                    EndLineCap = PenLineCap.Round,
                };

                DesenharSeta(dc, canetaSombra, new SolidColorBrush(Color.FromArgb(140, 0, 0, 0)), x1, y1, x2, y2, traco + 2.0);
                DesenharSeta(dc, caneta, new SolidColorBrush(cor), x1, y1, x2, y2, traco);
            }
        }

        var render = new RenderTargetBitmap(largura, altura, 96, 96, PixelFormats.Pbgra32);
        render.Render(visual);

        var ext = Path.GetExtension(caminho).ToLowerInvariant();
        BitmapEncoder encoder = ext == ".png"
            ? new PngBitmapEncoder()
            : new JpegBitmapEncoder { QualityLevel = 92 };
        encoder.Frames.Add(BitmapFrame.Create(render));
        using var fs = new FileStream(caminho, FileMode.Create, FileAccess.Write);
        encoder.Save(fs);
    }

    private static void DesenharSeta(
        DrawingContext dc, Pen caneta, Brush preenchimento, double x1, double y1, double x2, double y2, double traco)
    {
        var dx = x2 - x1;
        var dy = y2 - y1;
        var dist = Math.Sqrt(dx * dx + dy * dy);
        if (dist < 2.0) return;

        dc.DrawLine(caneta, new Point(x1, y1), new Point(x2, y2));

        var angulo = Math.Atan2(dy, dx);
        var compPonta = Math.Max(14.0, traco * 3.8);
        var abertura = Math.PI / 6.0;

        var p1 = new Point(x2, y2);
        var p2 = new Point(x2 - compPonta * Math.Cos(angulo - abertura), y2 - compPonta * Math.Sin(angulo - abertura));
        var p3 = new Point(x2 - compPonta * Math.Cos(angulo + abertura), y2 - compPonta * Math.Sin(angulo + abertura));

        var geo = new StreamGeometry();
        using (var ctx = geo.Open())
        {
            ctx.BeginFigure(p1, isFilled: true, isClosed: true);
            ctx.LineTo(p2, isStroked: true, isSmoothJoin: true);
            ctx.LineTo(p3, isStroked: true, isSmoothJoin: true);
        }
        geo.Freeze();
        dc.DrawGeometry(preenchimento, caneta, geo);
    }

    public static Color ConverterCor(string hex)
    {
        try
        {
            if (ColorConverter.ConvertFromString(hex) is Color c) return c;
        }
        catch { }
        return Color.FromRgb(239, 68, 68);
    }
    public static IReadOnlyList<ArmazenamentoLocal.FotoLocal> Copiar(
        IEnumerable<string> arquivos,
        string registroId,
        bool ficha,
        int quantidadeExistente,
        string categoria,
        string legenda)
    {
        var origens = arquivos.ToList();
        if (quantidadeExistente + origens.Count > 20)
            throw new InvalidDataException("Cada registro aceita no máximo 20 fotos.");
        foreach (var origem in origens)
        {
            var extensao = Path.GetExtension(origem).ToLowerInvariant();
            if (extensao is not (".jpg" or ".jpeg" or ".png" or ".webp") ||
                new FileInfo(origem).Length > 15 * 1024 * 1024)
                throw new InvalidDataException("Use JPEG, PNG ou WebP de até 15 MB.");
        }

        var pasta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ChaveiroCar", "fotos");
        Directory.CreateDirectory(pasta);
        var criadas = new List<ArmazenamentoLocal.FotoLocal>();
        try
        {
            for (var indice = 0; indice < origens.Count; indice++)
            {
                var origem = origens[indice];
                var uuid = Guid.NewGuid().ToString();
                var caminho = Path.Combine(pasta, uuid + Path.GetExtension(origem).ToLowerInvariant());
                File.Copy(origem, caminho);
                criadas.Add(new ArmazenamentoLocal.FotoLocal
                {
                    Uuid = uuid,
                    AtendimentoUuid = ficha ? "" : registroId,
                    FichaUuid = ficha ? registroId : "",
                    Categoria = categoria,
                    Legenda = legenda.Trim() is { Length: > 0 } rotulo
                        ? rotulo
                        : Path.GetFileNameWithoutExtension(origem),
                    Ordem = quantidadeExistente + indice,
                    CaminhoLocal = caminho,
                    Pendente = true,
                });
            }
            return criadas;
        }
        catch
        {
            foreach (var foto in criadas) File.Delete(foto.CaminhoLocal);
            throw;
        }
    }
}
