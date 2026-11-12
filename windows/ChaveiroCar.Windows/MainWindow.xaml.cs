using System.Windows;
using System.Windows.Input;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class MainWindow : Window
{
    private readonly ArmazenamentoLocal _local = new();
    private ClienteApi? _api;
    private List<ClienteApi.Empresa> _empresas = [];

    public MainWindow()
    {
        InitializeComponent();
        Loaded += MainWindow_Loaded;
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        RecarregarGrade();
        AtualizarStatus();
        if (string.IsNullOrWhiteSpace(_local.Estado.Acesso))
        {
            if (new LoginWindow(_local).ShowDialog() == true)
            {
                await Inicializar();
            }
            else
            {
                Status.Text = "Offline: entre para sincronizar.";
            }
        }
        else
        {
            await Inicializar();
        }
    }

    private async Task Inicializar()
    {
        _api = new ClienteApi(_local.Estado.BaseUrl);
        _api.DefinirTokens(_local.Estado.Acesso, _local.Estado.Refresh);
        try
        {
            _empresas = (await _api.EmpresasAsync()).ToList();
            Empresas.ItemsSource = _empresas;
            Empresas.DisplayMemberPath = "Nome";
            var atual = _empresas.FirstOrDefault(x => x.Id == _local.Estado.EmpresaId) ?? _empresas.FirstOrDefault();
            if (atual is not null)
            {
                Empresas.SelectedItem = atual;
                _local.Estado.EmpresaId = atual.Id;
                _local.Salvar();
            }
            await Sincronizar();
        }
        catch
        {
            Status.Text = "Offline: usando cache local.";
        }
        RecarregarGrade();
        AtualizarStatus();
    }

    private async Task SalvarTokens()
    {
        if (_api is null) return;
        if (_api.Acesso is not null) _local.Estado.Acesso = _api.Acesso;
        if (_api.Refresh is not null) _local.Estado.Refresh = _api.Refresh;
        _local.Salvar();
    }

    private void RecarregarGrade()
    {
        var termo = normalizar(Busca.Text);
        var itens = _local.Estado.Atendimentos
            .Where(a => termo.Length == 0 ||
                normalizar($"{a.Placa} {a.Marca} {a.Modelo} {a.ClienteNome}").Contains(termo))
            .OrderByDescending(a => a.DataServico)
            .ToList();
        Grade.ItemsSource = itens;
    }

    private static string normalizar(string texto) => texto.ToLowerInvariant().Trim();

    private void AtualizarStatus()
    {
        var pendentes = _local.Estado.Outbox.Count;
        var conflitos = _local.Estado.Conflitos.Count;
        Status.Text =
            $"Cursor {_local.Estado.Cursor} · {pendentes} na fila · {conflitos} conflito(s)" +
            (string.IsNullOrWhiteSpace(_local.Estado.Email) ? "" : $" · {_local.Estado.Email}");
    }

    private async Task Sincronizar()
    {
        if (_api is null || string.IsNullOrWhiteSpace(_local.Estado.EmpresaId)) return;
        try
        {
            var resumo = await MotorSincronia.SincronizarAsync(_local, _api, _local.Estado.EmpresaId);
            await SalvarTokens();
            Status.Text =
                $"Sincronizado: {resumo.Enviadas} enviadas, {resumo.Recebidas} recebidas" +
                (resumo.Conflitos > 0 ? $", {resumo.Conflitos} conflito(s) — resolva no celular" : "");
            if (resumo.Conflitos > 0)
            {
                var detalhe = string.Join(", ", _local.Estado.Conflitos.Select(c =>
                    c.Placa.Length > 0 ? c.Placa : c.Uuid));
                MessageBox.Show($"Conflitos: {detalhe}. Nada foi perdido.",
                    "ChaveiroCar", MessageBoxButton.OK);
            }
        }
        catch
        {
            Status.Text = "Sem conexão: fila guardada no aparelho.";
        }
        finally
        {
            RecarregarGrade();
            AtualizarStatus();
        }
    }

    private async void Sincronizar_Click(object sender, RoutedEventArgs e) => await Sincronizar();
    private void Pesquisar_Click(object sender, RoutedEventArgs e) => RecarregarGrade();
    private void Busca_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) RecarregarGrade();
    }

    private void Empresa_Mudou(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (Empresas.SelectedItem is ClienteApi.Empresa empresa)
        {
            _local.Estado.EmpresaId = empresa.Id;
            _local.Salvar();
        }
    }

    private Atendimento? Selecionado() => Grade.SelectedItem as Atendimento;

    private void Novo_Click(object sender, RoutedEventArgs e)
    {
        var item = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            Placa = "NOVA123",
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
        };
        MotorSincronia.EnfileirarUpsert(_local, item);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void Editar_Click(object sender, RoutedEventArgs e)
    {
        var item = Selecionado();
        if (item is null)
        {
            Status.Text = "Selecione um registro.";
            return;
        }
        item.Solucao = $"Editado {DateTime.Now:dd/MM HH:mm}";
        MotorSincronia.EnfileirarUpsert(_local, item);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void Excluir_Click(object sender, RoutedEventArgs e)
    {
        var item = Selecionado();
        if (item is null) return;
        if (MessageBox.Show($"Excluir {item.Placa}?", "ChaveiroCar",
                MessageBoxButton.YesNo) != MessageBoxResult.Yes) return;
        MotorSincronia.EnfileirarExclusao(_local, item.Uuid);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
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
        Close();
    }
}
