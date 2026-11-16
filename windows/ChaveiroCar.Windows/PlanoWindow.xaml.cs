using System.Windows;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class PlanoWindow : Window
{
    private readonly ClienteApi _api;
    private readonly ArmazenamentoLocal _local;
    private readonly string _empresaId;
    private ClienteApi.EstadoAssinatura? _estado;
    private bool _ocupado;

    public bool Atualizado { get; private set; }

    public PlanoWindow(ClienteApi api, ArmazenamentoLocal local, string empresaId)
    {
        _api = api;
        _local = local;
        _empresaId = empresaId;
        InitializeComponent();
        AplicarCacheLocal();
    }

    private async void Window_Loaded(object sender, RoutedEventArgs e)
    {
        await RecarregarAsync();
    }

    private void AplicarCacheLocal()
    {
        var plano = _local.Estado.PlanoDaEmpresa(_empresaId);
        var status = _local.Estado.StatusAssinaturaDaEmpresa(_empresaId);
        var ativos = _local.Estado.ModulosAtivosDaEmpresa(_empresaId);
        _local.Estado.RenovacaoPorEmpresa.TryGetValue(_empresaId, out var renovacao);

        PreencherCampos(
            planoCodigo: plano,
            status: status,
            modulosAtivos: ativos,
            modulosSelecionaveis: ativos,
            renovacaoEm: renovacao,
            mudancaAgendada: "",
            podeGerenciar: false,
            homologacao: false);
    }

    private void PreencherCampos(
        string planoCodigo,
        string status,
        IReadOnlyCollection<string> modulosAtivos,
        IReadOnlyCollection<string> modulosSelecionaveis,
        long? renovacaoEm,
        string mudancaAgendada,
        bool podeGerenciar,
        bool homologacao)
    {
        PlanoTexto.Text = $"Plano: {RotuloPlano(planoCodigo)}";
        StatusTexto.Text = RotuloStatus(status);
        var rotulosAtivos = modulosAtivos.Select(m => m == "RESIDENCIAL" ? "Residencial" : "Veicular").ToList();
        ModulosAtivosTexto.Text = rotulosAtivos.Count > 0
            ? $"Módulos liberados agora: {string.Join(" + ", rotulosAtivos)}"
            : "Nenhum módulo liberado no momento (histórico preservado em segurança).";

        if (renovacaoEm is > 0)
        {
            var data = DateTimeOffset.FromUnixTimeMilliseconds(renovacaoEm.Value).ToLocalTime().ToString("dd/MM/yyyy");
            RenovacaoTexto.Text = $"Próxima renovação: {data}";
            RenovacaoTexto.Visibility = Visibility.Visible;
        }
        else
        {
            RenovacaoTexto.Visibility = Visibility.Collapsed;
        }

        if (!string.IsNullOrWhiteSpace(mudancaAgendada))
        {
            MudancaAgendadaTexto.Text = mudancaAgendada;
            MudancaAgendadaTexto.Visibility = Visibility.Visible;
        }
        else
        {
            MudancaAgendadaTexto.Visibility = Visibility.Collapsed;
        }

        CheckVeicular.IsChecked = modulosSelecionaveis.Contains("AUTOMOTIVO");
        CheckResidencial.IsChecked = modulosSelecionaveis.Contains("RESIDENCIAL");

        PainelGerenciamento.Visibility = podeGerenciar ? Visibility.Visible : Visibility.Collapsed;
        AvisoSemPermissao.Visibility = podeGerenciar ? Visibility.Collapsed : Visibility.Visible;
        PainelHomologacao.Visibility = podeGerenciar && homologacao ? Visibility.Visible : Visibility.Collapsed;
    }

    private void AplicarResposta(ClienteApi.EstadoAssinatura estado)
    {
        _estado = estado;
        _local.Estado.RegistrarAssinaturaEmpresa(
            _empresaId,
            estado.PlanoCodigo,
            estado.Status,
            estado.ModulosAtivos,
            estado.ProximaRenovacaoEm);
        _local.Salvar();
        Atualizado = true;

        var paraCheckboxes = estado.ModulosContratados.Count > 0 ? estado.ModulosContratados : estado.ModulosAtivos;
        PreencherCampos(
            estado.PlanoCodigo,
            estado.Status,
            estado.ModulosAtivos,
            paraCheckboxes,
            estado.ProximaRenovacaoEm,
            estado.MudancaAgendada,
            estado.PodeGerenciar,
            estado.AmbienteHomologacao);
    }

    private List<string> ModulosMarcados()
    {
        var lista = new List<string>();
        if (CheckVeicular.IsChecked == true) lista.Add("AUTOMOTIVO");
        if (CheckResidencial.IsChecked == true) lista.Add("RESIDENCIAL");
        return lista;
    }

    private async Task RecarregarAsync(string aviso = "")
    {
        if (_ocupado) return;
        _ocupado = true;
        Mensagem.Text = "Consultando plano...";
        try
        {
            var resp = await _api.ObterAssinaturaAsync(_empresaId);
            AplicarResposta(resp);
            Mensagem.Text = aviso;
        }
        catch (Exception ex)
        {
            Mensagem.Text = $"Modo offline ou indisponível: {ex.Message}";
        }
        finally
        {
            _ocupado = false;
        }
    }

    private async Task ExecutarAsync(Func<Task<ClienteApi.EstadoAssinatura>> chamada, string avisoSucesso)
    {
        if (_ocupado) return;
        _ocupado = true;
        Mensagem.Text = "Enviando solicitação...";
        try
        {
            var resp = await chamada();
            AplicarResposta(resp);
            Mensagem.Text = resp.Duplicado ? "Evento duplicado ignorado (idempotente)." : avisoSucesso;
        }
        catch (Exception ex)
        {
            Mensagem.Text = ex.Message;
        }
        finally
        {
            _ocupado = false;
        }
    }

    private async void Atualizar_Click(object sender, RoutedEventArgs e) =>
        await RecarregarAsync("Status atualizado.");

    private async void SolicitarAlteracao_Click(object sender, RoutedEventArgs e)
    {
        var selecionados = ModulosMarcados();
        if (selecionados.Count == 0)
        {
            Mensagem.Text = "Selecione ao menos uma modalidade (Veicular ou Residencial).";
            return;
        }
        var acao = _estado is null || _estado.ModulosAtivos.Count == 0 || _estado.PlanoCodigo == "SEM_PLANO"
            ? "CONTRATAR"
            : "ALTERAR_MODULOS";
        await ExecutarAsync(
            () => _api.SolicitarAssinaturaAsync(_empresaId, acao, selecionados),
            "Solicitação registrada. O plano será liberado após a confirmação do pagamento.");
    }

    private async void Reativar_Click(object sender, RoutedEventArgs e)
    {
        var selecionados = ModulosMarcados();
        if (selecionados.Count == 0) selecionados = ["AUTOMOTIVO", "RESIDENCIAL"];
        await ExecutarAsync(
            () => _api.SolicitarAssinaturaAsync(_empresaId, "REATIVAR", selecionados),
            "Reativação solicitada. Aguardando confirmação de pagamento.");
    }

    private async void Cancelar_Click(object sender, RoutedEventArgs e)
    {
        if (MessageBox.Show(
                "Solicitar o cancelamento da assinatura? Seus atendimentos e fichas continuarão salvos.",
                "ChaveiroCar",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question) != MessageBoxResult.Yes)
        {
            return;
        }
        await ExecutarAsync(
            () => _api.SolicitarAssinaturaAsync(_empresaId, "CANCELAR", []),
            "Cancelamento registrado. Aguardando confirmação.");
    }

    private Task SimularEventoAsync(string tipo, IReadOnlyList<string>? modulos = null, string? aviso = null)
    {
        var eventoId = $"win-homolog-{Guid.NewGuid()}";
        return ExecutarAsync(
            () => _api.ProcessarEventoAssinaturaAsync(_empresaId, eventoId, tipo, modulos),
            aviso ?? $"Evento {tipo} processado.");
    }

    private async void EventoConfirmarPagamento_Click(object sender, RoutedEventArgs e)
    {
        var selecionados = ModulosMarcados();
        if (selecionados.Count == 0) selecionados = ["AUTOMOTIVO", "RESIDENCIAL"];
        await SimularEventoAsync("PAGAMENTO_CONFIRMADO", selecionados, "Pagamento confirmado e módulos liberados!");
    }

    private async void EventoRenovar_Click(object sender, RoutedEventArgs e) =>
        await SimularEventoAsync("RENOVACAO_CONFIRMADA", aviso: "Renovação confirmada!");

    private async void EventoFalha_Click(object sender, RoutedEventArgs e) =>
        await SimularEventoAsync("PAGAMENTO_FALHOU", aviso: "Falha de pagamento simulada.");

    private async void EventoCancelar_Click(object sender, RoutedEventArgs e) =>
        await SimularEventoAsync("ASSINATURA_CANCELADA", aviso: "Cancelamento confirmado.");

    private async void EventoEstorno_Click(object sender, RoutedEventArgs e) =>
        await SimularEventoAsync("ESTORNO", aviso: "Estorno processado e acesso revogado.");

    private static string RotuloPlano(string codigo) => codigo switch
    {
        "SOMENTE_RESIDENCIAL" => "Somente Residencial",
        "SOMENTE_VEICULAR" => "Somente Veicular",
        "COMPLETO" => "Veicular + Residencial",
        "SEM_PLANO" => "Sem plano ativo",
        _ => codigo,
    };

    private static string RotuloStatus(string status) => status switch
    {
        "ATIVA" => "Ativa",
        "PENDENTE_PAGAMENTO" => "Pagamento pendente",
        "FALHA_PAGAMENTO" => "Falha no pagamento",
        "CANCELADA" => "Cancelada",
        _ => status,
    };
}
