import Foundation

public enum AmbienteApi: String, CaseIterable, Codable, Sendable {
    case producao = "PRODUCAO"
    case homologacao = "HOMOLOGACAO"
    case desenvolvimento = "DESENVOLVIMENTO"

    public var rotulo: String {
        switch self {
        case .producao: return "Produção"
        case .homologacao: return "Homologação"
        case .desenvolvimento: return "Desenvolvimento"
        }
    }

    public var urlPadrao: String {
        switch self {
        case .producao: return "https://chaveirocar-api.onrender.com"
        case .homologacao: return "https://chaveirocar-api-hml.onrender.com"
        case .desenvolvimento: return "http://localhost:3333"
        }
    }
}

public enum SegmentoAtendimento: String, CaseIterable, Codable, Sendable {
    case automotivo = "AUTOMOTIVO"
    case residencial = "RESIDENCIAL"

    public var rotulo: String {
        switch self {
        case .automotivo: return "Automotivo"
        case .residencial: return "Residencial"
        }
    }

    public static func deTexto(_ valor: String?) -> SegmentoAtendimento {
        guard let v = valor?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else {
            return .automotivo
        }
        return SegmentoAtendimento(rawValue: v) ?? .automotivo
    }
}

public enum TipoServico: String, CaseIterable, Codable, Sendable {
    // Automotivo
    case copia = "COPIA"
    case perdaTotal = "PERDA_TOTAL"
    case abertura = "ABERTURA"
    case consertoIgnicao = "CONSERTO_IGNICAO"
    case consertoFechadura = "CONSERTO_FECHADURA"
    case programacao = "PROGRAMACAO"
    case telecomando = "TELECOMANDO"
    case imobilizador = "IMOBILIZADOR"
    // Residencial
    case aberturaResidencial = "ABERTURA_RESIDENCIAL"
    case copiaChave = "COPIA_CHAVE"
    case trocaSegredo = "TROCA_SEGREDO"
    case instalacaoFechadura = "INSTALACAO_FECHADURA"
    case trocaCilindro = "TROCA_CILINDRO"
    case fechaduraDigital = "FECHADURA_DIGITAL"
    case reparoFechadura = "REPARO_FECHADURA"
    // Comum
    case outro = "OUTRO"

    public var rotulo: String {
        switch self {
        case .copia: return "Cópia"
        case .perdaTotal: return "Perda total"
        case .abertura: return "Abertura"
        case .consertoIgnicao: return "Ignição"
        case .consertoFechadura: return "Fechadura"
        case .programacao: return "Programação"
        case .telecomando: return "Telecomando"
        case .imobilizador: return "Imobilizador"
        case .aberturaResidencial: return "Abertura residencial"
        case .copiaChave: return "Cópia de chave"
        case .trocaSegredo: return "Troca de segredo"
        case .instalacaoFechadura: return "Instalação de fechadura"
        case .trocaCilindro: return "Troca de cilindro"
        case .fechaduraDigital: return "Fechadura digital"
        case .reparoFechadura: return "Reparo de fechadura"
        case .outro: return "Outro"
        }
    }

    public static func doSegmento(_ segmento: SegmentoAtendimento) -> [TipoServico] {
        switch segmento {
        case .automotivo:
            return [.copia, .perdaTotal, .abertura, .consertoIgnicao, .consertoFechadura, .programacao, .telecomando, .imobilizador, .outro]
        case .residencial:
            return [.aberturaResidencial, .copiaChave, .trocaSegredo, .instalacaoFechadura, .trocaCilindro, .fechaduraDigital, .reparoFechadura, .outro]
        }
    }

    public static func padraoDoSegmento(_ segmento: SegmentoAtendimento) -> TipoServico {
        segmento == .residencial ? .aberturaResidencial : .copia
    }
}

public struct SetaAnotacao: Codable, Equatable, Identifiable, Sendable {
    public var id: UUID
    public var inicioX: Double
    public var inicioY: Double
    public var fimX: Double
    public var fimY: Double
    public var cor: String
    public var espessura: Double

    public init(
        id: UUID = UUID(),
        inicioX: Double,
        inicioY: Double,
        fimX: Double,
        fimY: Double,
        cor: String = "#FF3B30",
        espessura: Double = 6.0
    ) {
        self.id = id
        self.inicioX = min(max(inicioX, 0), 1)
        self.inicioY = min(max(inicioY, 0), 1)
        self.fimX = min(max(fimX, 0), 1)
        self.fimY = min(max(fimY, 0), 1)
        self.cor = cor
        self.espessura = min(max(espessura, 2), 24)
    }

    enum CodingKeys: String, CodingKey {
        case inicioX, inicioY, fimX, fimY, cor, espessura
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = UUID()
        self.inicioX = min(max(try c.decodeIfPresent(Double.self, forKey: .inicioX) ?? 0, 0), 1)
        self.inicioY = min(max(try c.decodeIfPresent(Double.self, forKey: .inicioY) ?? 0, 0), 1)
        self.fimX = min(max(try c.decodeIfPresent(Double.self, forKey: .fimX) ?? 0, 0), 1)
        self.fimY = min(max(try c.decodeIfPresent(Double.self, forKey: .fimY) ?? 0, 0), 1)
        self.cor = try c.decodeIfPresent(String.self, forKey: .cor) ?? "#FF3B30"
        self.espessura = min(max(try c.decodeIfPresent(Double.self, forKey: .espessura) ?? 6.0, 2), 24)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(inicioX, forKey: .inicioX)
        try c.encode(inicioY, forKey: .inicioY)
        try c.encode(fimX, forKey: .fimX)
        try c.encode(fimY, forKey: .fimY)
        try c.encode(cor, forKey: .cor)
        try c.encode(espessura, forKey: .espessura)
    }

    public static func ler(_ json: String?) -> [SetaAnotacao] {
        guard let texto = json?.trimmingCharacters(in: .whitespacesAndNewlines),
              !texto.isEmpty,
              let data = texto.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([SetaAnotacao].self, from: data)) ?? []
    }

    public static func serializar(_ setas: [SetaAnotacao]) -> String {
        guard !setas.isEmpty,
              let data = try? JSONEncoder().encode(setas),
              let texto = String(data: data, encoding: .utf8) else { return "[]" }
        return texto
    }
}

public struct FotoAtendimento: Codable, Equatable, Identifiable, Sendable {
    public var uuid: String
    public var atendimentoUuid: String
    public var fichaUuid: String
    public var arquivoLocal: String
    public var categoria: String
    public var legenda: String
    public var anotacoes: String
    public var ordem: Int
    public var revisao: Int64
    public var pendenteUpload: Bool
    public var excluido: Bool

    public var id: String { uuid }
    public var temAnotacoes: Bool { !SetaAnotacao.ler(anotacoes).isEmpty }

    public init(
        uuid: String = UUID().uuidString.lowercased(),
        atendimentoUuid: String = "",
        fichaUuid: String = "",
        arquivoLocal: String = "",
        categoria: String = "CARRO",
        legenda: String = "",
        anotacoes: String = "[]",
        ordem: Int = 0,
        revisao: Int64 = 0,
        pendenteUpload: Bool = false,
        excluido: Bool = false
    ) {
        self.uuid = uuid
        self.atendimentoUuid = atendimentoUuid
        self.fichaUuid = fichaUuid
        self.arquivoLocal = arquivoLocal
        self.categoria = categoria
        self.legenda = legenda
        self.anotacoes = anotacoes
        self.ordem = ordem
        self.revisao = revisao
        self.pendenteUpload = pendenteUpload
        self.excluido = excluido
    }
}

public struct Atendimento: Codable, Equatable, Identifiable, Sendable {
    public static let tiposLocalResidencial = [
        "Casa", "Apartamento", "Comércio", "Condomínio", "Portão", "Outro"
    ]

    public var uuid: String
    public var segmento: SegmentoAtendimento
    public var placa: String
    public var marca: String
    public var modelo: String
    public var ano: String
    public var chassi: String
    public var tipoLocal: String
    public var ambientePorta: String
    public var marcaFechadura: String
    public var modeloFechadura: String
    public var tipoCilindro: String
    public var enderecoLocal: String
    public var dataServico: Int64
    public var tiposServico: [String]
    public var defeito: String
    public var solucao: String
    public var abertura: String
    public var codigoChave: String
    public var codigoConfiguracao: String
    public var chipTransponder: String
    public var ferramenta: String
    public var valorCentavos: Int64
    public var clienteNome: String
    public var clienteTelefone: String
    public var pago: Bool
    public var revisao: Int64
    public var atualizadoEm: Int64
    public var excluido: Bool

    public var id: String { uuid }
    public var ehResidencial: Bool { segmento == .residencial }

    public var identificacaoPrincipal: String {
        if ehResidencial {
            if !tipoLocal.isEmpty && !ambientePorta.isEmpty { return "\(tipoLocal) · \(ambientePorta)" }
            if !tipoLocal.isEmpty { return tipoLocal }
            if !ambientePorta.isEmpty { return ambientePorta }
            if !marcaFechadura.isEmpty || !modeloFechadura.isEmpty {
                return "\(marcaFechadura) \(modeloFechadura)".trimmingCharacters(in: .whitespaces)
            }
            return "Serviço residencial"
        }
        if !placa.isEmpty { return placa }
        let veiculo = "\(marca) \(modelo)".trimmingCharacters(in: .whitespaces)
        return veiculo.isEmpty ? "Sem placa" : veiculo
    }

    public var subtituloPrincipal: String {
        if ehResidencial {
            let fechadura = [marcaFechadura, modeloFechadura, tipoCilindro]
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .joined(separator: " · ")
            return fechadura.isEmpty ? enderecoLocal : fechadura
        }
        return [marca, modelo, ano]
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    public init(
        uuid: String = UUID().uuidString.lowercased(),
        segmento: SegmentoAtendimento = .automotivo,
        placa: String = "",
        marca: String = "",
        modelo: String = "",
        ano: String = "",
        chassi: String = "",
        tipoLocal: String = "",
        ambientePorta: String = "",
        marcaFechadura: String = "",
        modeloFechadura: String = "",
        tipoCilindro: String = "",
        enderecoLocal: String = "",
        dataServico: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        tiposServico: [String] = ["COPIA"],
        defeito: String = "",
        solucao: String = "",
        abertura: String = "",
        codigoChave: String = "",
        codigoConfiguracao: String = "",
        chipTransponder: String = "",
        ferramenta: String = "",
        valorCentavos: Int64 = 0,
        clienteNome: String = "",
        clienteTelefone: String = "",
        pago: Bool = true,
        revisao: Int64 = 0,
        atualizadoEm: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        excluido: Bool = false
    ) {
        self.uuid = uuid
        self.segmento = segmento
        self.placa = placa
        self.marca = marca
        self.modelo = modelo
        self.ano = ano
        self.chassi = chassi
        self.tipoLocal = tipoLocal
        self.ambientePorta = ambientePorta
        self.marcaFechadura = marcaFechadura
        self.modeloFechadura = modeloFechadura
        self.tipoCilindro = tipoCilindro
        self.enderecoLocal = enderecoLocal
        self.dataServico = dataServico
        self.tiposServico = tiposServico
        self.defeito = defeito
        self.solucao = solucao
        self.abertura = abertura
        self.codigoChave = codigoChave
        self.codigoConfiguracao = codigoConfiguracao
        self.chipTransponder = chipTransponder
        self.ferramenta = ferramenta
        self.valorCentavos = valorCentavos
        self.clienteNome = clienteNome
        self.clienteTelefone = clienteTelefone
        self.pago = pago
        self.revisao = revisao
        self.atualizadoEm = atualizadoEm
        self.excluido = excluido
    }

    public func validar() throws {
        switch segmento {
        case .automotivo:
            if modelo.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                throw ErroValidacao("Informe o modelo do veículo.")
            }
            let p = placa.uppercased().filter { $0.isLetter || $0.isNumber }
            if !p.isEmpty {
                let formatoValido = p.count == 7
                if !formatoValido {
                    throw ErroValidacao("Placa inválida. Use 7 caracteres (ABC1234 ou ABC1D23) ou deixe em branco.")
                }
            }
        case .residencial:
            let temLocal = !tipoLocal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                !ambientePorta.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                !marcaFechadura.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                !modeloFechadura.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            if !temLocal {
                throw ErroValidacao("Informe o tipo de local, porta/ambiente ou fechadura.")
            }
        }
    }
}

public struct ErroValidacao: LocalizedError, Equatable, Sendable {
    public let mensagem: String
    public init(_ mensagem: String) { self.mensagem = mensagem }
    public var errorDescription: String? { mensagem }
}

public struct FichaTecnica: Codable, Equatable, Identifiable, Sendable {
    public var uuid: String
    public var equipamento: String
    public var marca: String
    public var modelo: String
    public var ano: String
    public var sistema: String
    public var descricao: String
    public var chip: String
    public var chave: String
    public var telecomando: String
    public var frequencia: String
    public var avisos: String
    public var funcoes: String
    public var manual: String
    public var confirmado: Bool
    public var origem: String
    public var revisao: Int64
    public var atualizadoEm: Int64
    public var excluido: Bool

    public var id: String { uuid }

    public init(
        uuid: String = UUID().uuidString.lowercased(),
        equipamento: String = "",
        marca: String = "",
        modelo: String = "",
        ano: String = "",
        sistema: String = "",
        descricao: String = "",
        chip: String = "",
        chave: String = "",
        telecomando: String = "",
        frequencia: String = "",
        avisos: String = "",
        funcoes: String = "",
        manual: String = "",
        confirmado: Bool = false,
        origem: String = "",
        revisao: Int64 = 0,
        atualizadoEm: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        excluido: Bool = false
    ) {
        self.uuid = uuid
        self.equipamento = equipamento
        self.marca = marca
        self.modelo = modelo
        self.ano = ano
        self.sistema = sistema
        self.descricao = descricao
        self.chip = chip
        self.chave = chave
        self.telecomando = telecomando
        self.frequencia = frequencia
        self.avisos = avisos
        self.funcoes = funcoes
        self.manual = manual
        self.confirmado = confirmado
        self.origem = origem
        self.revisao = revisao
        self.atualizadoEm = atualizadoEm
        self.excluido = excluido
    }
}
