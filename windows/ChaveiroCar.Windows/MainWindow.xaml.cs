using System.Diagnostics;
using System.Globalization;
using System.Net.Http;
using System.Text;
using System.Windows;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class MainWindow : Window
{
    private readonly ArmazenamentoLocal _local;
    private readonly DispatcherTimer _timerSincronia = new() { Interval = TimeSpan.FromSeconds(20) };
    private ClienteApi? _api;
    private List<ClienteApi.Empresa> _empresas = [];
    private string? _modalidadeAtiva;
    private bool _sincronizando;
    private bool _sincronizarNovamente;
    private bool _editandoModal;
    private DateTimeOffset _ultimaSincronia = DateTimeOffset.MinValue;

    public bool SaiuDaConta { get; private set; }

    public MainWindow() : this(new ArmazenamentoLocal())
    {
    }

    public MainWindow(ArmazenamentoLocal local)
    {
        _local = local;
        InitializeComponent();
        CarregarIconeSeguro();
        Loaded += MainWindow_Loaded;
        Activated += MainWindow_Activated;
        Closed += MainWindow_Closed;
        _timerSincronia.Tick += async (_, _) =>
        {
            if (!_editandoModal && !TemDialogoModalAberto()) await Sincronizar();
        };
    }

    private bool TemDialogoModalAberto() =>
        _editandoModal || OwnedWindows.Cast<Window>().Any(w => w.IsVisible);

    private void CarregarIconeSeguro()
    {
        try
        {
            var uri = new Uri("pack://application:,,,/ChaveiroCar.Windows;component/Assets/logo.png", UriKind.Absolute);
            Icon = System.Windows.Media.Imaging.BitmapFrame.Create(uri);
        }
        catch { }

        try
        {
            var uriLogo = new Uri("pack://application:,,,/ChaveiroCar.Windows;component/Assets/logo-simbolo.png", UriKind.Absolute);
            LogoCabecalho.Source = System.Windows.Media.Imaging.BitmapFrame.Create(uriLogo);
        }
        catch { }
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_local.Estado.Acesso))
        {
            var login = new LoginWindow(_local) { Owner = this };
            if (login.ShowDialog() != true || string.IsNullOrWhiteSpace(_local.Estado.Acesso))
            {
                SaiuDaConta = true;
                Close();
                return;
            }
        }

        RecarregarGrade();
        AtualizarInterfaceModalidade();
        AtualizarStatus();
        var versaoAtual = System.Reflection.Assembly.GetEntryAssembly()?.GetName().Version?.ToString(3) ?? "1.12.0";
        TxtVersaoApp.Text = $"ChaveiroCar Desktop v{versaoAtual}";
        await Inicializar();
        _timerSincronia.Start();
        await VerificarAtualizacao();
    }

    private async void MainWindow_Activated(object? sender, EventArgs e)
    {
        if (!TemDialogoModalAberto() && DateTimeOffset.UtcNow - _ultimaSincronia >= TimeSpan.FromSeconds(5))
        {
            await Sincronizar();
        }
    }

    private void MainWindow_Closed(object? sender, EventArgs e) => _timerSincronia.Stop();

    private AtualizacaoDisponivel? _atualizacaoPendente;

    private async Task VerificarAtualizacao(bool interativo = false)
    {
        var atualizacao = await VerificadorAtualizacao.VerificarAsync();
        _atualizacaoPendente = atualizacao;
        if (atualizacao is null)
        {
            BtnAtualizacaoDisponivel.Visibility = Visibility.Collapsed;
            if (interativo)
            {
                MessageBox.Show("Você já está utilizando a versão mais recente do ChaveiroCar.",
                    "Atualizações", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            return;
        }

        BtnAtualizacaoDisponivel.Content = $"Nova versão v{atualizacao.Versao} disponível";
        BtnAtualizacaoDisponivel.Visibility = Visibility.Visible;

        if (interativo)
        {
            AbrirJanelaAtualizacao(atualizacao);
        }
        else
        {
            var resposta = MessageBox.Show(
                $"Nova versão v{atualizacao.Versao} disponível no GitHub com melhorias e correções.\n\nDeseja abrir o atualizador agora?",
                "Atualização disponível", MessageBoxButton.YesNo, MessageBoxImage.Information);
            if (resposta == MessageBoxResult.Yes)
            {
                AbrirJanelaAtualizacao(atualizacao);
            }
        }
    }

    private void AbrirJanelaAtualizacao(AtualizacaoDisponivel atualizacao)
    {
        var janela = new JanelaAtualizacao(atualizacao) { Owner = this };
        janela.ShowDialog();
    }

    private async void VerificarAtualizacoes_Click(object sender, RoutedEventArgs e)
    {
        await VerificarAtualizacao(interativo: true);
    }

    private void AtualizarApp_Click(object sender, RoutedEventArgs e)
    {
        if (_atualizacaoPendente is not null)
        {
            AbrirJanelaAtualizacao(_atualizacaoPendente);
        }
    }

    private bool _sessaoExpiradaNotificada = false;

    private void TratarSessaoExpirada()
    {
        if (_sessaoExpiradaNotificada) return;
        _sessaoExpiradaNotificada = true;

        Dispatcher.Invoke(() =>
        {
            _timerSincronia.Stop();
            _local.Estado.Acesso = null;
            _local.Estado.Refresh = null;
            _local.Salvar();

            MessageBox.Show(
                "Sua sessão expirou ou foi desconectada. Por favor, faça login novamente para continuar sincronizando com a nuvem.",
                "Sessão Expirada", MessageBoxButton.OK, MessageBoxImage.Information);

            SaiuDaConta = true;
            Close();
        });
    }

    private async Task Inicializar()
    {
        _api = new ClienteApi(_local.Estado.BaseUrl);
        _api.AoExpirarSessao += TratarSessaoExpirada;
        _api.OnTokensAtualizados += (acesso, refresh) =>
        {
            _local.Estado.Acesso = acesso;
            _local.Estado.Refresh = refresh;
            _local.Salvar();
        };
        _api.DefinirTokens(_local.Estado.Acesso, _local.Estado.Refresh);
        try
        {
            _empresas = (await _api.EmpresasAsync()).ToList();
            RegistrarEmpresas(_empresas);
            Empresas.ItemsSource = _empresas;
            Empresas.DisplayMemberPath = "Nome";
            var atual = _empresas.FirstOrDefault(x => x.Id == _local.Estado.EmpresaId) ?? _empresas.FirstOrDefault();
            if (atual is not null)
            {
                Empresas.SelectedItem = atual;
                _local.Estado.EmpresaId = atual.Id;
                _local.Salvar();
            }
            AtualizarInterfaceModalidade();
            await Sincronizar();
        }
        catch (HttpRequestException ex) when (ex.StatusCode == System.Net.HttpStatusCode.Unauthorized)
        {
            TratarSessaoExpirada();
            return;
        }
        catch
        {
            Status.Text = "Modo offline ativo: exibindo dados sincronizados neste computador.";
        }
        AtualizarInterfaceModalidade();
        RecarregarGrade();
        AtualizarStatus();
    }

    private Task SalvarTokens()
    {
        if (_api is null) return Task.CompletedTask;
        if (_api.Acesso is not null) _local.Estado.Acesso = _api.Acesso;
        if (_api.Refresh is not null) _local.Estado.Refresh = _api.Refresh;
        _local.Salvar();
        return Task.CompletedTask;
    }

    private HashSet<string> ModulosAtivosAtuais() =>
        _local.Estado.ModulosAtivosDaEmpresa(_local.Estado.EmpresaId);

    private bool PodeEditarEmpresa()
    {
        var empresa = _empresas.FirstOrDefault(x => x.Id == _local.Estado.EmpresaId)
                      ?? Empresas?.SelectedItem as ClienteApi.Empresa;
        return empresa is null || !string.Equals(empresa.Papel, "leitor", StringComparison.OrdinalIgnoreCase);
    }

    private void RegistrarEmpresas(IEnumerable<ClienteApi.Empresa> empresas)
    {
        foreach (var emp in empresas)
        {
            _local.Estado.RegistrarAssinaturaEmpresa(
                emp.Id,
                emp.PlanoCodigo,
                emp.AssinaturaStatus,
                emp.ModulosAtivos,
                emp.ProximaRenovacaoEm);
        }
        _local.Salvar();
    }

    private void AtualizarInterfaceModalidade()
    {
        if (PainelEscolhaModalidade is null || PainelToolbar is null || PainelConteudo is null) return;

        var modulos = ModulosAtivosAtuais();
        var veicAtivo = modulos.Contains("AUTOMOTIVO");
        var resAtivo = modulos.Contains("RESIDENCIAL");

        if (_modalidadeAtiva is not null && !modulos.Contains(_modalidadeAtiva))
        {
            _modalidadeAtiva = null;
        }

        if (ResumoPlanoEscolha is not null)
        {
            var status = _local.Estado.StatusAssinaturaDaEmpresa(_local.Estado.EmpresaId);
            var ativosTxt = modulos.Count == 0
                ? "Nenhum módulo ativo"
                : string.Join(" + ", modulos.Select(m => m == "RESIDENCIAL" ? "Residencial" : "Veicular"));
            ResumoPlanoEscolha.Text = status == "ATIVA" ? $"Ativo: {ativosTxt}" : $"{status} · {ativosTxt}";
        }

        AtualizarCartaoModalidade(
            veicAtivo, BadgeVeicularEscolha, TxtBadgeVeicularEscolha,
            BtnEntrarVeicular, BtnConsultaVeicular,
            "Entrar em Veicular", "Contratar / Ativar Veicular");
        AtualizarCartaoModalidade(
            resAtivo, BadgeResidencialEscolha, TxtBadgeResidencialEscolha,
            BtnEntrarResidencial, BtnConsultaResidencial,
            "Entrar em Residencial", "Contratar / Ativar Residencial");

        var escolhendo = _modalidadeAtiva is null;
        PainelEscolhaModalidade.Visibility = escolhendo ? Visibility.Visible : Visibility.Collapsed;
        PainelToolbar.Visibility = escolhendo ? Visibility.Collapsed : Visibility.Visible;
        PainelConteudo.Visibility = escolhendo ? Visibility.Collapsed : Visibility.Visible;
        SeloModalidadeAtiva.Visibility = escolhendo ? Visibility.Collapsed : Visibility.Visible;
        BtnTrocarArea.Visibility = escolhendo ? Visibility.Collapsed : Visibility.Visible;
        PainelFiltroSegmento.Visibility = Visibility.Collapsed;

        if (!escolhendo)
        {
            var emVeicular = _modalidadeAtiva == "AUTOMOTIVO";
            TxtModalidadeAtiva.Text = emVeicular ? "Área: Veicular" : "Área: Residencial";
            TituloFicharioVeiculos.Visibility = emVeicular ? Visibility.Visible : Visibility.Collapsed;
            GradeFicharioVeiculos.Visibility = emVeicular ? Visibility.Visible : Visibility.Collapsed;
            RowDefFicharioVeiculos.Height = emVeicular ? new GridLength(1, GridUnitType.Star) : new GridLength(0);

            TituloFicharioResidencial.Visibility = emVeicular ? Visibility.Collapsed : Visibility.Visible;
            GradeFicharioResidencial.Visibility = emVeicular ? Visibility.Collapsed : Visibility.Visible;
            RowDefFicharioResidencial.Height = emVeicular ? new GridLength(0) : new GridLength(1, GridUnitType.Star);

            var podeEditar = PodeEditarEmpresa();
            BtnNovoAtendimento.IsEnabled = podeEditar;
            BtnEditarAtendimento.IsEnabled = podeEditar;
            BtnExcluirAtendimento.IsEnabled = podeEditar;
            BtnNovaFicha.IsEnabled = podeEditar;
            BtnEditarFicha.IsEnabled = podeEditar;
            BtnNovoAtendimentoFicha.IsEnabled = podeEditar;
            BtnExcluirFicha.IsEnabled = podeEditar;
        }
    }

    private void AtualizarCartaoModalidade(
        bool disponivel,
        Border? badge,
        TextBlock? txtBadge,
        Button? btnEntrar,
        Button? btnConsulta,
        string textoDisponivel,
        string textoIndisponivel)
    {
        if (txtBadge is not null)
        {
            txtBadge.Text = disponivel ? "Disponível no plano" : "Não contratado";
            txtBadge.Foreground = disponivel
                ? (Brush)FindResource("CorPrimariaEscura")
                : new SolidColorBrush(Color.FromRgb(146, 64, 14));
        }
        if (badge is not null)
        {
            badge.Background = disponivel
                ? (Brush)FindResource("CorPrimariaSuave")
                : new SolidColorBrush(Color.FromRgb(254, 243, 199));
        }
        if (btnEntrar is not null)
        {
            btnEntrar.Content = disponivel ? textoDisponivel : textoIndisponivel;
        }
        if (btnConsulta is not null)
        {
            btnConsulta.Visibility = disponivel ? Visibility.Visible : Visibility.Collapsed;
        }
    }

    private string SegmentoSelecionado() =>
        _modalidadeAtiva ?? (FiltroSegmento?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "";

    private void RecarregarGrade()
    {
        if (Grade is null || Busca is null || GradeFicharioVeiculos is null ||
            GradeFicharioResidencial is null || GradeFichas is null || GradeConflitos is null) return;
        var termo = normalizar(Busca.Text);
        var seg = SegmentoSelecionado();
        var itens = _local.Estado.Atendimentos
            .Where(a => a.ExcluidoEm is null && (string.IsNullOrEmpty(seg) || a.Segmento == seg) &&
                (termo.Length == 0 ||
                normalizar($"{a.Placa} {a.Marca} {a.Modelo} {a.TipoLocal} {a.AmbientePorta} {a.MarcaFechadura} {a.ModeloFechadura} {a.TipoCilindro} {a.EnderecoLocal} {a.ClienteNome}").Contains(termo)))
            .OrderByDescending(a => a.DataServico)
            .ToList();
        Grade.ItemsSource = itens;

        GradeFicharioVeiculos.ItemsSource = _local.Estado.Atendimentos
            .Where(a => a.Segmento != "RESIDENCIAL" && (!string.IsNullOrWhiteSpace(a.Marca) || !string.IsNullOrWhiteSpace(a.Modelo)))
            .GroupBy(a => new { Marca = a.Marca.Trim(), Modelo = a.Modelo.Trim(), Ano = a.Ano.Trim() })
            .Select(g => new
            {
                g.Key.Marca,
                g.Key.Modelo,
                g.Key.Ano,
                Servicos = g.Count(),
                Ultimo = DateTimeOffset.FromUnixTimeMilliseconds(g.Max(x => x.DataServico)).ToLocalTime().ToString("dd/MM/yyyy"),
                Abertura = g.Select(x => x.Abertura).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
                CodigoChave = g.Select(x => x.CodigoChave).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
                Chip = g.Select(x => x.ChipTransponder).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
            })
            .Where(f => termo.Length == 0 || normalizar($"{f.Marca} {f.Modelo} {f.Ano}").Contains(termo))
            .OrderByDescending(f => f.Servicos)
            .ToList();

        GradeFicharioResidencial.ItemsSource = _local.Estado.Atendimentos
            .Where(a => a.Segmento == "RESIDENCIAL")
            .GroupBy(a => new
            {
                TipoLocal = a.TipoLocal.Trim(),
                Ambiente = a.AmbientePorta.Trim(),
                MarcaFechadura = a.MarcaFechadura.Trim(),
                ModeloFechadura = a.ModeloFechadura.Trim(),
                Cilindro = a.TipoCilindro.Trim(),
            })
            .Select(g => new
            {
                g.Key.TipoLocal,
                g.Key.Ambiente,
                g.Key.MarcaFechadura,
                g.Key.ModeloFechadura,
                g.Key.Cilindro,
                Servicos = g.Count(),
                Ultimo = DateTimeOffset.FromUnixTimeMilliseconds(g.Max(x => x.DataServico)).ToLocalTime().ToString("dd/MM/yyyy"),
                Abertura = g.Select(x => x.Abertura).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
                CodigoChave = g.Select(x => x.CodigoChave).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
            })
            .Where(f => termo.Length == 0 || normalizar($"{f.TipoLocal} {f.Ambiente} {f.MarcaFechadura} {f.ModeloFechadura} {f.Cilindro}").Contains(termo))
            .OrderByDescending(f => f.Servicos)
            .ToList();

        var fichasEmpresa = _local.Estado.Fichas
            .Where(f => f.ExcluidoEm is null &&
                (string.IsNullOrEmpty(seg) || string.Equals(f.Segmento, seg, StringComparison.OrdinalIgnoreCase)) &&
                (termo.Length == 0 ||
                normalizar($"{f.Equipamento} {f.Marca} {f.Modelo} {f.Ano} {f.Sistema} {f.Chip} {f.Chave} {f.Manual}").Contains(termo)));
        var fichasCatalogo = CatalogoFichasPadrao.Listar(string.IsNullOrEmpty(seg) ? null : seg, Busca.Text);
        GradeFichas.ItemsSource = fichasEmpresa
            .Concat(fichasCatalogo)
            .OrderBy(f => f.EhCatalogoOficial ? 1 : 0)
            .ThenBy(f => f.Marca)
            .ThenBy(f => f.Modelo)
            .ToList();
        GradeConflitos.ItemsSource = _local.Estado.Conflitos.ToList();
    }

    private static string normalizar(string texto) => texto.ToLowerInvariant().Trim();

    private void AtualizarStatus(string? mensagemConexao = null)
    {
        var conflitos = _local.Estado.Conflitos.Count;
        var total = _local.Estado.Atendimentos.Count;
        EmailUsuario.Text = string.IsNullOrWhiteSpace(_local.Estado.Email)
            ? "Sessão ativa"
            : _local.Estado.Email;

        if (AbaConflitos is not null)
        {
            if (conflitos > 0)
            {
                AbaConflitos.Visibility = Visibility.Visible;
            }
            else
            {
                AbaConflitos.Visibility = Visibility.Collapsed;
                if (AbasPrincipal.SelectedIndex == 3)
                {
                    AbasPrincipal.SelectedIndex = 0;
                }
            }
        }

        if (StatusDot is not null)
        {
            if (!string.IsNullOrWhiteSpace(mensagemConexao) && mensagemConexao.Contains("Sem conexão"))
            {
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(148, 163, 184));
            }
            else
            {
                StatusDot.Fill = (Brush)FindResource("CorPrimaria");
            }
        }

        if (!string.IsNullOrWhiteSpace(mensagemConexao))
        {
            Status.Text = mensagemConexao;
            return;
        }

        Status.Text = conflitos > 0
            ? $"{total} atendimento(s) · {conflitos} conflito(s) aguardando decisão"
            : $"{total} atendimento(s) sincronizado(s) na nuvem";
    }

    private async Task Sincronizar()
    {
        if (_api is null || string.IsNullOrWhiteSpace(_local.Estado.EmpresaId)) return;
        if (_sincronizando)
        {
            _sincronizarNovamente = true;
            return;
        }
        _sincronizando = true;
        if (StatusDot is not null)
        {
            StatusDot.Fill = new SolidColorBrush(Color.FromRgb(245, 158, 11));
        }
        string? mensagem = null;
        try
        {
            var empresasAtualizadas = (await _api.EmpresasAsync()).ToList();
            if (empresasAtualizadas.Count > 0)
            {
                _empresas = empresasAtualizadas;
                RegistrarEmpresas(_empresas);
                AtualizarInterfaceModalidade();
            }
            var resumo = await MotorSincronia.SincronizarAsync(_local, _api, _local.Estado.EmpresaId);
            await SalvarTokens();
            _ultimaSincronia = DateTimeOffset.UtcNow;
            var total = _local.Estado.Atendimentos.Count;
            if (resumo.Conflitos > 0)
            {
                var detalhe = string.Join(", ", _local.Estado.Conflitos.Select(c =>
                    c.Placa.Length > 0 ? c.Placa : c.Uuid));
                AbaConflitos.Visibility = Visibility.Visible;
                MessageBox.Show($"Conflitos detectados: {detalhe}. Nenhum registro foi descartado.",
                    "ChaveiroCar", MessageBoxButton.OK, MessageBoxImage.Warning);
                AbasPrincipal.SelectedIndex = 3;
                mensagem = $"{total} atendimento(s) · {_local.Estado.Conflitos.Count} conflito(s) aguardando decisão";
            }
            else
            {
                mensagem = $"{total} atendimento(s) · Sincronizado com a nuvem às {DateTime.Now:HH:mm}";
            }
        }
        catch (HttpRequestException ex) when (ex.StatusCode == System.Net.HttpStatusCode.Unauthorized)
        {
            TratarSessaoExpirada();
            return;
        }
        catch
        {
            mensagem = "Sem conexão no momento: alterações salvas neste computador";
        }
        finally
        {
            _sincronizando = false;
            AtualizarInterfaceModalidade();
            RecarregarGrade();
            AtualizarStatus(mensagem);
            if (_sincronizarNovamente && _api is not null && !SaiuDaConta && !TemDialogoModalAberto())
            {
                _sincronizarNovamente = false;
                _ = Sincronizar();
            }
        }
    }

    private void Pesquisar_Click(object sender, RoutedEventArgs e) => RecarregarGrade();
    private void FiltroSegmento_Mudou(object sender, SelectionChangedEventArgs e) => RecarregarGrade();
    private void Busca_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) RecarregarGrade();
    }

    private async void Empresa_Mudou(object sender, SelectionChangedEventArgs e)
    {
        if (Empresas.SelectedItem is ClienteApi.Empresa empresa)
        {
            _local.Estado.EmpresaId = empresa.Id;
            _local.Salvar();
            AtualizarInterfaceModalidade();
            RecarregarGrade();
            await Sincronizar();
        }
    }

    private Atendimento? Selecionado() => Grade.SelectedItem as Atendimento;
    private ArmazenamentoLocal.Ficha? FichaSelecionada() => GradeFichas.SelectedItem as ArmazenamentoLocal.Ficha;

    private void EntrarVeicular_Click(object sender, RoutedEventArgs e) => EntrarNaArea("AUTOMOTIVO", 0);
    private void ConsultarVeicularInicial_Click(object sender, RoutedEventArgs e) => EntrarNaArea("AUTOMOTIVO", 2);
    private void EntrarResidencial_Click(object sender, RoutedEventArgs e) => EntrarNaArea("RESIDENCIAL", 0);
    private void ConsultarResidencialInicial_Click(object sender, RoutedEventArgs e) => EntrarNaArea("RESIDENCIAL", 2);

    private void EntrarNaArea(string seg, int abaInicial)
    {
        if (!ModulosAtivosAtuais().Contains(seg))
        {
            Plano_Click(this, new RoutedEventArgs());
            return;
        }
        _modalidadeAtiva = seg;
        AbasPrincipal.SelectedIndex = abaInicial;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void TrocarArea_Click(object sender, RoutedEventArgs e)
    {
        _modalidadeAtiva = null;
        AtualizarInterfaceModalidade();
    }

    private void Plano_Click(object sender, RoutedEventArgs e)
    {
        if (_api is null || string.IsNullOrWhiteSpace(_local.Estado.EmpresaId))
        {
            Status.Text = "Selecione uma empresa ativa para consultar o plano.";
            return;
        }
        var janela = new PlanoWindow(_api, _local, _local.Estado.EmpresaId) { Owner = this };
        janela.ShowDialog();
        AtualizarInterfaceModalidade();
        RecarregarGrade();
        if (janela.Atualizado)
        {
            _ = Sincronizar();
        }
    }

    private void Grade_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (Selecionado() is not null) Editar_Click(sender, e);
    }

    private void GradeFichas_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (FichaSelecionada() is not null) VerManualFicha_Click(sender, e);
    }

    private void Novo_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var seg = SegmentoSelecionado() == "RESIDENCIAL" ? "RESIDENCIAL" : "AUTOMOTIVO";
        var item = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            Segmento = seg,
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TiposServico = [seg == "RESIDENCIAL" ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA"],
        };
        AbrirEditorAtendimento(item, []);
    }

    private void Editar_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var item = Selecionado();
        if (item is null)
        {
            Status.Text = "Selecione um atendimento na tabela para editar.";
            return;
        }
        var fotos = _local.Estado.Fotos.Where(f => f.AtendimentoUuid == item.Uuid).ToList();
        AbrirEditorAtendimento(item, fotos);
    }

    private void AbrirEditorAtendimento(Atendimento item, IReadOnlyList<ArmazenamentoLocal.FotoLocal> fotos)
    {
        AtendimentoEditorWindow? editor = null;
        bool confirmado = false;
        _editandoModal = true;
        try
        {
            editor = new AtendimentoEditorWindow(
                item,
                fotos,
                segmentoFixo: _modalidadeAtiva is not null,
                aoVerFicha: rascunho => AbrirFichaParaAtendimento(rascunho, (Window?)editor ?? this, permitirAcoes: false))
            {
                Owner = this,
            };
            confirmado = editor.ShowDialog() == true && editor.Resultado is not null;
            if (confirmado && editor.Resultado is not null)
            {
                MotorSincronia.EnfileirarUpsert(_local, editor.Resultado);
                AdicionarFotos(editor.ResultadoFotos);
            }
        }
        finally
        {
            _editandoModal = false;
        }
        if (!confirmado) return;
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void AdicionarFotos(IEnumerable<ArmazenamentoLocal.FotoLocal> fotos)
    {
        foreach (var foto in fotos)
        {
            var indice = _local.Estado.Fotos.FindIndex(atual => atual.Uuid == foto.Uuid);
            if (indice < 0) _local.Estado.Fotos.Add(foto);
            else _local.Estado.Fotos[indice] = foto;
        }
        _local.Salvar();
    }

    private void ReciboWhatsApp_Click(object sender, RoutedEventArgs e)
    {
        var item = Selecionado();
        if (item is null)
        {
            MessageBox.Show("Selecione um atendimento na tabela para gerar o recibo WhatsApp.",
                "Recibo WhatsApp", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        string telefone = new string((item.ClienteTelefone ?? "").Where(char.IsDigit).ToArray());
        if (telefone.Length >= 10 && !telefone.StartsWith("55"))
        {
            telefone = "55" + telefone;
        }

        string titulo = item.Segmento == "RESIDENCIAL"
            ? (string.IsNullOrWhiteSpace(item.AmbientePorta) ? "Serviço Residencial" : $"{item.TipoLocal} · {item.AmbientePorta}")
            : (string.IsNullOrWhiteSpace(item.Placa) ? $"{item.Marca} {item.Modelo}" : $"{item.Placa} · {item.Marca} {item.Modelo}");

        string servicos = item.TiposServico.Count > 0 ? string.Join(", ", item.TiposServico) : "Serviço de chaveiro";
        string data = DateTimeOffset.FromUnixTimeMilliseconds(item.DataServico).ToLocalTime().ToString("dd/MM/yyyy");
        string valor = (item.ValorCentavos / 100m).ToString("C2", CultureInfo.GetCultureInfo("pt-BR"));

        var sb = new StringBuilder();
        sb.AppendLine("*COMPROVANTE DE ATENDIMENTO — CHAVEIROCAR*");
        sb.AppendLine("────────────────────────");
        sb.AppendLine($"*Item:* {titulo}");
        sb.AppendLine($"*Data:* {data}");
        sb.AppendLine($"*Serviço:* {servicos}");
        if (!string.IsNullOrWhiteSpace(item.Defeito)) sb.AppendLine($"*Defeito:* {item.Defeito}");
        if (!string.IsNullOrWhiteSpace(item.Solucao)) sb.AppendLine($"*Solução:* {item.Solucao}");
        sb.AppendLine($"*Valor:* {valor}");
        sb.AppendLine($"*Situação:* {(item.Pago ? "Pago ✅" : "Pendente de pagamento ⏳")}");
        sb.AppendLine("────────────────────────");
        sb.AppendLine("Obrigado pela preferência!");

        string textoFormatado = sb.ToString();
        string url = string.IsNullOrWhiteSpace(telefone)
            ? $"https://web.whatsapp.com/send?text={Uri.EscapeDataString(textoFormatado)}"
            : $"https://web.whatsapp.com/send?phone={telefone}&text={Uri.EscapeDataString(textoFormatado)}";

        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Não foi possível abrir o WhatsApp: {ex.Message}", "ChaveiroCar",
                MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void Excluir_Click(object sender, RoutedEventArgs e)
    {
        var item = Selecionado();
        if (item is null)
        {
            Status.Text = "Selecione um atendimento na tabela para excluir.";
            return;
        }
        var nome = !string.IsNullOrWhiteSpace(item.Placa)
            ? item.Placa
            : (!string.IsNullOrWhiteSpace(item.Modelo) ? item.Modelo : item.TipoLocal);
        if (MessageBox.Show($"Excluir o atendimento \"{nome}\"?", "ChaveiroCar",
                MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
        MotorSincronia.EnfileirarExclusao(_local, item.Uuid, item.Revisao);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void ExportarPdf_Click(object sender, RoutedEventArgs e)
    {
        var atendimento = Selecionado();
        if (atendimento is null)
        {
            Status.Text = "Selecione um atendimento para gerar o relatório em PDF.";
            return;
        }
        var documento = new FlowDocument
        {
            PagePadding = new Thickness(48),
            ColumnWidth = double.PositiveInfinity,
        };
        documento.Blocks.Add(new Paragraph(new Run("ChaveiroCar — Relatório de atendimento"))
        {
            FontSize = 20,
            FontWeight = FontWeights.Bold,
            Margin = new Thickness(0, 0, 0, 18),
        });
        var data = DateTimeOffset.FromUnixTimeMilliseconds(atendimento.DataServico).ToLocalTime();
        AdicionarLinha(documento, "Data", data.ToString("dd/MM/yyyy"));
        AdicionarLinha(documento, "Segmento", atendimento.Segmento == "RESIDENCIAL" ? "Residencial" : "Automotivo");
        if (atendimento.Segmento == "RESIDENCIAL")
        {
            AdicionarLinha(documento, "Tipo de local", atendimento.TipoLocal);
            AdicionarLinha(documento, "Ambiente / porta", atendimento.AmbientePorta);
            AdicionarLinha(documento, "Fechadura", $"{atendimento.MarcaFechadura} {atendimento.ModeloFechadura}".Trim());
            AdicionarLinha(documento, "Cilindro / chave", atendimento.TipoCilindro);
            AdicionarLinha(documento, "Endereço / referência", atendimento.EnderecoLocal);
        }
        else
        {
            AdicionarLinha(documento, "Placa", string.IsNullOrWhiteSpace(atendimento.Placa) ? "Sem placa" : atendimento.Placa);
            AdicionarLinha(documento, "Veículo", $"{atendimento.Marca} {atendimento.Modelo} {atendimento.Ano}".Trim());
            AdicionarLinha(documento, "Chassi", atendimento.Chassi);
        }
        AdicionarLinha(documento, "Cliente", atendimento.ClienteNome);
        AdicionarLinha(documento, "Telefone", atendimento.ClienteTelefone);
        AdicionarLinha(documento, "Serviço", string.Join(", ", atendimento.TiposServico));
        AdicionarLinha(documento, "Defeito", atendimento.Defeito);
        AdicionarLinha(documento, "Solução", atendimento.Solucao);
        AdicionarLinha(documento, "Observações", atendimento.Abertura);
        AdicionarLinha(documento, "Código da chave", atendimento.CodigoChave);
        AdicionarLinha(documento, "Código de configuração", atendimento.CodigoConfiguracao);
        if (atendimento.Segmento != "RESIDENCIAL")
            AdicionarLinha(documento, "Chip / transponder", atendimento.ChipTransponder);
        AdicionarLinha(documento, "Ferramenta", atendimento.Ferramenta);
        AdicionarLinha(documento, "Valor", (atendimento.ValorCentavos / 100m).ToString("C"));
        AdicionarLinha(documento, "Pagamento", atendimento.Pago ? "Pago" : "Pendente");

        var impressora = new PrintDialog();
        if (impressora.ShowDialog() == true)
        {
            var sufixo = !string.IsNullOrWhiteSpace(atendimento.Placa)
                ? atendimento.Placa
                : (!string.IsNullOrWhiteSpace(atendimento.Modelo) ? atendimento.Modelo : atendimento.TipoLocal);
            impressora.PrintDocument(
                ((IDocumentPaginatorSource)documento).DocumentPaginator,
                $"ChaveiroCar - {sufixo}");
        }
    }

    private static void AdicionarLinha(FlowDocument documento, string rotulo, string valor)
    {
        if (string.IsNullOrWhiteSpace(valor)) return;
        var paragrafo = new Paragraph
        {
            Margin = new Thickness(0, 0, 0, 8),
            KeepTogether = true,
        };
        paragrafo.Inlines.Add(new Run(rotulo + ": ") { FontWeight = FontWeights.SemiBold });
        paragrafo.Inlines.Add(new Run(valor));
        documento.Blocks.Add(paragrafo);
    }

    private void NovaFicha_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var seg = string.IsNullOrWhiteSpace(_modalidadeAtiva) ? "AUTOMOTIVO" : _modalidadeAtiva;
        var ficha = new ArmazenamentoLocal.Ficha
        {
            Uuid = Guid.NewGuid().ToString(),
            Segmento = seg,
        };
        AbrirEditorFicha(ficha, [], copiaDeOficial: false);
    }

    private void EditarFicha_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var ficha = FichaSelecionada();
        if (ficha is null)
        {
            Status.Text = "Selecione uma ficha técnica para editar ou complementar.";
            return;
        }
        EditarOuComplementarFicha(ficha);
    }

    private void EditarOuComplementarFicha(ArmazenamentoLocal.Ficha ficha)
    {
        if (!PodeEditarEmpresa()) return;
        ArmazenamentoLocal.Ficha baseEdicao;
        IReadOnlyList<ArmazenamentoLocal.FotoLocal> fotos;
        var copiaDeOficial = ficha.EhCatalogoOficial;
        if (copiaDeOficial)
        {
            baseEdicao = new ArmazenamentoLocal.Ficha
            {
                Uuid = Guid.NewGuid().ToString(),
                Segmento = ficha.Segmento,
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
                Origem = "Anotação da empresa (baseada no catálogo oficial)",
                Revisao = 0,
            };
            fotos = [];
        }
        else
        {
            baseEdicao = ficha;
            fotos = _local.Estado.Fotos.Where(f => f.FichaUuid == ficha.Uuid).ToList();
        }
        AbrirEditorFicha(baseEdicao, fotos, copiaDeOficial);
    }

    private void AbrirEditorFicha(
        ArmazenamentoLocal.Ficha baseEdicao,
        IReadOnlyList<ArmazenamentoLocal.FotoLocal> fotos,
        bool copiaDeOficial)
    {
        bool confirmado = false;
        _editandoModal = true;
        try
        {
            var editor = new FichaEditorWindow(
                baseEdicao,
                fotos,
                segmentoFixo: _modalidadeAtiva is not null,
                copiaDeOficial: copiaDeOficial) { Owner = this };
            confirmado = editor.ShowDialog() == true && editor.Resultado is not null;
            if (confirmado && editor.Resultado is not null)
            {
                MotorSincronia.EnfileirarFicha(_local, editor.Resultado);
                AdicionarFotos(editor.ResultadoFotos);
            }
        }
        finally
        {
            _editandoModal = false;
        }
        if (!confirmado) return;
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void ExcluirFicha_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var ficha = FichaSelecionada();
        if (ficha is null) return;
        if (ficha.EhCatalogoOficial)
        {
            MessageBox.Show(
                "Fichas do catálogo padrão não podem ser excluídas. Use \"Editar / complementar\" para salvar uma anotação própria da empresa.",
                "ChaveiroCar",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            return;
        }
        if (MessageBox.Show($"Excluir ficha {ficha.Marca} {ficha.Modelo}?", "ChaveiroCar",
                MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
        MotorSincronia.EnfileirarExclusao(_local, ficha.Uuid, ficha.Revisao, "ficha");
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void VerManualFicha_Click(object sender, RoutedEventArgs e)
    {
        var ficha = FichaSelecionada();
        if (ficha is null)
        {
            Status.Text = "Selecione uma ficha técnica para visualizar o manual.";
            return;
        }
        MostrarVisualizadorFicha(ficha, this, permitirAcoes: true);
    }

    private void VerFichaAtendimento_Click(object sender, RoutedEventArgs e)
    {
        var item = Selecionado();
        if (item is null)
        {
            Status.Text = "Selecione um atendimento para consultar a ficha técnica relacionada.";
            return;
        }
        AbrirFichaParaAtendimento(item, this, permitirAcoes: true);
    }

    private void NovoServicoDaFicha_Click(object sender, RoutedEventArgs e)
    {
        var ficha = FichaSelecionada();
        if (ficha is null)
        {
            Status.Text = "Selecione uma ficha técnica para iniciar um atendimento.";
            return;
        }
        CriarAtendimentoDeFicha(ficha);
    }

    private void CriarAtendimentoDeFicha(ArmazenamentoLocal.Ficha ficha)
    {
        if (!PodeEditarEmpresa()) return;
        if (!ModulosAtivosAtuais().Contains(ficha.Segmento))
        {
            Plano_Click(this, new RoutedEventArgs());
            return;
        }
        _modalidadeAtiva = ficha.Segmento;
        AtualizarInterfaceModalidade();
        var abertura = string.Join(" · ",
            new[] { ficha.Sistema, ficha.Chip, ficha.Chave }.Where(s => !string.IsNullOrWhiteSpace(s)));
        var item = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            Segmento = ficha.Segmento,
            Marca = ficha.Segmento == "RESIDENCIAL" ? "" : ficha.Marca,
            Modelo = ficha.Segmento == "RESIDENCIAL" ? "" : ficha.Modelo,
            Ano = ficha.Segmento == "RESIDENCIAL" ? "" : ficha.Ano,
            MarcaFechadura = ficha.Segmento == "RESIDENCIAL" ? ficha.Marca : "",
            ModeloFechadura = ficha.Segmento == "RESIDENCIAL" ? ficha.Modelo : "",
            Ferramenta = ficha.Equipamento,
            Abertura = abertura,
            ChipTransponder = ficha.Segmento == "RESIDENCIAL" ? "" : ficha.Chip,
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TiposServico = [ficha.Segmento == "RESIDENCIAL" ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA"],
        };
        AbrirEditorAtendimento(item, []);
    }

    private void AbrirFichaParaAtendimento(Atendimento item, Window owner, bool permitirAcoes)
    {
        var fichas = CatalogoFichasPadrao.BuscarParaAtendimento(
            item,
            _local.Estado.Fichas.Where(f => f.ExcluidoEm is null));
        if (fichas.Count == 1)
        {
            MostrarVisualizadorFicha(fichas[0], owner, permitirAcoes);
            return;
        }
        if (fichas.Count > 1)
        {
            var escolhida = MostrarEscolhaFicha(fichas, owner);
            if (escolhida is not null)
            {
                MostrarVisualizadorFicha(escolhida, owner, permitirAcoes);
            }
            return;
        }

        if (ReferenceEquals(owner, this))
        {
            var termo = string.Join(" ",
                (item.Segmento == "RESIDENCIAL"
                    ? new[] { item.MarcaFechadura, item.ModeloFechadura }
                    : new[] { item.Marca, item.Modelo })
                .Where(s => !string.IsNullOrWhiteSpace(s)));
            Busca.Text = termo;
            AbasPrincipal.SelectedIndex = 2;
            RecarregarGrade();
            Status.Text = "Nenhuma ficha exata encontrada; consulta técnica aberta com a busca.";
        }
        else
        {
            MessageBox.Show(
                owner,
                "Nenhuma ficha exata encontrada no catálogo para este veículo/fechadura. Salve ou volte para consultar na aba Fichas Técnicas.",
                "ChaveiroCar",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
        }
    }

    private static ArmazenamentoLocal.Ficha? MostrarEscolhaFicha(
        List<ArmazenamentoLocal.Ficha> opcoes,
        Window owner)
    {
        ArmazenamentoLocal.Ficha? escolhida = null;
        var dialogo = new Window
        {
            Title = "Escolha a ficha técnica",
            Width = 540,
            Height = 360,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Owner = owner,
            Background = (Brush)Application.Current.FindResource("CorFundoJanela"),
        };
        var list = new ListBox
        {
            ItemsSource = opcoes.Select(f =>
                new KeyValuePair<ArmazenamentoLocal.Ficha, string>(
                    f,
                    $"{f.Marca} {f.Modelo} {f.Ano} — {f.Equipamento} ({f.OrigemFormatada})".Trim())).ToList(),
            DisplayMemberPath = "Value",
            Margin = new Thickness(0, 0, 0, 12),
        };
        list.MouseDoubleClick += (_, _) =>
        {
            if (list.SelectedItem is KeyValuePair<ArmazenamentoLocal.Ficha, string> par)
            {
                escolhida = par.Key;
                dialogo.DialogResult = true;
            }
        };
        var btnAbrir = new Button
        {
            Content = "Abrir ficha selecionada",
            Style = (Style)Application.Current.FindResource("BotaoPrimario"),
            Width = 170,
        };
        btnAbrir.Click += (_, _) =>
        {
            if (list.SelectedItem is KeyValuePair<ArmazenamentoLocal.Ficha, string> par)
            {
                escolhida = par.Key;
                dialogo.DialogResult = true;
            }
        };
        var root = new DockPanel { Margin = new Thickness(16) };
        DockPanel.SetDock(btnAbrir, Dock.Bottom);
        btnAbrir.HorizontalAlignment = HorizontalAlignment.Right;
        root.Children.Add(btnAbrir);
        root.Children.Add(list);
        dialogo.Content = root;
        if (list.Items.Count > 0) list.SelectedIndex = 0;
        return dialogo.ShowDialog() == true ? escolhida : null;
    }

    private void MostrarVisualizadorFicha(
        ArmazenamentoLocal.Ficha ficha,
        Window owner,
        bool permitirAcoes)
    {
        var dialogo = new Window
        {
            Title = $"Ficha Técnica — {ficha.Marca} {ficha.Modelo}".Trim(),
            Width = 720,
            Height = 640,
            MinWidth = 560,
            MinHeight = 460,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Owner = owner,
            Background = (Brush)Application.Current.FindResource("CorFundoJanela"),
        };

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var header = new Border
        {
            Background = (Brush)Application.Current.FindResource("CorCabecalho"),
            Padding = new Thickness(20, 14, 20, 14),
        };
        var headerStack = new StackPanel();
        headerStack.Children.Add(new TextBlock
        {
            Text = $"{ficha.Marca} {ficha.Modelo} {ficha.Ano}".Trim(),
            Foreground = Brushes.White,
            FontSize = 18,
            FontWeight = FontWeights.Bold,
        });
        headerStack.Children.Add(new TextBlock
        {
            Text = $"{ficha.SegmentoFormatado} · {ficha.OrigemFormatada} · {ficha.Equipamento}".Trim(' ', '·'),
            Foreground = new SolidColorBrush(Color.FromRgb(94, 234, 212)),
            FontSize = 12,
            Margin = new Thickness(0, 2, 0, 0),
        });
        header.Child = headerStack;
        Grid.SetRow(header, 0);
        root.Children.Add(header);

        var scroll = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            Margin = new Thickness(16),
        };
        var corpo = new StackPanel();
        void AddCampo(string rotulo, string valor, bool monoespaco = false)
        {
            if (string.IsNullOrWhiteSpace(valor)) return;
            corpo.Children.Add(new TextBlock
            {
                Text = rotulo,
                FontWeight = FontWeights.SemiBold,
                Foreground = (Brush)Application.Current.FindResource("CorTextoSecundario"),
                FontSize = 12,
                Margin = new Thickness(0, 6, 0, 2),
            });
            corpo.Children.Add(new TextBox
            {
                Text = valor,
                IsReadOnly = true,
                TextWrapping = TextWrapping.Wrap,
                AcceptsReturn = true,
                VerticalContentAlignment = VerticalAlignment.Top,
                FontFamily = monoespaco ? new FontFamily("Consolas") : new FontFamily("Segoe UI"),
            });
        }
        AddCampo("Sistema / Módulo", ficha.Sistema);
        AddCampo("Chip / Transponder", ficha.Chip, monoespaco: true);
        AddCampo("Chave / Lâmina", ficha.Chave);
        AddCampo("Telecomando", ficha.Telecomando);
        AddCampo("Frequência", ficha.Frequencia);
        AddCampo("Descrição", ficha.Descricao);
        AddCampo("Funções documentadas", ficha.Funcoes);
        AddCampo("Avisos importantes", ficha.Avisos);
        AddCampo("Manual / procedimento (disponível offline)", ficha.Manual);
        AddCampo("Aviso de consulta", "Ficha apenas informativa: não executa operações na central nem altera atendimentos existentes.");

        scroll.Content = new Border
        {
            Background = (Brush)Application.Current.FindResource("CorSuperficie"),
            BorderBrush = (Brush)Application.Current.FindResource("CorBorda"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(18, 12, 18, 16),
            Child = corpo,
        };
        Grid.SetRow(scroll, 1);
        root.Children.Add(scroll);

        var footer = new Border
        {
            Background = (Brush)Application.Current.FindResource("CorSuperficie"),
            BorderBrush = (Brush)Application.Current.FindResource("CorBorda"),
            BorderThickness = new Thickness(0, 1, 0, 0),
            Padding = new Thickness(18, 12, 18, 12),
        };
        var footerDock = new DockPanel { LastChildFill = false };
        var acoesEsq = new StackPanel { Orientation = Orientation.Horizontal };
        if (!string.IsNullOrWhiteSpace(ficha.Manual))
        {
            var btnCopiar = new Button { Content = "Copiar manual", Margin = new Thickness(0, 0, 8, 0) };
            btnCopiar.Click += (_, _) => Clipboard.SetText(ficha.Manual);
            acoesEsq.Children.Add(btnCopiar);
        }
        if (permitirAcoes && PodeEditarEmpresa())
        {
            var btnEditar = new Button
            {
                Content = ficha.EhCatalogoOficial ? "Complementar para a empresa" : "Editar ficha",
                Margin = new Thickness(0, 0, 8, 0),
            };
            btnEditar.Click += (_, _) =>
            {
                dialogo.Close();
                EditarOuComplementarFicha(ficha);
            };
            acoesEsq.Children.Add(btnEditar);

            var btnCriar = new Button
            {
                Content = "+ Novo atendimento com esta ficha",
                Style = (Style)Application.Current.FindResource("BotaoPrimario"),
            };
            btnCriar.Click += (_, _) =>
            {
                dialogo.Close();
                CriarAtendimentoDeFicha(ficha);
            };
            acoesEsq.Children.Add(btnCriar);
        }
        DockPanel.SetDock(acoesEsq, Dock.Left);
        footerDock.Children.Add(acoesEsq);

        var btnFechar = new Button { Content = "Fechar", IsCancel = true, Width = 100 };
        DockPanel.SetDock(btnFechar, Dock.Right);
        footerDock.Children.Add(btnFechar);

        footer.Child = footerDock;
        Grid.SetRow(footer, 2);
        root.Children.Add(footer);
        dialogo.Content = root;
        dialogo.ShowDialog();
    }

    private void Equipe_Click(object sender, RoutedEventArgs e)
    {
        if (_api is null || Empresas.SelectedItem is not ClienteApi.Empresa empresa)
        {
            Status.Text = "Selecione uma empresa ativa para gerenciar a equipe.";
            return;
        }
        var podeGerenciar = empresa.Papel is "proprietario" or "administrador" or "dono" or "admin";
        new EquipeWindow(_api, empresa.Id, podeGerenciar) { Owner = this }.ShowDialog();
        _ = SalvarTokens();
    }

    private void ManterConflito_Click(object sender, RoutedEventArgs e)
    {
        if (GradeConflitos.SelectedItem is not ArmazenamentoLocal.Conflito conflito) return;
        try
        {
            MotorSincronia.ManterLocal(_local, conflito);
            RecarregarGrade();
            AtualizarStatus();
            _ = Sincronizar();
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível manter a versão deste PC: {ex.Message}";
        }
    }

    private async void AceitarConflito_Click(object sender, RoutedEventArgs e)
    {
        if (GradeConflitos.SelectedItem is not ArmazenamentoLocal.Conflito conflito || _api is null) return;
        try
        {
            await MotorSincronia.AceitarServidor(_local, _api, _local.Estado.EmpresaId, conflito);
            await SalvarTokens();
            RecarregarGrade();
            AtualizarStatus();
        }
        catch (Exception ex)
        {
            Status.Text = $"Não foi possível aceitar a versão do servidor: {ex.Message}";
        }
    }

    private async void Sair_Click(object sender, RoutedEventArgs e)
    {
        if (_api is not null)
        {
            try { await _api.SairAsync(); } catch { }
        }
        _local.Estado.Acesso = null;
        _local.Estado.Refresh = null;
        _local.Salvar();
        SaiuDaConta = true;
        Close();
    }
}
