import Foundation

public struct OperacaoSaida: Codable, Equatable, Identifiable, Sendable {
    public var opId: String
    public var entidade: String
    public var idEntidade: String
    public var tipo: String
    public var baseRevisao: Int64

    public var id: String { opId }

    public init(
        opId: String = UUID().uuidString.lowercased(),
        entidade: String = "atendimento",
        idEntidade: String,
        tipo: String,
        baseRevisao: Int64
    ) {
        self.opId = opId
        self.entidade = entidade
        self.idEntidade = idEntidade
        self.tipo = tipo
        self.baseRevisao = baseRevisao
    }
}

public struct ConflitoSinc: Codable, Equatable, Identifiable, Sendable {
    public var idEntidade: String
    public var entidade: String
    public var identificacao: String
    public var revisaoServidor: Int64
    public var snapshotServidorJson: String

    public var id: String { "\(entidade):\(idEntidade)" }

    public init(
        idEntidade: String,
        entidade: String = "atendimento",
        identificacao: String,
        revisaoServidor: Int64,
        snapshotServidorJson: String = ""
    ) {
        self.idEntidade = idEntidade
        self.entidade = entidade
        self.identificacao = identificacao
        self.revisaoServidor = revisaoServidor
        self.snapshotServidorJson = snapshotServidorJson
    }
}

public struct ResumoModeloVeiculo: Equatable, Identifiable, Sendable {
    public let marca: String
    public let modelo: String
    public let ano: String
    public let totalServicos: Int
    public let ultimaData: Int64
    public let abertura: String
    public let codigoChave: String
    public let chipTransponder: String

    public var id: String { "\(marca)|\(modelo)|\(ano)".lowercased() }
}

public struct ResumoResidencialFichario: Equatable, Identifiable, Sendable {
    public let tipoLocal: String
    public let marcaFechadura: String
    public let modeloFechadura: String
    public let tipoCilindro: String
    public let totalServicos: Int
    public let ultimaData: Int64
    public let abertura: String
    public let codigoChave: String

    public var id: String { "\(tipoLocal)|\(marcaFechadura)|\(modeloFechadura)".lowercased() }
}

public final class ArmazenamentoLocal: @unchecked Sendable {
    private struct EstadoPersistido: Codable {
        var cursor: Int64 = 0
        var atendimentos: [Atendimento] = []
        var fichas: [FichaTecnica] = []
        var fotos: [FotoAtendimento] = []
        var fila: [OperacaoSaida] = []
        var conflitos: [ConflitoSinc] = []
    }

    private let pastaBase: URL
    private var estado = EstadoPersistido()

    public init(pastaBase: URL? = nil) {
        if let pastaBase {
            self.pastaBase = pastaBase
        } else {
            let suporte = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.pastaBase = suporte.appendingPathComponent("ChaveiroCar", isDirectory: true)
        }
        try? FileManager.default.createDirectory(at: self.pastaBase, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: pastaFotos, withIntermediateDirectories: true)
        carregar()
    }

    public var pastaFotos: URL {
        pastaBase.appendingPathComponent("fotos", isDirectory: true)
    }

    private var arquivoEstado: URL {
        pastaBase.appendingPathComponent("estado.json")
    }

    public var cursor: Int64 {
        get { estado.cursor }
        set {
            estado.cursor = newValue
            salvar()
        }
    }

    public var fila: [OperacaoSaida] { estado.fila }
    public var conflitos: [ConflitoSinc] { estado.conflitos }
    public var fotos: [FotoAtendimento] { estado.fotos }

    public func listarAtendimentos(
        busca: String = "",
        segmento: SegmentoAtendimento? = nil,
        tipoServico: String? = nil,
        somenteComFotos: Bool = false
    ) -> [Atendimento] {
        let termo = Self.normalizar(busca)
        return estado.atendimentos
            .filter { !$0.excluido }
            .filter { item in
                if let segmento, item.segmento != segmento { return false }
                if let tipoServico, !tipoServico.isEmpty, !item.tiposServico.contains(tipoServico) { return false }
                if somenteComFotos, fotosDoAtendimento(item.uuid).isEmpty { return false }
                if termo.isEmpty { return true }
                let alvo = Self.normalizar([
                    item.placa, item.marca, item.modelo, item.ano, item.chassi,
                    item.tipoLocal, item.ambientePorta, item.marcaFechadura, item.modeloFechadura,
                    item.tipoCilindro, item.enderecoLocal, item.defeito, item.solucao,
                    item.abertura, item.codigoChave, item.codigoConfiguracao, item.chipTransponder,
                    item.ferramenta, item.clienteNome, item.clienteTelefone
                ].joined(separator: " "))
                return termo.split(separator: " ").allSatisfy { alvo.contains($0) }
            }
            .sorted { $0.dataServico > $1.dataServico }
    }

    public func obterAtendimento(_ uuid: String) -> Atendimento? {
        estado.atendimentos.first { $0.uuid == uuid && !$0.excluido }
    }

    public func salvarAtendimentoLocal(_ item: Atendimento) throws {
        try item.validar()
        var copia = item
        copia.atualizadoEm = Int64(Date().timeIntervalSince1970 * 1000)
        if let idx = estado.atendimentos.firstIndex(where: { $0.uuid == copia.uuid }) {
            copia.revisao = estado.atendimentos[idx].revisao
            estado.atendimentos[idx] = copia
        } else {
            estado.atendimentos.append(copia)
        }
        enfileirar(entidade: "atendimento", id: copia.uuid, tipo: "salvar", baseRevisao: copia.revisao)
        salvar()
    }

    public func excluirAtendimentoLocal(_ uuid: String) {
        guard let idx = estado.atendimentos.firstIndex(where: { $0.uuid == uuid }) else { return }
        let revisaoBase = estado.atendimentos[idx].revisao
        estado.atendimentos[idx].excluido = true
        enfileirar(entidade: "atendimento", id: uuid, tipo: "excluir", baseRevisao: revisaoBase)
        salvar()
    }

    public func aplicarAtendimentoServidor(_ remoto: Atendimento) {
        if let idx = estado.atendimentos.firstIndex(where: { $0.uuid == remoto.uuid }) {
            estado.atendimentos[idx] = remoto
        } else {
            estado.atendimentos.append(remoto)
        }
        salvar()
    }

    public func listarFichas(busca: String = "") -> [FichaTecnica] {
        let termo = Self.normalizar(busca)
        return estado.fichas
            .filter { !$0.excluido }
            .filter { f in
                if termo.isEmpty { return true }
                let alvo = Self.normalizar([
                    f.equipamento, f.marca, f.modelo, f.ano, f.sistema,
                    f.chip, f.chave, f.telecomando, f.manual, f.funcoes
                ].joined(separator: " "))
                return termo.split(separator: " ").allSatisfy { alvo.contains($0) }
            }
            .sorted { ($0.marca, $0.modelo, $0.ano) < ($1.marca, $1.modelo, $1.ano) }
    }

    public func salvarFichaLocal(_ ficha: FichaTecnica) {
        var copia = ficha
        copia.atualizadoEm = Int64(Date().timeIntervalSince1970 * 1000)
        if let idx = estado.fichas.firstIndex(where: { $0.uuid == copia.uuid }) {
            copia.revisao = estado.fichas[idx].revisao
            estado.fichas[idx] = copia
        } else {
            estado.fichas.append(copia)
        }
        enfileirar(entidade: "ficha", id: copia.uuid, tipo: "salvar", baseRevisao: copia.revisao)
        salvar()
    }

    public func aplicarFichaServidor(_ remota: FichaTecnica) {
        if let idx = estado.fichas.firstIndex(where: { $0.uuid == remota.uuid }) {
            estado.fichas[idx] = remota
        } else {
            estado.fichas.append(remota)
        }
        salvar()
    }

    public func fotosDoAtendimento(_ atendimentoUuid: String) -> [FotoAtendimento] {
        estado.fotos
            .filter { $0.atendimentoUuid == atendimentoUuid && !$0.excluido }
            .sorted { ($0.ordem, $0.uuid) < ($1.ordem, $1.uuid) }
    }

    public func salvarFotoLocal(
        uuid: String = UUID().uuidString.lowercased(),
        atendimentoUuid: String,
        dadosImagem: Data,
        dadosOriginal: Data? = nil,
        categoria: String = "CARRO",
        legenda: String = "",
        anotacoes: String = "[]",
        ordem: Int = 0
    ) -> FotoAtendimento {
        let nomeArquivo = "\(uuid).jpg"
        let destino = pastaFotos.appendingPathComponent(nomeArquivo)
        let destinoOriginal = pastaFotos.appendingPathComponent("orig_\(nomeArquivo)")
        if let dadosOriginal {
            try? dadosOriginal.write(to: destinoOriginal, options: .atomic)
        } else if !FileManager.default.fileExists(atPath: destinoOriginal.path) {
            try? dadosImagem.write(to: destinoOriginal, options: .atomic)
        }
        try? dadosImagem.write(to: destino, options: .atomic)

        let foto = FotoAtendimento(
            uuid: uuid,
            atendimentoUuid: atendimentoUuid,
            arquivoLocal: destino.path,
            categoria: categoria,
            legenda: legenda,
            anotacoes: anotacoes,
            ordem: ordem,
            revisao: estado.fotos.first(where: { $0.uuid == uuid })?.revisao ?? 0,
            pendenteUpload: true,
            excluido: false
        )
        if let idx = estado.fotos.firstIndex(where: { $0.uuid == uuid }) {
            estado.fotos[idx] = foto
        } else {
            estado.fotos.append(foto)
        }
        salvar()
        return foto
    }

    public func carregarOriginalDaFoto(_ foto: FotoAtendimento) -> Data? {
        let urlAtual = URL(fileURLWithPath: foto.arquivoLocal)
        let urlOriginal = urlAtual.deletingLastPathComponent().appendingPathComponent("orig_\(urlAtual.lastPathComponent)")
        if let dados = try? Data(contentsOf: urlOriginal) { return dados }
        return try? Data(contentsOf: urlAtual)
    }

    public func aplicarFotoServidor(_ foto: FotoAtendimento, bytes: Data?) {
        var copia = foto
        if let bytes {
            let destino = pastaFotos.appendingPathComponent("\(foto.uuid).jpg")
            try? bytes.write(to: destino, options: .atomic)
            copia.arquivoLocal = destino.path
        }
        copia.pendenteUpload = false
        if let idx = estado.fotos.firstIndex(where: { $0.uuid == copia.uuid }) {
            estado.fotos[idx] = copia
        } else {
            estado.fotos.append(copia)
        }
        salvar()
    }

    public func marcarOperacaoConcluida(opId: String, novaRevisao: Int64) {
        guard let op = estado.fila.first(where: { $0.opId == opId }) else { return }
        estado.fila.removeAll { $0.opId == opId }
        if op.entidade == "atendimento", let idx = estado.atendimentos.firstIndex(where: { $0.uuid == op.idEntidade }) {
            estado.atendimentos[idx].revisao = max(novaRevisao, estado.atendimentos[idx].revisao)
        } else if op.entidade == "ficha", let idx = estado.fichas.firstIndex(where: { $0.uuid == op.idEntidade }) {
            estado.fichas[idx].revisao = max(novaRevisao, estado.fichas[idx].revisao)
        }
        salvar()
    }

    public func registrarConflito(_ conflito: ConflitoSinc, opId: String) {
        estado.fila.removeAll { $0.opId == opId }
        estado.conflitos.removeAll { $0.idEntidade == conflito.idEntidade && $0.entidade == conflito.entidade }
        estado.conflitos.append(conflito)
        salvar()
    }

    public func removerConflito(entidade: String, idEntidade: String) {
        estado.conflitos.removeAll { $0.entidade == entidade && $0.idEntidade == idEntidade }
        salvar()
    }

    public func ficharioVeiculos() -> [ResumoModeloVeiculo] {
        let automotivos = estado.atendimentos.filter { !$0.excluido && $0.segmento == .automotivo && !$0.modelo.isEmpty }
        let grupos = Dictionary(grouping: automotivos) {
            "\($0.marca.trimmingCharacters(in: .whitespaces).lowercased())|\($0.modelo.trimmingCharacters(in: .whitespaces).lowercased())|\($0.ano.trimmingCharacters(in: .whitespaces).lowercased())"
        }
        return grupos.values.compactMap { itens -> ResumoModeloVeiculo? in
            let ordenados = itens.sorted { $0.dataServico > $1.dataServico }
            guard let recente = ordenados.first else { return nil }
            return ResumoModeloVeiculo(
                marca: recente.marca,
                modelo: recente.modelo,
                ano: recente.ano,
                totalServicos: ordenados.count,
                ultimaData: recente.dataServico,
                abertura: ordenados.first(where: { !$0.abertura.isEmpty })?.abertura ?? "",
                codigoChave: ordenados.first(where: { !$0.codigoChave.isEmpty })?.codigoChave ?? "",
                chipTransponder: ordenados.first(where: { !$0.chipTransponder.isEmpty })?.chipTransponder ?? ""
            )
        }.sorted { $0.ultimaData > $1.ultimaData }
    }

    public func ficharioResidencial() -> [ResumoResidencialFichario] {
        let residenciais = estado.atendimentos.filter { !$0.excluido && $0.segmento == .residencial }
        let grupos = Dictionary(grouping: residenciais) {
            "\($0.tipoLocal.trimmingCharacters(in: .whitespaces).lowercased())|\($0.marcaFechadura.trimmingCharacters(in: .whitespaces).lowercased())|\($0.modeloFechadura.trimmingCharacters(in: .whitespaces).lowercased())"
        }
        return grupos.values.compactMap { itens -> ResumoResidencialFichario? in
            let ordenados = itens.sorted { $0.dataServico > $1.dataServico }
            guard let recente = ordenados.first else { return nil }
            return ResumoResidencialFichario(
                tipoLocal: recente.tipoLocal.isEmpty ? "Local geral" : recente.tipoLocal,
                marcaFechadura: recente.marcaFechadura,
                modeloFechadura: recente.modeloFechadura,
                tipoCilindro: ordenados.first(where: { !$0.tipoCilindro.isEmpty })?.tipoCilindro ?? "",
                totalServicos: ordenados.count,
                ultimaData: recente.dataServico,
                abertura: ordenados.first(where: { !$0.abertura.isEmpty })?.abertura ?? "",
                codigoChave: ordenados.first(where: { !$0.codigoChave.isEmpty })?.codigoChave ?? ""
            )
        }.sorted { $0.ultimaData > $1.ultimaData }
    }

    public static func normalizar(_ texto: String) -> String {
        texto.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func enfileirar(entidade: String, id: String, tipo: String, baseRevisao: Int64) {
        estado.fila.removeAll { $0.entidade == entidade && $0.idEntidade == id }
        estado.fila.append(OperacaoSaida(entidade: entidade, idEntidade: id, tipo: tipo, baseRevisao: baseRevisao))
    }

    private func carregar() {
        guard let dados = try? Data(contentsOf: arquivoEstado),
              let lido = try? JSONDecoder().decode(EstadoPersistido.self, from: dados) else { return }
        estado = lido
    }

    public func salvar() {
        guard let dados = try? JSONEncoder().encode(estado) else { return }
        try? dados.write(to: arquivoEstado, options: .atomic)
    }
}
