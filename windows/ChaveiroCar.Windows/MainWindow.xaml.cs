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

    private string SegmentoSelecionado() =>
        (FiltroSegmento?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "";

    private void RecarregarGrade()
    {
        if (Grade is null) return;
        var termo = normalizar(Busca.Text);
        var seg = SegmentoSelecionado();
        var itens = _local.Estado.Atendimentos
            .Where(a => (string.IsNullOrEmpty(seg) || a.Segmento == seg) &&
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
                (resumo.Conflitos > 0 ? $", {resumo.Conflitos} conflito(s) — resolva na aba Conflitos" : "") +
                (resumo.Falhas > 0 ? $", {resumo.Falhas} pendência(s) para tentar de novo" : "");
            if (resumo.Conflitos > 0)
            {
                var detalhe = string.Join(", ", _local.Estado.Conflitos.Select(c =>
                    c.Placa.Length > 0 ? c.Placa : c.Uuid));
                MessageBox.Show($"Conflitos: {detalhe}. Nada foi descartado.",
                    "ChaveiroCar", MessageBoxButton.OK);
                AbasPrincipal.SelectedIndex = 3;
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
    private void FiltroSegmento_Mudou(object sender, SelectionChangedEventArgs e) => RecarregarGrade();
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
        var seg = SegmentoSelecionado() == "RESIDENCIAL" ? "RESIDENCIAL" : "AUTOMOTIVO";
        var item = new Atendimento
        {
            Uuid = Guid.NewGuid().ToString(),
            Segmento = seg,
            DataServico = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TiposServico = [seg == "RESIDENCIAL" ? "ABERTURA_RESIDENCIAL" : "CHAVE_CODIFICADA"],
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
            var indice = _local.Estado.Fotos.FindIndex(atual => atual.Uuid == foto.Uuid);
            if (indice < 0) _local.Estado.Fotos.Add(foto);
            else _local.Estado.Fotos[indice] = foto;
        }
        _local.Salvar();
    }

    private void Excluir_Click(object sender, RoutedEventArgs e)
    {
        var item = Selecionado();
        if (item is null) return;
        var nome = !string.IsNullOrWhiteSpace(item.Placa)
            ? item.Placa
            : (!string.IsNullOrWhiteSpace(item.Modelo) ? item.Modelo : item.TipoLocal);
        if (MessageBox.Show($"Excluir {nome}?", "ChaveiroCar",
                MessageBoxButton.YesNo) != MessageBoxResult.Yes) return;
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
        MotorSincronia.EnfileirarExclusao(_local, ficha.Uuid, ficha.Revisao, "ficha");
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
