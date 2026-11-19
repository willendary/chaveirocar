using System.IO;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Aplica o ícone oficial do ChaveiroCar em todas as janelas WPF e diretamente
/// via Win32 WM_SETICON (ICON_BIG / ICON_SMALL) para garantir exibição correta
/// na barra de tarefas, Alt+Tab e barra de título do Windows.
/// </summary>
public static class IconeApp
{
    private const uint WM_SETICON = 0x0080;
    private const int ICON_SMALL = 0;
    private const int ICON_BIG = 1;
    private const int SM_CXICON = 11;
    private const int SM_CYICON = 12;
    private const int SM_CXSMICON = 49;
    private const int SM_CYSMICON = 50;
    private const uint IMAGE_ICON = 1;
    private const uint LR_LOADFROMFILE = 0x0010;

    private static ImageSource? _iconeJanelaCache;
    private static ImageSource? _logoSimboloCache;
    private static string? _caminhoIcoTemporario;

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int SetCurrentProcessExplicitAppUserModelID(string appId);

    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int nIndex);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadImage(IntPtr hInst, string lpszName, uint uType, int cxDesired, int cyDesired, uint fuLoad);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyIcon(IntPtr hIcon);

    public static void ConfigurarIdentidadeProcesso()
    {
        try
        {
            SetCurrentProcessExplicitAppUserModelID("ChaveiroCar.Desktop.Oficial");
        }
        catch { }
    }

    public static ImageSource? ObterLogoSimbolo()
    {
        if (_logoSimboloCache is not null) return _logoSimboloCache;
        try
        {
            var uri = new Uri("pack://application:,,,/ChaveiroCar.Windows;component/Assets/logo-simbolo.png", UriKind.Absolute);
            var img = BitmapFrame.Create(uri);
            img.Freeze();
            _logoSimboloCache = img;
            return img;
        }
        catch
        {
            return null;
        }
    }

    public static void Aplicar(Window janela)
    {
        try
        {
            _iconeJanelaCache ??= CarregarImageSourceIcone();
            if (_iconeJanelaCache is not null)
            {
                janela.Icon = _iconeJanelaCache;
            }
        }
        catch { }

        janela.SourceInitialized += (_, _) => AplicarIconeWin32(janela);
    }

    private static ImageSource? CarregarImageSourceIcone()
    {
        try
        {
            var uriIco = new Uri("pack://application:,,,/ChaveiroCar.Windows;component/app.ico", UriKind.Absolute);
            var decoder = BitmapDecoder.Create(uriIco, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
            var melhor = decoder.Frames
                .OrderByDescending(f => f.PixelWidth)
                .FirstOrDefault();
            if (melhor is not null)
            {
                melhor.Freeze();
                return melhor;
            }
        }
        catch { }

        try
        {
            var uriPng = new Uri("pack://application:,,,/ChaveiroCar.Windows;component/Assets/logo.png", UriKind.Absolute);
            var frame = BitmapFrame.Create(uriPng);
            frame.Freeze();
            return frame;
        }
        catch
        {
            return null;
        }
    }

    private static string? GarantirArquivoIco()
    {
        if (!string.IsNullOrWhiteSpace(_caminhoIcoTemporario) && File.Exists(_caminhoIcoTemporario))
        {
            return _caminhoIcoTemporario;
        }

        try
        {
            var pasta = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "ChaveiroCar");
            Directory.CreateDirectory(pasta);
            var destino = Path.Combine(pasta, "chaveirocar-oficial.ico");
            var info = Application.GetResourceStream(new Uri("pack://application:,,,/ChaveiroCar.Windows;component/app.ico", UriKind.Absolute));
            if (info?.Stream is null) return null;
            using (info.Stream)
            using (var fs = new FileStream(destino, FileMode.Create, FileAccess.Write, FileShare.Read))
            {
                info.Stream.CopyTo(fs);
            }
            _caminhoIcoTemporario = destino;
            return destino;
        }
        catch
        {
            return null;
        }
    }

    private static void AplicarIconeWin32(Window janela)
    {
        try
        {
            var hwnd = new WindowInteropHelper(janela).Handle;
            if (hwnd == IntPtr.Zero) return;

            var icoPath = GarantirArquivoIco();
            if (string.IsNullOrWhiteSpace(icoPath)) return;

            int bigW = Math.Max(32, GetSystemMetrics(SM_CXICON));
            int bigH = Math.Max(32, GetSystemMetrics(SM_CYICON));
            int smallW = Math.Max(16, GetSystemMetrics(SM_CXSMICON));
            int smallH = Math.Max(16, GetSystemMetrics(SM_CYSMICON));

            var hBig = LoadImage(IntPtr.Zero, icoPath, IMAGE_ICON, bigW, bigH, LR_LOADFROMFILE);
            var hSmall = LoadImage(IntPtr.Zero, icoPath, IMAGE_ICON, smallW, smallH, LR_LOADFROMFILE);

            if (hBig != IntPtr.Zero)
            {
                SendMessage(hwnd, WM_SETICON, (IntPtr)ICON_BIG, hBig);
            }
            if (hSmall != IntPtr.Zero)
            {
                SendMessage(hwnd, WM_SETICON, (IntPtr)ICON_SMALL, hSmall);
            }

            janela.Closed += (_, _) =>
            {
                if (hBig != IntPtr.Zero) DestroyIcon(hBig);
                if (hSmall != IntPtr.Zero) DestroyIcon(hSmall);
            };
        }
        catch { }
    }
}
