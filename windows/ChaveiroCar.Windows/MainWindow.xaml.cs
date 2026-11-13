using System.Windows;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Controls;
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
        await VerificarAtualizacao();
    }

    private static async Task VerificarAtualizacao()
    {
        var atualizacao = await VerificadorAtualizacao.VerificarAsync();
        if (atualizacao is null) return;
        if (MessageBox.Show(
                $"ChaveiroCar {atualizacao.Versao} está disponível. Baixar atualização?",
                "Atualização disponível", MessageBoxButton.YesNo, MessageBoxImage.Information)
            == MessageBoxResult.Yes)
        {
            VerificadorAtualizacao.AbrirDownload(atualizacao);
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
        GradeFichas.ItemsSource = _local.Estado.Fichas
            .Where(f => termo.Length == 0 ||
                normalizar($"{f.Equipamento} {f.Marca} {f.Modelo} {f.Ano} {f.Chip}").Contains(termo))
            .OrderBy(f => f.Marca)
            .ThenBy(f => f.Modelo)
            .ToList();
        GradeConflitos.ItemsSource = _local.Estado.Conflitos.ToList();
    }

    private static string normalizar(string texto) => texto.ToLowerInvariant().Trim();

    private void AtualizarStatus()
    {
        var pendentes = _local.Estado.Outbox.Count;
        var conflitos = _local.Estado.Conflitos.Count;
        var cursor = _local.Estado.CursoresPorEmpresa.GetValueOrDefault(
            _local.Estado.EmpresaId, _local.Estado.Cursor);
        Status.Text =
            $"Cursor {cursor} · {pendentes} na fila · {conflitos} conflito(s)" +
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
                (resumo.Conflitos > 0 ? $", {resumo.Conflitos} conflito(s) — resolva na aba Conflitos" : "");
            if (resumo.Conflitos > 0)
            {
                var detalhe = string.Join(", ", _local.Estado.Conflitos.Select(c =>
                    c.Placa.Length > 0 ? c.Placa : c.Uuid));
                MessageBox.Show($"Conflitos: {detalhe}. Nada foi descartado.",
                    "ChaveiroCar", MessageBoxButton.OK);
                AbasPrincipal.SelectedIndex = 2;
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
    private ArmazenamentoLocal.Ficha? FichaSelecionada() => GradeFichas.SelectedItem as ArmazenamentoLocal.Ficha;

    private void Novo_Click(object sender, RoutedEventArgs e)
    {
        var item = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TiposServico = ["OUTRO"],
        };
        var editor = new AtendimentoEditorWindow(item, []) { Owner = this };
        if (editor.ShowDialog() != true || editor.Resultado is null) return;
        MotorSincronia.EnfileirarUpsert(_local, editor.Resultado);
        AdicionarFotos(editor.ResultadoFotos);
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
        var fotos = _local.Estado.Fotos.Where(f => f.AtendimentoUuid == item.Uuid).ToList();
        var editor = new AtendimentoEditorWindow(item, fotos) { Owner = this };
        if (editor.ShowDialog() != true || editor.Resultado is null) return;
        MotorSincronia.EnfileirarUpsert(_local, editor.Resultado);
        AdicionarFotos(editor.ResultadoFotos);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void AdicionarFotos(IEnumerable<ArmazenamentoLocal.FotoLocal> fotos)
    {
        foreach (var foto in fotos)
        {
            if (_local.Estado.Fotos.All(atual => atual.Uuid != foto.Uuid))
                _local.Estado.Fotos.Add(foto);
        }
        _local.Salvar();
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

    private void ExportarPdf_Click(object sender, RoutedEventArgs e)
    {
        var atendimento = Selecionado();
        if (atendimento is null)
        {
            Status.Text = "Selecione um atendimento para gerar o relatório.";
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
        AdicionarLinha(documento, "Placa", atendimento.Placa);
        AdicionarLinha(documento, "Veículo", $"{atendimento.Marca} {atendimento.Modelo} {atendimento.Ano}".Trim());
        AdicionarLinha(documento, "Chassi", atendimento.Chassi);
        AdicionarLinha(documento, "Cliente", atendimento.ClienteNome);
        AdicionarLinha(documento, "Telefone", atendimento.ClienteTelefone);
        AdicionarLinha(documento, "Serviço", string.Join(", ", atendimento.TiposServico));
        AdicionarLinha(documento, "Defeito", atendimento.Defeito);
        AdicionarLinha(documento, "Solução", atendimento.Solucao);
        AdicionarLinha(documento, "Observações", atendimento.Abertura);
        AdicionarLinha(documento, "Código da chave", atendimento.CodigoChave);
        AdicionarLinha(documento, "Código de configuração", atendimento.CodigoConfiguracao);
        AdicionarLinha(documento, "Chip / transponder", atendimento.ChipTransponder);
        AdicionarLinha(documento, "Ferramenta", atendimento.Ferramenta);
        AdicionarLinha(documento, "Valor", (atendimento.ValorCentavos / 100m).ToString("C"));
        AdicionarLinha(documento, "Pagamento", atendimento.Pago ? "Pago" : "Pendente");

        var impressora = new PrintDialog();
        if (impressora.ShowDialog() == true)
        {
            impressora.PrintDocument(
                ((IDocumentPaginatorSource)documento).DocumentPaginator,
                $"ChaveiroCar - {atendimento.Placa}");
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
        var ficha = new ArmazenamentoLocal.Ficha { Uuid = Guid.NewGuid().ToString() };
        var editor = new FichaEditorWindow(ficha, []) { Owner = this };
        if (editor.ShowDialog() != true || editor.Resultado is null) return;
        MotorSincronia.EnfileirarFicha(_local, editor.Resultado);
        AdicionarFotos(editor.ResultadoFotos);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void EditarFicha_Click(object sender, RoutedEventArgs e)
    {
        var ficha = FichaSelecionada();
        if (ficha is null)
        {
            Status.Text = "Selecione uma ficha técnica.";
            return;
        }
        var fotos = _local.Estado.Fotos.Where(f => f.FichaUuid == ficha.Uuid).ToList();
        var editor = new FichaEditorWindow(ficha, fotos) { Owner = this };
        if (editor.ShowDialog() != true || editor.Resultado is null) return;
        MotorSincronia.EnfileirarFicha(_local, editor.Resultado);
        AdicionarFotos(editor.ResultadoFotos);
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void ExcluirFicha_Click(object sender, RoutedEventArgs e)
    {
        var ficha = FichaSelecionada();
        if (ficha is null) return;
        if (MessageBox.Show($"Excluir ficha {ficha.Marca} {ficha.Modelo}?", "ChaveiroCar",
                MessageBoxButton.YesNo) != MessageBoxResult.Yes) return;
        MotorSincronia.EnfileirarExclusao(_local, ficha.Uuid, "ficha");
        RecarregarGrade();
        AtualizarStatus();
        _ = Sincronizar();
    }

    private void Equipe_Click(object sender, RoutedEventArgs e)
    {
        if (_api is null || Empresas.SelectedItem is not ClienteApi.Empresa empresa)
        {
            Status.Text = "Entre e selecione uma empresa para gerenciar a equipe.";
            return;
        }
        var podeGerenciar = empresa.Papel is "proprietario" or "administrador";
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
        Close();
    }
}
