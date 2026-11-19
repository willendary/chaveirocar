using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class MainWindow : Window
{
    private enum SecaoAtiva { Inicio, Atendimentos, Fichario, Fichas, Conflitos }

    private sealed record ItemFicharioVeiculo(
        string Marca,
        string Modelo,
        string Ano,
        int Servicos,
        string Ultimo,
        string Abertura,
        string CodigoChave,
        string Chip);

    private sealed record ItemFicharioResidencial(
        string TipoLocal,
        string Ambiente,
        string MarcaFechadura,
        string ModeloFechadura,
        string Cilindro,
        int Servicos,
        string Ultimo,
        string Abertura,
        string CodigoChave);

    private readonly ArmazenamentoLocal _local;
    private readonly DispatcherTimer _timerSincronia = new() { Interval = TimeSpan.FromSeconds(20) };
    private ClienteApi? _api;
    private List<ClienteApi.Empresa> _empresas = [];
    private string? _modalidadeAtiva;
    private SecaoAtiva _secaoAtiva = SecaoAtiva.Inicio;
    private string _filtroPagamento = "TODOS"; // TODOS | PAGOS | PENDENTES
    private bool _sincronizando;
    private bool _sincronizarNovamente;
    private bool _editandoModal;
    private bool _inicializandoEmpresas;
    private bool _sessaoExpiradaNotificada;
    private DateTimeOffset _ultimaSincronia = DateTimeOffset.MinValue;
    private AtualizacaoDisponivel? _atualizacaoPendente;

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
        IconeApp.Aplicar(this);
        var logo = IconeApp.ObterLogoSimbolo();
        if (logo is not null)
        {
            LogoCabecalho.Source = logo;
        }
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

        var versaoAtual = System.Reflection.Assembly.GetEntryAssembly()?.GetName().Version?.ToString(3) ?? "1.15.0";
        TxtVersaoApp.Text = $"Desktop v{versaoAtual}";
        AtualizarInterfaceModalidade();
        RecarregarGrade();
        AtualizarStatus();
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

    private async Task VerificarAtualizacao(bool interativo = false)
    {
        var atualizacao = await VerificadorAtualizacao.VerificarAsync();
        _atualizacaoPendente = atualizacao;
        if (atualizacao is null)
        {
            BtnAtualizacaoDisponivel.Visibility = Visibility.Collapsed;
            PontoAtualizacaoSidebar.Visibility = Visibility.Collapsed;
            if (interativo)
            {
                MessageBox.Show("Você já está utilizando a versão mais recente do ChaveiroCar.",
                    "Atualizações", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            return;
        }

        BtnAtualizacaoDisponivel.Content = $"Atualizar para v{atualizacao.Versao}";
        BtnAtualizacaoDisponivel.Visibility = Visibility.Visible;
        PontoAtualizacaoSidebar.Visibility = Visibility.Visible;

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
            _inicializandoEmpresas = true;
            try
            {
                Empresas.ItemsSource = _empresas;
                Empresas.DisplayMemberPath = "Nome";
                var atual = _empresas.FirstOrDefault(x => x.Id == _local.Estado.EmpresaId) ?? _empresas.FirstOrDefault();
                if (atual is not null)
                {
                    Empresas.SelectedItem = atual;
                    _local.Estado.EmpresaId = atual.Id;
                    _local.Salvar();
                }
            }
            finally
            {
                _inicializandoEmpresas = false;
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
            TxtStatusNuvem.Text = "Modo offline";
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

    private string GarantirModalidadeAtiva(string? preferida = null)
    {
        var modulos = ModulosAtivosAtuais();
        if (!string.IsNullOrWhiteSpace(preferida) && modulos.Contains(preferida))
        {
            _modalidadeAtiva = preferida;
            return preferida;
        }
        if (_modalidadeAtiva is not null && modulos.Contains(_modalidadeAtiva))
        {
            return _modalidadeAtiva;
        }
        if (modulos.Contains("AUTOMOTIVO"))
        {
            _modalidadeAtiva = "AUTOMOTIVO";
            return "AUTOMOTIVO";
        }
        if (modulos.Contains("RESIDENCIAL"))
        {
            _modalidadeAtiva = "RESIDENCIAL";
            return "RESIDENCIAL";
        }
        return "";
    }

    private void AtualizarInterfaceModalidade()
    {
        if (PainelEscolhaModalidade is null || PainelAtendimentos is null || PainelFichario is null || PainelFichas is null)
            return;

        var modulos = ModulosAtivosAtuais();
        var veicAtivo = modulos.Contains("AUTOMOTIVO");
        var resAtivo = modulos.Contains("RESIDENCIAL");

        if (_modalidadeAtiva is not null && !modulos.Contains(_modalidadeAtiva))
        {
            _modalidadeAtiva = null;
            _secaoAtiva = SecaoAtiva.Inicio;
        }

        if (ResumoPlanoEscolha is not null)
        {
            var status = _local.Estado.StatusAssinaturaDaEmpresa(_local.Estado.EmpresaId);
            var ativosTxt = modulos.Count == 0
                ? "Nenhum módulo ativo"
                : string.Join(" + ", modulos.Select(m => m == "RESIDENCIAL" ? "Residencial" : "Veicular"));
            ResumoPlanoEscolha.Text = status == "ATIVA" ? $"Plano ativo · {ativosTxt}" : $"{status} · {ativosTxt}";
        }

        AtualizarCartaoModalidade(
            veicAtivo, BadgeVeicularEscolha, TxtBadgeVeicularEscolha,
            BtnEntrarVeicular, BtnConsultaVeicular,
            "Entrar em Veicular", "Contratar / Ativar Veicular");
        AtualizarCartaoModalidade(
            resAtivo, BadgeResidencialEscolha, TxtBadgeResidencialEscolha,
            BtnEntrarResidencial, BtnConsultaResidencial,
            "Entrar em Residencial", "Contratar / Ativar Residencial");

        // Alternador rápido na Sidebar
        var estiloSidebar = (Style)FindResource("BotaoSidebar");
        var estiloSidebarAtivo = (Style)FindResource("BotaoSidebarAtivo");
        BtnAreaVeicular.Style = _modalidadeAtiva == "AUTOMOTIVO" ? estiloSidebarAtivo : estiloSidebar;
        BtnAreaResidencial.Style = _modalidadeAtiva == "RESIDENCIAL" ? estiloSidebarAtivo : estiloSidebar;
        BtnAreaVeicular.Opacity = veicAtivo ? 1.0 : 0.45;
        BtnAreaResidencial.Opacity = resAtivo ? 1.0 : 0.45;

        // Navegação ativa
        BtnNavInicio.Style = _secaoAtiva == SecaoAtiva.Inicio ? estiloSidebarAtivo : estiloSidebar;
        BtnNavAtendimentos.Style = _secaoAtiva == SecaoAtiva.Atendimentos ? estiloSidebarAtivo : estiloSidebar;
        BtnNavFichario.Style = _secaoAtiva == SecaoAtiva.Fichario ? estiloSidebarAtivo : estiloSidebar;
        BtnNavFichas.Style = _secaoAtiva == SecaoAtiva.Fichas ? estiloSidebarAtivo : estiloSidebar;
        BtnNavConflitos.Style = _secaoAtiva == SecaoAtiva.Conflitos ? estiloSidebarAtivo : estiloSidebar;

        // Visibilidade dos painéis
        PainelEscolhaModalidade.Visibility = _secaoAtiva == SecaoAtiva.Inicio ? Visibility.Visible : Visibility.Collapsed;
        PainelAtendimentos.Visibility = _secaoAtiva == SecaoAtiva.Atendimentos ? Visibility.Visible : Visibility.Collapsed;
        PainelFichario.Visibility = _secaoAtiva == SecaoAtiva.Fichario ? Visibility.Visible : Visibility.Collapsed;
        PainelFichas.Visibility = _secaoAtiva == SecaoAtiva.Fichas ? Visibility.Visible : Visibility.Collapsed;
        PainelConflitos.Visibility = _secaoAtiva == SecaoAtiva.Conflitos ? Visibility.Visible : Visibility.Collapsed;

        // Selo da área e cabeçalho contextual
        var emVeicular = _modalidadeAtiva != "RESIDENCIAL";
        SeloModalidadeAtiva.Visibility = _secaoAtiva != SecaoAtiva.Inicio && _modalidadeAtiva is not null
            ? Visibility.Visible
            : Visibility.Collapsed;
        TxtModalidadeAtiva.Text = emVeicular ? "Área Veicular" : "Área Residencial";

        switch (_secaoAtiva)
        {
            case SecaoAtiva.Inicio:
                TxtTituloPagina.Text = "Painel Inicial";
                TxtSubtituloPagina.Text = "Visão geral da operação e acesso rápido às modalidades.";
                BtnAcaoPrincipalTopo.Content = "+ Novo atendimento";
                BtnAcaoPrincipalTopo.Visibility = modulos.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
                break;
            case SecaoAtiva.Atendimentos:
                TxtTituloPagina.Text = "Atendimentos";
                TxtSubtituloPagina.Text = emVeicular
                    ? "Histórico veicular, placas, códigos de chave, chips, recibos e PDF."
                    : "Histórico residencial, imóveis, fechaduras, cilindros, recibos e PDF.";
                BtnAcaoPrincipalTopo.Content = "+ Novo atendimento";
                BtnAcaoPrincipalTopo.Visibility = Visibility.Visible;
                break;
            case SecaoAtiva.Fichario:
                TxtTituloPagina.Text = "Fichário Inteligente";
                TxtSubtituloPagina.Text = emVeicular
                    ? "Memória técnica consolidada por marca, modelo e ano."
                    : "Memória técnica consolidada por tipo de local, porta e fechadura.";
                BtnAcaoPrincipalTopo.Content = "+ Novo atendimento";
                BtnAcaoPrincipalTopo.Visibility = Visibility.Visible;
                break;
            case SecaoAtiva.Fichas:
                TxtTituloPagina.Text = "Fichas Técnicas e Manuais";
                TxtSubtituloPagina.Text = "Base técnica da empresa e catálogo padrão disponíveis offline.";
                BtnAcaoPrincipalTopo.Content = "+ Nova ficha técnica";
                BtnAcaoPrincipalTopo.Visibility = Visibility.Visible;
                break;
            case SecaoAtiva.Conflitos:
                TxtTituloPagina.Text = "Conflitos de Sincronia";
                TxtSubtituloPagina.Text = "Resolva divergências entre este computador e o servidor sem perder dados.";
                BtnAcaoPrincipalTopo.Visibility = Visibility.Collapsed;
                break;
        }

        // Colunas e tabelas por segmento
        ColPlacaOuFechadura.Header = emVeicular ? "Placa" : "Fechadura";
        ColVeiculoOuLocal.Header = emVeicular ? "Veículo" : "Imóvel / Ambiente";
        GradeFicharioVeiculos.Visibility = emVeicular ? Visibility.Visible : Visibility.Collapsed;
        GradeFicharioResidencial.Visibility = emVeicular ? Visibility.Collapsed : Visibility.Visible;

        var podeEditar = PodeEditarEmpresa();
        BtnAcaoPrincipalTopo.IsEnabled = podeEditar;
        BtnNovoAtendimento.IsEnabled = podeEditar;
        BtnEditarAtendimento.IsEnabled = podeEditar;
        BtnRepetirAtendimento.IsEnabled = podeEditar;
        BtnExcluirAtendimento.IsEnabled = podeEditar;
        BtnNovaFicha.IsEnabled = podeEditar;
        BtnEditarFicha.IsEnabled = podeEditar;
        BtnNovoAtendimentoFicha.IsEnabled = podeEditar;
        BtnExcluirFicha.IsEnabled = podeEditar;
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

    private string SegmentoSelecionado() => _modalidadeAtiva ?? "";

    private long? InicioPeriodoSelecionado()
    {
        var tag = (FiltroPeriodo?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "TODOS";
        var agora = DateTimeOffset.Now;
        return tag switch
        {
            "30" => agora.AddDays(-30).ToUnixTimeMilliseconds(),
            "90" => agora.AddDays(-90).ToUnixTimeMilliseconds(),
            "ANO" => new DateTimeOffset(agora.Year, 1, 1, 0, 0, 0, agora.Offset).ToUnixTimeMilliseconds(),
            _ => null,
        };
    }

    private void RecarregarGrade()
    {
        if (Grade is null || Busca is null || GradeFicharioVeiculos is null ||
            GradeFicharioResidencial is null || GradeFichas is null || GradeConflitos is null) return;

        var ativos = _local.Estado.Atendimentos.Where(a => a.ExcluidoEm is null).ToList();
        var seg = SegmentoSelecionado();

        // KPIs globais do painel inicial
        var totalVeicular = ativos.Count(a => a.Segmento != "RESIDENCIAL");
        var totalResidencial = ativos.Count(a => a.Segmento == "RESIDENCIAL");
        var pendentesLista = ativos.Where(a => !a.Pago).ToList();
        var totalValorPendente = pendentesLista.Sum(a => a.ValorCentavos);
        var fichasEmpresaTodas = _local.Estado.Fichas.Where(f => f.ExcluidoEm is null).ToList();
        var fichasCatalogoTodas = CatalogoFichasPadrao.Listar(null, "");

        TxtKpiVeicular.Text = totalVeicular.ToString();
        TxtKpiResidencial.Text = totalResidencial.ToString();
        TxtKpiFichas.Text = (fichasEmpresaTodas.Count + fichasCatalogoTodas.Count).ToString();
        TxtKpiPendentes.Text = pendentesLista.Count.ToString();
        TxtKpiPendentesValor.Text = $"{(totalValorPendente / 100m).ToString("C2", CultureInfo.GetCultureInfo("pt-BR"))} a receber";

        // Atendimentos filtrados
        var selecionadoAnteriorUuid = Selecionado()?.Uuid;
        var termo = Normalizar(Busca.Text);
        var desde = InicioPeriodoSelecionado();

        var itens = ativos
            .Where(a =>
                (string.IsNullOrEmpty(seg) || a.Segmento == seg) &&
                (!desde.HasValue || a.DataServico >= desde.Value) &&
                (_filtroPagamento == "TODOS" || (_filtroPagamento == "PAGOS" ? a.Pago : !a.Pago)) &&
                (termo.Length == 0 || Normalizar(
                    $"{a.Placa} {a.Marca} {a.Modelo} {a.Ano} {a.Chassi} {a.Defeito} {a.Solucao} {a.Abertura} " +
                    $"{a.CodigoChave} {a.CodigoConfiguracao} {a.ChipTransponder} {a.Ferramenta} {a.TiposFormatados} " +
                    $"{a.TipoLocal} {a.AmbientePorta} {a.MarcaFechadura} {a.ModeloFechadura} {a.TipoCilindro} {a.EnderecoLocal} " +
                    $"{a.ClienteNome} {a.ClienteTelefone}").Contains(termo)))
            .OrderByDescending(a => a.DataServico)
            .ToList();

        Grade.ItemsSource = itens;
        TxtContadorLista.Text = itens.Count == 1 ? "1 registro" : $"{itens.Count} registros";
        PainelVazioAtendimentos.Visibility = itens.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        TxtVazioAtendimentosSub.Text = termo.Length > 0 || _filtroPagamento != "TODOS" || desde.HasValue
            ? "Nenhum registro corresponde aos filtros atuais. Tente limpar a busca ou mudar o período."
            : "Registre o primeiro atendimento desta área para começar sua memória técnica.";

        if (!string.IsNullOrWhiteSpace(selecionadoAnteriorUuid))
        {
            var reencontrado = itens.FirstOrDefault(x => x.Uuid == selecionadoAnteriorUuid);
            if (reencontrado is not null) Grade.SelectedItem = reencontrado;
        }
        AtualizarPainelDetalhe();

        // Fichário (somente atendimentos ativos — exclui soft-deleted!)
        var termoFichario = Normalizar(BuscaFichario?.Text ?? "");
        var ficharioVeic = ativos
            .Where(a => a.Segmento != "RESIDENCIAL" && (!string.IsNullOrWhiteSpace(a.Marca) || !string.IsNullOrWhiteSpace(a.Modelo)))
            .GroupBy(a => new { Marca = a.Marca.Trim(), Modelo = a.Modelo.Trim(), Ano = a.Ano.Trim() })
            .Select(g => new ItemFicharioVeiculo(
                g.Key.Marca,
                g.Key.Modelo,
                g.Key.Ano,
                g.Count(),
                DateTimeOffset.FromUnixTimeMilliseconds(g.Max(x => x.DataServico)).ToLocalTime().ToString("dd/MM/yyyy"),
                g.Select(x => x.Abertura).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
                g.Select(x => x.CodigoChave).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
                g.Select(x => x.ChipTransponder).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? ""))
            .Where(f => termoFichario.Length == 0 || Normalizar($"{f.Marca} {f.Modelo} {f.Ano} {f.CodigoChave} {f.Chip} {f.Abertura}").Contains(termoFichario))
            .OrderByDescending(f => f.Servicos)
            .ToList();
        GradeFicharioVeiculos.ItemsSource = ficharioVeic;

        var ficharioRes = ativos
            .Where(a => a.Segmento == "RESIDENCIAL")
            .GroupBy(a => new
            {
                TipoLocal = a.TipoLocal.Trim(),
                Ambiente = a.AmbientePorta.Trim(),
                MarcaFechadura = a.MarcaFechadura.Trim(),
                ModeloFechadura = a.ModeloFechadura.Trim(),
                Cilindro = a.TipoCilindro.Trim(),
            })
            .Select(g => new ItemFicharioResidencial(
                g.Key.TipoLocal,
                g.Key.Ambiente,
                g.Key.MarcaFechadura,
                g.Key.ModeloFechadura,
                g.Key.Cilindro,
                g.Count(),
                DateTimeOffset.FromUnixTimeMilliseconds(g.Max(x => x.DataServico)).ToLocalTime().ToString("dd/MM/yyyy"),
                g.Select(x => x.Abertura).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? "",
                g.Select(x => x.CodigoChave).FirstOrDefault(x => !string.IsNullOrWhiteSpace(x)) ?? ""))
            .Where(f => termoFichario.Length == 0 || Normalizar($"{f.TipoLocal} {f.Ambiente} {f.MarcaFechadura} {f.ModeloFechadura} {f.Cilindro} {f.CodigoChave} {f.Abertura}").Contains(termoFichario))
            .OrderByDescending(f => f.Servicos)
            .ToList();
        GradeFicharioResidencial.ItemsSource = ficharioRes;

        // Fichas Técnicas
        var termoFichas = Normalizar(BuscaFichas?.Text ?? "");
        var fichasEmpresa = fichasEmpresaTodas
            .Where(f =>
                (string.IsNullOrEmpty(seg) || string.Equals(f.Segmento, seg, StringComparison.OrdinalIgnoreCase)) &&
                (termoFichas.Length == 0 ||
                 Normalizar($"{f.Equipamento} {f.Marca} {f.Modelo} {f.Ano} {f.Sistema} {f.Chip} {f.Chave} {f.Manual}").Contains(termoFichas)));
        var fichasCatalogo = CatalogoFichasPadrao.Listar(string.IsNullOrEmpty(seg) ? null : seg, BuscaFichas?.Text ?? "");
        var listaFichas = fichasEmpresa
            .Concat(fichasCatalogo)
            .OrderBy(f => f.EhCatalogoOficial ? 1 : 0)
            .ThenBy(f => f.Marca)
            .ThenBy(f => f.Modelo)
            .ToList();
        GradeFichas.ItemsSource = listaFichas;

        // Badges da Sidebar
        var qtdArea = string.IsNullOrEmpty(seg) ? ativos.Count : ativos.Count(a => a.Segmento == seg);
        TxtBadgeAtendimentos.Text = qtdArea.ToString();
        TxtBadgeFichario.Text = (seg == "RESIDENCIAL" ? ficharioRes.Count : ficharioVeic.Count).ToString();
        TxtBadgeFichas.Text = listaFichas.Count.ToString();

        GradeConflitos.ItemsSource = _local.Estado.Conflitos.ToList();
    }

    private void AtualizarPainelDetalhe()
    {
        var item = Selecionado();
        if (item is null)
        {
            PainelDetalheVazio.Visibility = Visibility.Visible;
            PainelDetalheConteudo.Visibility = Visibility.Collapsed;
            return;
        }

        PainelDetalheVazio.Visibility = Visibility.Collapsed;
        PainelDetalheConteudo.Visibility = Visibility.Visible;

        var residencial = item.Segmento == "RESIDENCIAL";
        TxtDetalheTitulo.Text = residencial
            ? (string.IsNullOrWhiteSpace(item.TipoLocal) ? "Serviço residencial" : $"{item.TipoLocal}{(string.IsNullOrWhiteSpace(item.AmbientePorta) ? "" : " · " + item.AmbientePorta)}")
            : (string.IsNullOrWhiteSpace(item.Placa) ? item.IdentificacaoPrincipal : item.Placa);
        TxtDetalheTitulo.FontFamily = !residencial && !string.IsNullOrWhiteSpace(item.Placa)
            ? new FontFamily("Consolas")
            : new FontFamily("Segoe UI");

        TxtDetalheSubtitulo.Text = residencial
            ? string.Join(" · ", new[] { item.MarcaFechadura, item.ModeloFechadura, item.TipoCilindro }.Where(s => !string.IsNullOrWhiteSpace(s)))
            : string.Join(" ", new[] { item.Marca, item.Modelo, item.Ano }.Where(s => !string.IsNullOrWhiteSpace(s)));
        if (string.IsNullOrWhiteSpace(TxtDetalheSubtitulo.Text))
            TxtDetalheSubtitulo.Text = residencial ? "Chaveiro residencial" : "Veículo sem detalhes adicionais";

        TxtDetalheData.Text = $"{item.SegmentoFormatado} · {item.DataFormatada}";
        TxtDetalheValor.Text = item.ValorFormatado;
        TxtDetalhePago.Text = item.Pago ? "Pago" : "A receber";
        BadgeDetalhePago.Background = item.Pago
            ? new SolidColorBrush(Color.FromRgb(19, 78, 74))
            : new SolidColorBrush(Color.FromRgb(120, 53, 15));
        TxtDetalhePago.Foreground = item.Pago
            ? new SolidColorBrush(Color.FromRgb(94, 234, 212))
            : new SolidColorBrush(Color.FromRgb(253, 230, 138));

        StackDetalheCampos.Children.Clear();

        void AddSecao(string titulo, string valor, bool copiavel = false, bool mono = false)
        {
            if (string.IsNullOrWhiteSpace(valor)) return;
            var bloco = new Border
            {
                Background = copiavel ? new SolidColorBrush(Color.FromRgb(248, 250, 252)) : Brushes.Transparent,
                BorderBrush = copiavel ? new SolidColorBrush(Color.FromRgb(226, 232, 240)) : Brushes.Transparent,
                BorderThickness = copiavel ? new Thickness(1) : new Thickness(0),
                CornerRadius = new CornerRadius(8),
                Padding = copiavel ? new Thickness(10, 8, 10, 8) : new Thickness(0, 0, 0, 2),
                Margin = new Thickness(0, 0, 0, 10),
            };
            var dock = new DockPanel { LastChildFill = true };
            if (copiavel)
            {
                var btnCopiar = new Button
                {
                    Content = "Copiar",
                    Style = (Style)FindResource("BotaoCompacto"),
                    Margin = new Thickness(8, 0, 0, 0),
                    VerticalAlignment = VerticalAlignment.Center,
                };
                btnCopiar.Click += (_, _) =>
                {
                    try
                    {
                        Clipboard.SetText(valor);
                        Status.Text = $"{titulo} copiado para a área de transferência.";
                    }
                    catch { }
                };
                DockPanel.SetDock(btnCopiar, Dock.Right);
                dock.Children.Add(btnCopiar);
            }
            var stack = new StackPanel();
            stack.Children.Add(new TextBlock
            {
                Text = titulo.ToUpperInvariant(),
                FontSize = 10.5,
                FontWeight = FontWeights.Bold,
                Foreground = (Brush)FindResource("CorTextoMudo"),
                Margin = new Thickness(0, 0, 0, 2),
            });
            stack.Children.Add(new TextBlock
            {
                Text = valor,
                FontSize = mono ? 14 : 13,
                FontWeight = mono ? FontWeights.Bold : FontWeights.Normal,
                FontFamily = mono ? new FontFamily("Consolas") : new FontFamily("Segoe UI"),
                Foreground = (Brush)FindResource("CorTextoPrimario"),
                TextWrapping = TextWrapping.Wrap,
            });
            dock.Children.Add(stack);
            bloco.Child = dock;
            StackDetalheCampos.Children.Add(bloco);
        }

        AddSecao("Serviços realizados", item.TiposFormatados);
        if (!residencial && !string.IsNullOrWhiteSpace(item.Placa))
            AddSecao("Placa", item.Placa, copiavel: true, mono: true);
        AddSecao("Código da chave", item.CodigoChave, copiavel: true, mono: true);
        AddSecao(residencial ? "Senha / configuração" : "Código de configuração", item.CodigoConfiguracao, copiavel: true, mono: true);
        if (!residencial) AddSecao("Chip / transponder", item.ChipTransponder, copiavel: true, mono: true);
        if (!residencial) AddSecao("Chassi", item.Chassi, copiavel: true, mono: true);
        if (residencial) AddSecao("Endereço / referência", item.EnderecoLocal);
        AddSecao("Defeito relatado", item.Defeito);
        AddSecao("Solução aplicada", item.Solucao);
        AddSecao("Abertura / procedimento", item.Abertura);
        AddSecao("Ferramenta utilizada", item.Ferramenta);

        var clienteInfo = string.Join(" · ", new[] { item.ClienteNome, item.ClienteTelefone }.Where(s => !string.IsNullOrWhiteSpace(s)));
        AddSecao("Cliente", clienteInfo);

        var fotos = _local.Estado.Fotos.Where(f => f.AtendimentoUuid == item.Uuid).ToList();
        if (fotos.Count > 0)
        {
            var cardFotos = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(240, 253, 250)),
                BorderBrush = new SolidColorBrush(Color.FromRgb(153, 246, 228)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(10, 8, 10, 8),
                Margin = new Thickness(0, 2, 0, 6),
            };
            var dock = new DockPanel { LastChildFill = true };
            var btnAbrir = new Button
            {
                Content = "Ver fotos",
                Style = (Style)FindResource("BotaoCompacto"),
                VerticalAlignment = VerticalAlignment.Center,
            };
            btnAbrir.Click += (_, _) =>
            {
                var primeira = fotos.FirstOrDefault(f => File.Exists(f.CaminhoLocal));
                if (primeira is not null)
                {
                    Process.Start(new ProcessStartInfo(primeira.CaminhoLocal) { UseShellExecute = true });
                }
                else
                {
                    Editar_Click(this, new RoutedEventArgs());
                }
            };
            DockPanel.SetDock(btnAbrir, Dock.Right);
            dock.Children.Add(btnAbrir);
            dock.Children.Add(new TextBlock
            {
                Text = $"{fotos.Count} foto(s) vinculada(s)",
                Foreground = (Brush)FindResource("CorPrimariaEscura"),
                FontWeight = FontWeights.SemiBold,
                FontSize = 12.5,
                VerticalAlignment = VerticalAlignment.Center,
            });
            cardFotos.Child = dock;
            StackDetalheCampos.Children.Add(cardFotos);
        }
    }

    private static string Normalizar(string texto)
    {
        if (string.IsNullOrWhiteSpace(texto)) return "";
        var decom = texto.Trim().ToLowerInvariant().Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(decom.Length);
        foreach (var ch in decom)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(ch) != UnicodeCategory.NonSpacingMark)
                sb.Append(ch);
        }
        return sb.ToString().Normalize(NormalizationForm.FormC);
    }

    private void AtualizarStatus(string? mensagemConexao = null)
    {
        var conflitos = _local.Estado.Conflitos.Count;
        var total = _local.Estado.Atendimentos.Count(a => a.ExcluidoEm is null);
        var pendentesSync = _local.Estado.Outbox.Count + _local.Estado.Fotos.Count(f => f.Pendente);

        var email = string.IsNullOrWhiteSpace(_local.Estado.Email) ? "Sessão ativa" : _local.Estado.Email;
        EmailUsuario.Text = email;
        TxtInicialUsuario.Text = email.Length > 0 ? char.ToUpperInvariant(email[0]).ToString() : "C";

        BtnNavConflitos.Visibility = conflitos > 0 ? Visibility.Visible : Visibility.Collapsed;
        TxtBadgeConflitos.Text = conflitos.ToString();
        if (conflitos == 0 && _secaoAtiva == SecaoAtiva.Conflitos)
        {
            _secaoAtiva = SecaoAtiva.Atendimentos;
            AtualizarInterfaceModalidade();
        }

        TxtPendenciasRodape.Text = pendentesSync > 0
            ? $"{pendentesSync} alteração(ões) na fila local"
            : "Sincronia automática ativa";

        if (StatusDot is not null && TxtStatusNuvem is not null)
        {
            if (_sincronizando)
            {
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(245, 158, 11));
                TxtStatusNuvem.Text = "Sincronizando…";
            }
            else if (!string.IsNullOrWhiteSpace(mensagemConexao) && mensagemConexao.Contains("Sem conexão"))
            {
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(148, 163, 184));
                TxtStatusNuvem.Text = "Modo offline";
            }
            else
            {
                StatusDot.Fill = (Brush)FindResource("CorPrimaria");
                TxtStatusNuvem.Text = _ultimaSincronia > DateTimeOffset.MinValue
                    ? $"Nuvem · {_ultimaSincronia.ToLocalTime():HH:mm}"
                    : "Nuvem ativa";
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
        BtnSincronizar.IsEnabled = false;
        AtualizarStatus();
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
            var total = _local.Estado.Atendimentos.Count(a => a.ExcluidoEm is null);
            if (resumo.Conflitos > 0)
            {
                var detalhe = string.Join(", ", _local.Estado.Conflitos.Select(c =>
                    c.Placa.Length > 0 ? c.Placa : c.Uuid));
                MessageBox.Show($"Conflitos detectados: {detalhe}. Nenhum registro foi descartado.",
                    "ChaveiroCar", MessageBoxButton.OK, MessageBoxImage.Warning);
                _secaoAtiva = SecaoAtiva.Conflitos;
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
            BtnSincronizar.IsEnabled = true;
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

    // ==================== NAVEGAÇÃO SIDEBAR E KPIs ====================

    private void NavInicio_Click(object sender, RoutedEventArgs e)
    {
        _secaoAtiva = SecaoAtiva.Inicio;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void NavAtendimentos_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrEmpty(GarantirModalidadeAtiva()))
        {
            Plano_Click(sender, e);
            return;
        }
        _secaoAtiva = SecaoAtiva.Atendimentos;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void NavFichario_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrEmpty(GarantirModalidadeAtiva()))
        {
            Plano_Click(sender, e);
            return;
        }
        _secaoAtiva = SecaoAtiva.Fichario;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void NavFichas_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrEmpty(GarantirModalidadeAtiva()))
        {
            Plano_Click(sender, e);
            return;
        }
        _secaoAtiva = SecaoAtiva.Fichas;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void NavConflitos_Click(object sender, RoutedEventArgs e)
    {
        _secaoAtiva = SecaoAtiva.Conflitos;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void SelecionarAreaVeicular_Click(object sender, RoutedEventArgs e)
    {
        EntrarNaArea("AUTOMOTIVO", _secaoAtiva == SecaoAtiva.Inicio ? SecaoAtiva.Atendimentos : _secaoAtiva);
    }

    private void SelecionarAreaResidencial_Click(object sender, RoutedEventArgs e)
    {
        EntrarNaArea("RESIDENCIAL", _secaoAtiva == SecaoAtiva.Inicio ? SecaoAtiva.Atendimentos : _secaoAtiva);
    }

    private void EntrarVeicular_Click(object sender, RoutedEventArgs e) => EntrarNaArea("AUTOMOTIVO", SecaoAtiva.Atendimentos);
    private void ConsultarVeicularInicial_Click(object sender, RoutedEventArgs e) => EntrarNaArea("AUTOMOTIVO", SecaoAtiva.Fichas);
    private void EntrarResidencial_Click(object sender, RoutedEventArgs e) => EntrarNaArea("RESIDENCIAL", SecaoAtiva.Atendimentos);
    private void ConsultarResidencialInicial_Click(object sender, RoutedEventArgs e) => EntrarNaArea("RESIDENCIAL", SecaoAtiva.Fichas);

    private void KpiVeicular_Click(object sender, MouseButtonEventArgs e) => EntrarNaArea("AUTOMOTIVO", SecaoAtiva.Atendimentos);
    private void KpiResidencial_Click(object sender, MouseButtonEventArgs e) => EntrarNaArea("RESIDENCIAL", SecaoAtiva.Atendimentos);
    private void KpiFichas_Click(object sender, MouseButtonEventArgs e)
    {
        if (string.IsNullOrEmpty(GarantirModalidadeAtiva())) { Plano_Click(this, new RoutedEventArgs()); return; }
        _secaoAtiva = SecaoAtiva.Fichas;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void KpiPendentes_Click(object sender, MouseButtonEventArgs e)
    {
        if (string.IsNullOrEmpty(GarantirModalidadeAtiva())) { Plano_Click(this, new RoutedEventArgs()); return; }
        DefinirFiltroPagamento("PENDENTES");
        _secaoAtiva = SecaoAtiva.Atendimentos;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private void EntrarNaArea(string seg, SecaoAtiva secao)
    {
        if (!ModulosAtivosAtuais().Contains(seg))
        {
            Plano_Click(this, new RoutedEventArgs());
            return;
        }
        _modalidadeAtiva = seg;
        _secaoAtiva = secao;
        AtualizarInterfaceModalidade();
        RecarregarGrade();
    }

    private async void SincronizarManual_Click(object sender, RoutedEventArgs e) => await Sincronizar();

    private void AcaoPrincipalTopo_Click(object sender, RoutedEventArgs e)
    {
        if (_secaoAtiva == SecaoAtiva.Fichas)
            NovaFicha_Click(sender, e);
        else
            Novo_Click(sender, e);
    }

    // ==================== FILTROS E EVENTOS DE BUSCA ====================

    private void Busca_TextChanged(object sender, TextChangedEventArgs e)
    {
        var vazio = string.IsNullOrEmpty(Busca.Text);
        PlaceholderBusca.Visibility = vazio ? Visibility.Visible : Visibility.Collapsed;
        BtnLimparBusca.Visibility = vazio ? Visibility.Collapsed : Visibility.Visible;
        RecarregarGrade();
    }

    private void LimparBusca_Click(object sender, RoutedEventArgs e)
    {
        Busca.Text = "";
        Busca.Focus();
    }

    private void Busca_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) RecarregarGrade();
        else if (e.Key == Key.Escape && !string.IsNullOrEmpty(Busca.Text)) Busca.Text = "";
    }

    private void BuscaFichario_TextChanged(object sender, TextChangedEventArgs e) => RecarregarGrade();
    private void BuscaFichas_TextChanged(object sender, TextChangedEventArgs e) => RecarregarGrade();
    private void FiltroPeriodo_Mudou(object sender, SelectionChangedEventArgs e) => RecarregarGrade();

    private void FiltroPagamento_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag)
        {
            DefinirFiltroPagamento(tag);
            RecarregarGrade();
        }
    }

    private void DefinirFiltroPagamento(string tag)
    {
        _filtroPagamento = tag;
        var chip = (Style)FindResource("BotaoChip");
        var chipAtivo = (Style)FindResource("BotaoChipAtivo");
        BtnFiltroPagoTodos.Style = tag == "TODOS" ? chipAtivo : chip;
        BtnFiltroPagoSim.Style = tag == "PAGOS" ? chipAtivo : chip;
        BtnFiltroPagoPendente.Style = tag == "PENDENTES" ? chipAtivo : chip;
    }

    private async void Empresa_Mudou(object sender, SelectionChangedEventArgs e)
    {
        if (_inicializandoEmpresas) return;
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

    private void Grade_SelectionChanged(object sender, SelectionChangedEventArgs e) => AtualizarPainelDetalhe();

    private void Grade_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (Selecionado() is not null) Editar_Click(sender, e);
    }

    private void GradeFicharioVeiculos_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (GradeFicharioVeiculos.SelectedItem is ItemFicharioVeiculo item)
        {
            Busca.Text = $"{item.Marca} {item.Modelo}".Trim();
            _secaoAtiva = SecaoAtiva.Atendimentos;
            AtualizarInterfaceModalidade();
            RecarregarGrade();
        }
    }

    private void GradeFicharioResidencial_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (GradeFicharioResidencial.SelectedItem is ItemFicharioResidencial item)
        {
            Busca.Text = $"{item.TipoLocal} {item.MarcaFechadura} {item.ModeloFechadura}".Trim();
            _secaoAtiva = SecaoAtiva.Atendimentos;
            AtualizarInterfaceModalidade();
            RecarregarGrade();
        }
    }

    private void GradeFichas_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (FichaSelecionada() is not null) VerManualFicha_Click(sender, e);
    }

    // ==================== CRUD DE ATENDIMENTOS ====================

    private void Novo_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var seg = GarantirModalidadeAtiva();
        if (string.IsNullOrEmpty(seg))
        {
            Plano_Click(sender, e);
            return;
        }
        var item = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            Segmento = seg,
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TiposServico = [seg == "RESIDENCIAL" ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA"],
        };
        AbrirEditorAtendimento(item, []);
    }

    private void RepetirAtendimento_Click(object sender, RoutedEventArgs e)
    {
        if (!PodeEditarEmpresa()) return;
        var origem = Selecionado();
        if (origem is null) return;
        var copia = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            Segmento = origem.Segmento,
            Placa = origem.Placa,
            Marca = origem.Marca,
            Modelo = origem.Modelo,
            Ano = origem.Ano,
            Chassi = origem.Chassi,
            TipoLocal = origem.TipoLocal,
            AmbientePorta = origem.AmbientePorta,
            MarcaFechadura = origem.MarcaFechadura,
            ModeloFechadura = origem.ModeloFechadura,
            TipoCilindro = origem.TipoCilindro,
            EnderecoLocal = origem.EnderecoLocal,
            ClienteNome = origem.ClienteNome,
            ClienteTelefone = origem.ClienteTelefone,
            ChipTransponder = origem.ChipTransponder,
            CodigoChave = origem.CodigoChave,
            CodigoConfiguracao = origem.CodigoConfiguracao,
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TiposServico = [origem.Segmento == "RESIDENCIAL" ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA"],
        };
        AbrirEditorAtendimento(copia, []);
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
                aoVerFicha: rascunho => AbrirFichaParaAtendimento(rascunho, (Window?)editor ?? this, permitirAcoes: false),
                api: _api,
                empresaId: _local.Estado.EmpresaId,
                historicoLocal: _local.Estado.Atendimentos.Where(a => a.ExcluidoEm is null).ToList())
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
        if (_secaoAtiva == SecaoAtiva.Inicio) _secaoAtiva = SecaoAtiva.Atendimentos;
        AtualizarInterfaceModalidade();
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

        string servicos = item.TiposServico.Count > 0 ? item.TiposFormatados : "Serviço de chaveiro";
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
            FontFamily = new FontFamily("Segoe UI"),
        };
        documento.Blocks.Add(new Paragraph(new Run("ChaveiroCar — Relatório de atendimento"))
        {
            FontSize = 20,
            FontWeight = FontWeights.Bold,
            Margin = new Thickness(0, 0, 0, 18),
        });
        var data = DateTimeOffset.FromUnixTimeMilliseconds(atendimento.DataServico).ToLocalTime();
        AdicionarLinha(documento, "Data", data.ToString("dd/MM/yyyy"));
        AdicionarLinha(documento, "Segmento", atendimento.Segmento == "RESIDENCIAL" ? "Residencial" : "Veicular");
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
        AdicionarLinha(documento, "Serviço", atendimento.TiposFormatados);
        AdicionarLinha(documento, "Defeito", atendimento.Defeito);
        AdicionarLinha(documento, "Solução", atendimento.Solucao);
        AdicionarLinha(documento, "Observações", atendimento.Abertura);
        AdicionarLinha(documento, "Código da chave", atendimento.CodigoChave);
        AdicionarLinha(documento, "Código de configuração", atendimento.CodigoConfiguracao);
        if (atendimento.Segmento != "RESIDENCIAL")
            AdicionarLinha(documento, "Chip / transponder", atendimento.ChipTransponder);
        AdicionarLinha(documento, "Ferramenta", atendimento.Ferramenta);
        AdicionarLinha(documento, "Valor", (atendimento.ValorCentavos / 100m).ToString("C2", CultureInfo.GetCultureInfo("pt-BR")));
        AdicionarLinha(documento, "Pagamento", atendimento.Pago ? "Pago" : "A receber");

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

    // ==================== FICHAS TÉCNICAS E MANUAIS ====================

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
            BuscaFichas.Text = termo;
            _secaoAtiva = SecaoAtiva.Fichas;
            AtualizarInterfaceModalidade();
            RecarregarGrade();
            Status.Text = "Nenhuma ficha exata encontrada; consulta técnica aberta com a busca.";
        }
        else
        {
            MessageBox.Show(
                owner,
                "Nenhuma ficha exata encontrada no catálogo para este veículo/fechadura. Salve ou volte para consultar em Fichas e Manuais.",
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
        IconeApp.Aplicar(dialogo);
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
            Width = 180,
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
        IconeApp.Aplicar(dialogo);

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
            BorderBrush = (Brush)Application.Current.FindResource("CorBordaSuave"),
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
            BorderBrush = (Brush)Application.Current.FindResource("CorBordaSuave"),
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

    // ==================== ADMINISTRAÇÃO E CONFLITOS ====================

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
