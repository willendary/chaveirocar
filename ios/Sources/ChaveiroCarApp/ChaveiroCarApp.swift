import Foundation
import ChaveiroCarCore
#if canImport(SwiftUI)
import SwiftUI

@main
struct ChaveiroCariOSApp: App {
    @StateObject private var estado = EstadoAplicativo()

    var body: some Scene {
        WindowGroup {
            RaizView()
                .environmentObject(estado)
        }
    }
}

@MainActor
final class EstadoAplicativo: ObservableObject {
    @Published var sessao: SessaoSalva
    @Published var logado: Bool
    @Published var empresas: [EmpresaResumo] = []
    @Published var atendimentos: [Atendimento] = []
    @Published var fichas: [FichaTecnica] = []
    @Published var conflitos: [ConflitoSinc] = []
    @Published var busca: String = ""
    @Published var filtroSegmento: SegmentoAtendimento? = nil
    @Published var filtroTipoServico: String = ""
    @Published var somenteComFotos: Bool = false
    @Published var sincronizando: Bool = false
    @Published var mensagemStatus: String = ""

    let armazenamento: ArmazenamentoLocal
    let api: ClienteApi

    init(armazenamento: ArmazenamentoLocal = ArmazenamentoLocal()) {
        self.armazenamento = armazenamento
        let salva = KeychainArmazenamento.carregar() ?? SessaoSalva()
        self.sessao = salva
        self.logado = !salva.acesso.isEmpty
        self.api = ClienteApi(
            baseUrl: salva.baseUrl,
            tokenAcesso: salva.acesso.isEmpty ? nil : salva.acesso,
            tokenRefresh: salva.refresh.isEmpty ? nil : salva.refresh
        )
        recarregarLocal()
    }

    func recarregarLocal() {
        atendimentos = armazenamento.listarAtendimentos(
            busca: busca,
            segmento: filtroSegmento,
            tipoServico: filtroTipoServico.isEmpty ? nil : filtroTipoServico,
            somenteComFotos: somenteComFotos
        )
        fichas = armazenamento.listarFichas()
        conflitos = armazenamento.conflitos
    }

    func salvarSessao() {
        sessao.acesso = api.tokenAcesso ?? ""
        sessao.refresh = api.tokenRefresh ?? ""
        KeychainArmazenamento.salvar(sessao)
    }

    func sincronizarAgora() async {
        guard !sincronizando, !sessao.empresaId.isEmpty else { return }
        sincronizando = true
        defer { sincronizando = false }
        do {
            let resumo = try await MotorSincronia.sincronizar(
                armazenamento: armazenamento,
                api: api,
                empresaId: sessao.empresaId
            )
            salvarSessao()
            recarregarLocal()
            mensagemStatus = "Sincronizado: \(resumo.enviadas) enviada(s), \(resumo.recebidas) recebida(s)."
        } catch {
            mensagemStatus = "Modo offline: alterações salvas na fila local."
        }
    }

    func encerrarSessao() async {
        await api.sair()
        KeychainArmazenamento.limpar()
        sessao = SessaoSalva()
        logado = false
    }
}

struct RaizView: View {
    @EnvironmentObject var estado: EstadoAplicativo

    var body: some View {
        if estado.logado {
            TabView {
                AtendimentosListaView()
                    .tabItem { Label("Serviços", systemImage: "wrench.and.screwdriver") }
                FicharioSegmentadoView()
                    .tabItem { Label("Fichário", systemImage: "book.closed") }
                ConsultaTecnicaListaView()
                    .tabItem { Label("Consulta", systemImage: "magnifyingglass") }
                EquipeAjustesView()
                    .tabItem { Label("Ajustes", systemImage: "gearshape") }
            }
        } else {
            LoginContaView()
        }
    }
}

struct LoginContaView: View {
    @EnvironmentObject var estado: EstadoAplicativo
    @State private var email = ""
    @State private var senha = ""
    @State private var nome = ""
    @State private var empresa = "Minha empresa"
    @State private var tokenGoogle = ""
    @State private var modoCadastro = false
    @State private var erro = ""
    @State private var carregando = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Ambiente da API") {
                    Picker("Ambiente", selection: $estado.sessao.ambiente) {
                        ForEach(AmbienteApi.allCases, id: \.self) { amb in
                            Text(amb.rotulo).tag(amb)
                        }
                    }
                    .onChange(of: estado.sessao.ambiente) { novo in
                        estado.sessao.baseUrl = novo.urlPadrao
                        estado.api.baseUrl = novo.urlPadrao
                    }
                    TextField("URL da API", text: $estado.sessao.baseUrl)
                }

                Section(modoCadastro ? "Criar conta ChaveiroCar" : "Entrar na conta") {
                    if modoCadastro {
                        TextField("Seu nome", text: $nome)
                        TextField("Nome da empresa", text: $empresa)
                    }
                    TextField("E-mail", text: $email)
                    SecureField("Senha", text: $senha)
                    Button(modoCadastro ? "Criar conta" : "Entrar") {
                        Task { await autenticarEmail() }
                    }
                    .disabled(carregando)

                    Button(modoCadastro ? "Já tenho conta" : "Criar nova conta") {
                        modoCadastro.toggle()
                    }
                }

                Section("Login rápido com Google") {
                    TextField("Token Google OAuth / ID Token", text: $tokenGoogle)
                    Button("Entrar com Google") {
                        Task { await autenticarGoogle() }
                    }
                    .disabled(carregando || tokenGoogle.trimmingCharacters(in: .whitespaces).isEmpty)
                }

                if !erro.isEmpty {
                    Section {
                        Text(erro).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("ChaveiroCar")
        }
    }

    private func autenticarEmail() async {
        carregando = true
        erro = ""
        defer { carregando = false }
        do {
            estado.api.baseUrl = estado.sessao.baseUrl
            let res = modoCadastro
                ? try await estado.api.cadastrar(nome: nome, email: email, senha: senha, empresa: empresa)
                : try await estado.api.entrar(email: email, senha: senha)
            estado.empresas = res.empresas
            estado.sessao.email = email
            estado.sessao.emailVerificado = res.emailVerificado
            estado.sessao.empresaId = res.empresas.first?.id ?? ""
            estado.salvarSessao()
            estado.logado = true
            await estado.sincronizarAgora()
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func autenticarGoogle() async {
        carregando = true
        erro = ""
        defer { carregando = false }
        do {
            estado.api.baseUrl = estado.sessao.baseUrl
            let token = tokenGoogle.trimmingCharacters(in: .whitespacesAndNewlines)
            let ehJwt = token.split(separator: ".").count == 3
            let res = try await estado.api.entrarComGoogle(
                idToken: ehJwt ? token : "",
                accessToken: ehJwt ? "" : token,
                empresa: empresa
            )
            estado.empresas = res.empresas
            estado.sessao.email = res.email
            estado.sessao.emailVerificado = true
            estado.sessao.empresaId = res.empresas.first?.id ?? ""
            estado.salvarSessao()
            estado.logado = true
            await estado.sincronizarAgora()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}

struct AtendimentosListaView: View {
    @EnvironmentObject var estado: EstadoAplicativo
    @State private var exibindoNovo = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                Picker("Segmento", selection: $estado.filtroSegmento) {
                    Text("Todos").tag(SegmentoAtendimento?.none)
                    Text("Automotivo").tag(SegmentoAtendimento?.some(.automotivo))
                    Text("Residencial").tag(SegmentoAtendimento?.some(.residencial))
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .onChange(of: estado.filtroSegmento) { _ in estado.recarregarLocal() }

                List {
                    if !estado.conflitos.isEmpty {
                        Section("Conflitos de sincronização") {
                            ForEach(estado.conflitos) { conflito in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Conflito: \(conflito.identificacao)").font(.headline)
                                    HStack {
                                        Button("Manter meu aparelho") {
                                            Task {
                                                try? await MotorSincronia.manterLocal(
                                                    armazenamento: estado.armazenamento,
                                                    api: estado.api,
                                                    empresaId: estado.sessao.empresaId,
                                                    conflito: conflito
                                                )
                                                estado.recarregarLocal()
                                            }
                                        }
                                        Button("Aceitar servidor") {
                                            MotorSincronia.aceitarServidor(
                                                armazenamento: estado.armazenamento,
                                                conflito: conflito
                                            )
                                            estado.recarregarLocal()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Section("Atendimentos (\(estado.atendimentos.count))") {
                        ForEach(estado.atendimentos) { item in
                            NavigationLink(destination: AtendimentoDetalheView(atendimento: item)) {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(item.identificacaoPrincipal).font(.headline)
                                        Spacer()
                                        Text(item.segmento.rotulo)
                                            .font(.caption2)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.secondary.opacity(0.15))
                                            .clipShape(Capsule())
                                    }
                                    if !item.subtituloPrincipal.isEmpty {
                                        Text(item.subtituloPrincipal)
                                            .font(.subheadline)
                                            .foregroundStyle(.secondary)
                                    }
                                    if !item.solucao.isEmpty {
                                        Text(item.solucao)
                                            .font(.caption)
                                            .lineLimit(2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $estado.busca, prompt: "Buscar placa, modelo, local, fechadura...")
            .onChange(of: estado.busca) { _ in estado.recarregarLocal() }
            .navigationTitle("Serviços")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        exibindoNovo = true
                    } label: {
                        Label("Novo", systemImage: "plus")
                    }
                }
                ToolbarItem(placement: .automatic) {
                    Button {
                        Task { await estado.sincronizarAgora() }
                    } label: {
                        Label("Sincronizar", systemImage: "arrow.triangle.2.circlepath")
                    }
                }
            }
            .sheet(isPresented: $exibindoNovo) {
                AtendimentoFormularioSheet(atendimentoInicial: Atendimento(
                    segmento: estado.filtroSegmento ?? .automotivo,
                    tiposServico: [TipoServico.padraoDoSegmento(estado.filtroSegmento ?? .automotivo).rawValue]
                ))
            }
        }
    }
}

struct AtendimentoDetalheView: View {
    @EnvironmentObject var estado: EstadoAplicativo
    let atendimento: Atendimento
    @State private var editando = false
    @State private var fotoParaAnotar: FotoAtendimento? = nil

    var body: some View {
        List {
            Section(atendimento.ehResidencial ? "Imóvel / Fechadura" : "Veículo") {
                LabeledContent("Segmento", value: atendimento.segmento.rotulo)
                if atendimento.ehResidencial {
                    if !atendimento.tipoLocal.isEmpty { LabeledContent("Tipo de local", value: atendimento.tipoLocal) }
                    if !atendimento.ambientePorta.isEmpty { LabeledContent("Porta / ambiente", value: atendimento.ambientePorta) }
                    if !atendimento.marcaFechadura.isEmpty { LabeledContent("Marca da fechadura", value: atendimento.marcaFechadura) }
                    if !atendimento.modeloFechadura.isEmpty { LabeledContent("Modelo da fechadura", value: atendimento.modeloFechadura) }
                    if !atendimento.tipoCilindro.isEmpty { LabeledContent("Cilindro / miolo", value: atendimento.tipoCilindro) }
                    if !atendimento.enderecoLocal.isEmpty { LabeledContent("Endereço", value: atendimento.enderecoLocal) }
                } else {
                    LabeledContent("Placa", value: atendimento.placa.isEmpty ? "Sem placa" : atendimento.placa)
                    if !atendimento.marca.isEmpty { LabeledContent("Marca", value: atendimento.marca) }
                    LabeledContent("Modelo", value: atendimento.modelo)
                    if !atendimento.ano.isEmpty { LabeledContent("Ano", value: atendimento.ano) }
                    if !atendimento.chassi.isEmpty { LabeledContent("Chassi", value: atendimento.chassi) }
                    if !atendimento.chipTransponder.isEmpty { LabeledContent("Chip / transponder", value: atendimento.chipTransponder) }
                }
            }

            Section("Serviço e memória técnica") {
                LabeledContent("Tipos", value: atendimento.tiposServico.joined(separator: ", "))
                if !atendimento.defeito.isEmpty { LabeledContent("Defeito", value: atendimento.defeito) }
                if !atendimento.solucao.isEmpty { LabeledContent("Solução", value: atendimento.solucao) }
                if !atendimento.abertura.isEmpty { LabeledContent("Abertura", value: atendimento.abertura) }
                if !atendimento.codigoChave.isEmpty { LabeledContent("Código de chave", value: atendimento.codigoChave) }
                if !atendimento.codigoConfiguracao.isEmpty { LabeledContent("Código de config.", value: atendimento.codigoConfiguracao) }
                if !atendimento.ferramenta.isEmpty { LabeledContent("Ferramenta", value: atendimento.ferramenta) }
            }

            let fotos = estado.armazenamento.fotosDoAtendimento(atendimento.uuid)
            Section("Fotos (\(fotos.count))") {
                ForEach(fotos) { foto in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(foto.legenda.isEmpty ? foto.categoria : foto.legenda)
                            if foto.temAnotacoes {
                                Text("Com setas indicativas").font(.caption).foregroundStyle(.orange)
                            }
                        }
                        Spacer()
                        Button("Anotar com setas") {
                            fotoParaAnotar = foto
                        }
                    }
                }
            }
        }
        .navigationTitle(atendimento.identificacaoPrincipal)
        .toolbar {
            Button("Editar") { editando = true }
        }
        .sheet(isPresented: $editando) {
            AtendimentoFormularioSheet(atendimentoInicial: atendimento)
        }
        .sheet(item: $fotoParaAnotar) { foto in
            FotoAnotadorSheet(foto: foto)
        }
    }
}

struct AtendimentoFormularioSheet: View {
    @EnvironmentObject var estado: EstadoAplicativo
    @Environment(\.dismiss) private var dismiss
    @State var item: Atendimento
    @State private var erro = ""

    init(atendimentoInicial: Atendimento) {
        _item = State(initialValue: atendimentoInicial)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Segmento do atendimento") {
                    Picker("Segmento", selection: $item.segmento) {
                        ForEach(SegmentoAtendimento.allCases, id: \.self) { seg in
                            Text(seg.rotulo).tag(seg)
                        }
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: item.segmento) { novo in
                        item.tiposServico = [TipoServico.padraoDoSegmento(novo).rawValue]
                    }
                }

                if item.ehResidencial {
                    Section("Local e fechadura") {
                        Picker("Tipo de local", selection: $item.tipoLocal) {
                            Text("Selecione...").tag("")
                            ForEach(Atendimento.tiposLocalResidencial, id: \.self) { local in
                                Text(local).tag(local)
                            }
                        }
                        TextField("Porta / ambiente (ex.: Porta principal)", text: $item.ambientePorta)
                        TextField("Marca da fechadura (ex.: Papaiz, Pado, Intelbras)", text: $item.marcaFechadura)
                        TextField("Modelo da fechadura", text: $item.modeloFechadura)
                        TextField("Tipo de cilindro / miolo", text: $item.tipoCilindro)
                        TextField("Endereço / referência (opcional)", text: $item.enderecoLocal)
                    }
                } else {
                    Section("Veículo (placa opcional, modelo obrigatório)") {
                        TextField("Placa (opcional)", text: $item.placa)
                        TextField("Marca", text: $item.marca)
                        TextField("Modelo *", text: $item.modelo)
                        TextField("Ano", text: $item.ano)
                        TextField("Chassi", text: $item.chassi)
                        TextField("Chip / transponder", text: $item.chipTransponder)
                    }
                }

                Section("Tipos de serviço") {
                    ForEach(TipoServico.doSegmento(item.segmento), id: \.self) { tipo in
                        let marcado = item.tiposServico.contains(tipo.rawValue)
                        Button {
                            if marcado && item.tiposServico.count > 1 {
                                item.tiposServico.removeAll { $0 == tipo.rawValue }
                            } else if !marcado {
                                item.tiposServico.append(tipo.rawValue)
                            }
                        } label: {
                            HStack {
                                Text(tipo.rotulo)
                                Spacer()
                                if marcado { Image(systemName: "checkmark") }
                            }
                        }
                    }
                }

                Section("Diagnóstico e memória técnica") {
                    TextField("Defeito encontrado", text: $item.defeito)
                    TextField("Solução aplicada", text: $item.solucao)
                    TextField("Abertura / procedimento", text: $item.abertura)
                    TextField("Código da chave / segredo", text: $item.codigoChave)
                    TextField("Código de configuração", text: $item.codigoConfiguracao)
                    TextField("Ferramenta utilizada", text: $item.ferramenta)
                }

                Section("Cliente") {
                    TextField("Nome do cliente", text: $item.clienteNome)
                    TextField("Telefone", text: $item.clienteTelefone)
                    Toggle("Serviço pago", isOn: $item.pago)
                }

                if !erro.isEmpty {
                    Section {
                        Text(erro).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle(item.identificacaoPrincipal)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") {
                        do {
                            try estado.armazenamento.salvarAtendimentoLocal(item)
                            estado.recarregarLocal()
                            dismiss()
                        } catch {
                            erro = error.localizedDescription
                        }
                    }
                }
            }
        }
    }
}

struct FotoAnotadorSheet: View {
    @EnvironmentObject var estado: EstadoAplicativo
    @Environment(\.dismiss) private var dismiss
    let foto: FotoAtendimento

    @State private var setas: [SetaAnotacao]
    @State private var desfeitas: [SetaAnotacao] = []
    @State private var rascunho: SetaAnotacao? = nil
    @State private var corSelecionada = "#FF3B30"
    @State private var espessuraSelecionada = 6.0

    private let cores = ["#FF3B30", "#FFCC00", "#34C759", "#00C7BE", "#FFFFFF"]

    init(foto: FotoAtendimento) {
        self.foto = foto
        _setas = State(initialValue: SetaAnotacao.ler(foto.anotacoes))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text("Arraste na imagem para desenhar setas indicando componentes.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                GeometryReader { geo in
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.black.opacity(0.85))
                        Canvas { ctx, size in
                            for seta in setas + (rascunho.map { [$0] } ?? []) {
                                let p1 = CGPoint(x: seta.inicioX * size.width, y: seta.inicioY * size.height)
                                let p2 = CGPoint(x: seta.fimX * size.width, y: seta.fimY * size.height)
                                var traco = Path()
                                traco.move(to: p1)
                                traco.addLine(to: p2)
                                ctx.stroke(traco, with: .color(.red), lineWidth: seta.espessura)
                            }
                        }
                        .gesture(
                            DragGesture(minimumDistance: 4)
                                .onChanged { valor in
                                    let w = max(geo.size.width, 1)
                                    let h = max(geo.size.height, 1)
                                    rascunho = SetaAnotacao(
                                        inicioX: valor.startLocation.x / w,
                                        inicioY: valor.startLocation.y / h,
                                        fimX: valor.location.x / w,
                                        fimY: valor.location.y / h,
                                        cor: corSelecionada,
                                        espessura: espessuraSelecionada
                                    )
                                }
                                .onEnded { _ in
                                    if let nova = rascunho {
                                        setas.append(nova)
                                        desfeitas.removeAll()
                                    }
                                    rascunho = nil
                                }
                        )
                    }
                }
                .frame(height: 320)
                .padding(.horizontal)

                HStack {
                    ForEach(cores, id: \.self) { cor in
                        Button(cor) { corSelecionada = cor }
                            .font(.caption2)
                            .buttonStyle(.bordered)
                    }
                }

                HStack(spacing: 16) {
                    Button("Desfazer") {
                        if let ultima = setas.popLast() { desfeitas.append(ultima) }
                    }
                    .disabled(setas.isEmpty)

                    Button("Refazer") {
                        if let recuperada = desfeitas.popLast() { setas.append(recuperada) }
                    }
                    .disabled(desfeitas.isEmpty)

                    Button("Limpar tudo", role: .destructive) {
                        setas.removeAll()
                        desfeitas.removeAll()
                    }
                    .disabled(setas.isEmpty)
                }
            }
            .navigationTitle("Anotar com setas")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") {
                        let dadosOriginais = estado.armazenamento.carregarOriginalDaFoto(foto) ?? Data()
                        _ = estado.armazenamento.salvarFotoLocal(
                            uuid: foto.uuid,
                            atendimentoUuid: foto.atendimentoUuid,
                            dadosImagem: dadosOriginais,
                            dadosOriginal: dadosOriginais,
                            categoria: foto.categoria,
                            legenda: foto.legenda,
                            anotacoes: SetaAnotacao.serializar(setas),
                            ordem: foto.ordem
                        )
                        estado.recarregarLocal()
                        dismiss()
                    }
                }
            }
        }
    }
}

struct FicharioSegmentadoView: View {
    @EnvironmentObject var estado: EstadoAplicativo
    @State private var segmento: SegmentoAtendimento = .automotivo

    var body: some View {
        NavigationStack {
            VStack {
                Picker("Segmento", selection: $segmento) {
                    Text("Veículos").tag(SegmentoAtendimento.automotivo)
                    Text("Residencial").tag(SegmentoAtendimento.residencial)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)

                if segmento == .automotivo {
                    let veiculos = estado.armazenamento.ficharioVeiculos()
                    List(veiculos) { v in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(v.marca) \(v.modelo) \(v.ano)".trimmingCharacters(in: .whitespaces))
                                .font(.headline)
                            Text("\(v.totalServicos) serviço(s)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if !v.abertura.isEmpty { Text("Abertura: \(v.abertura)").font(.caption) }
                            if !v.codigoChave.isEmpty { Text("Código: \(v.codigoChave)").font(.caption) }
                            if !v.chipTransponder.isEmpty { Text("Chip: \(v.chipTransponder)").font(.caption) }
                        }
                    }
                } else {
                    let residenciais = estado.armazenamento.ficharioResidencial()
                    List(residenciais) { r in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(r.tipoLocal) · \(r.marcaFechadura) \(r.modeloFechadura)".trimmingCharacters(in: .whitespaces))
                                .font(.headline)
                            Text("\(r.totalServicos) serviço(s)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if !r.tipoCilindro.isEmpty { Text("Cilindro: \(r.tipoCilindro)").font(.caption) }
                            if !r.abertura.isEmpty { Text("Procedimento: \(r.abertura)").font(.caption) }
                            if !r.codigoChave.isEmpty { Text("Segredo/chave: \(r.codigoChave)").font(.caption) }
                        }
                    }
                }
            }
            .navigationTitle("Fichário")
        }
    }
}

struct ConsultaTecnicaListaView: View {
    @EnvironmentObject var estado: EstadoAplicativo
    @State private var buscaFicha = ""

    var body: some View {
        NavigationStack {
            let fichas = estado.armazenamento.listarFichas(busca: buscaFicha)
            List(fichas) { ficha in
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(ficha.marca) \(ficha.modelo) \(ficha.ano)".trimmingCharacters(in: .whitespaces))
                        .font(.headline)
                    Text("\(ficha.equipamento) · \(ficha.sistema)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    if !ficha.chip.isEmpty { Text("Chip: \(ficha.chip)").font(.caption) }
                    if !ficha.manual.isEmpty { Text(ficha.manual).font(.caption).lineLimit(3) }
                }
            }
            .searchable(text: $buscaFicha, prompt: "Equipamento, modelo, chip...")
            .navigationTitle("Consulta Técnica")
        }
    }
}

struct EquipeAjustesView: View {
    @EnvironmentObject var estado: EstadoAplicativo

    var body: some View {
        NavigationStack {
            Form {
                Section("Conta e Empresa") {
                    LabeledContent("E-mail", value: estado.sessao.email)
                    LabeledContent("Ambiente", value: estado.sessao.ambiente.rotulo)
                    LabeledContent("Fila offline", value: "\(estado.armazenamento.fila.count) pendente(s)")
                    Button("Sincronizar agora") {
                        Task { await estado.sincronizarAgora() }
                    }
                }

                if !estado.mensagemStatus.isEmpty {
                    Section("Status") {
                        Text(estado.mensagemStatus).font(.caption)
                    }
                }

                Section {
                    Button("Sair da conta", role: .destructive) {
                        Task { await estado.encerrarSessao() }
                    }
                }
            }
            .navigationTitle("Ajustes")
        }
    }
}
#else
@main
struct ChaveiroCarCLI {
    static func main() {
        print("ChaveiroCar iOS Core pronto (\(AmbienteApi.producao.urlPadrao))")
    }
}
#endif
