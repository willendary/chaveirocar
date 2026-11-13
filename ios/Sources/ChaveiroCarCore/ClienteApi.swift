import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct EmpresaResumo: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let nome: String
    public let papel: String

    public init(id: String, nome: String, papel: String) {
        self.id = id
        self.nome = nome
        self.papel = papel
    }
}

public struct MembroEmpresa: Codable, Equatable, Identifiable, Sendable {
    public let usuarioId: String
    public let nome: String
    public let email: String
    public let papel: String

    public var id: String { usuarioId }
}

public struct ConviteEmpresa: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let email: String
    public let papel: String
    public let status: String
}

public struct ResultadoOperacaoSync: Codable, Equatable, Sendable {
    public let opId: String
    public let status: String
    public let revisao: Int64
    public let registroJson: String?
}

public struct RespostaPushSync: Codable, Equatable, Sendable {
    public let resultados: [ResultadoOperacaoSync]
    public let cursor: Int64
}

public struct FotoRemotaSync: Codable, Equatable, Sendable {
    public let uuid: String
    public let atendimentoUuid: String
    public let fichaUuid: String
    public let categoria: String
    public let legenda: String
    public let anotacoes: String
    public let ordem: Int
    public let revisao: Int64
    public let url: String
    public let excluido: Bool
}

public struct LotePullSync: Equatable, Sendable {
    public let cursor: Int64
    public let temMais: Bool
    public let atendimentos: [Atendimento]
    public let fichas: [FichaTecnica]
    public let fotos: [FotoRemotaSync]
}

public struct ErroClienteApi: LocalizedError, Equatable, Sendable {
    public let status: Int
    public let mensagem: String
    public init(status: Int, mensagem: String) {
        self.status = status
        self.mensagem = mensagem
    }
    public var errorDescription: String? { mensagem }
}

public final class ClienteApi: @unchecked Sendable {
    public var baseUrl: String
    public var tokenAcesso: String?
    public var tokenRefresh: String?
    private let sessao: URLSession

    public init(
        baseUrl: String = AmbienteApi.producao.urlPadrao,
        tokenAcesso: String? = nil,
        tokenRefresh: String? = nil,
        sessao: URLSession = .shared
    ) {
        self.baseUrl = baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        self.tokenAcesso = tokenAcesso
        self.tokenRefresh = tokenRefresh
        self.sessao = sessao
    }

    public func entrar(email: String, senha: String) async throws -> (emailVerificado: Bool, empresas: [EmpresaResumo]) {
        let resposta = try await requisitarJson("POST", "/api/v1/auth/login", corpo: [
            "email": email,
            "senha": senha,
        ], permitirRenovar: false)
        guardarTokens(resposta)
        return extrairSessao(resposta)
    }

    public func cadastrar(nome: String, email: String, senha: String, empresa: String) async throws -> (emailVerificado: Bool, empresas: [EmpresaResumo]) {
        let resposta = try await requisitarJson("POST", "/api/v1/auth/cadastro", corpo: [
            "nome": nome,
            "email": email,
            "senha": senha,
            "empresa": empresa.isEmpty ? "Minha empresa" : empresa,
        ], permitirRenovar: false)
        guardarTokens(resposta)
        return extrairSessao(resposta)
    }

    public func entrarComGoogle(idToken: String = "", accessToken: String = "", empresa: String = "Minha empresa") async throws -> (email: String, empresas: [EmpresaResumo]) {
        var corpo: [String: Any] = ["empresa": empresa.isEmpty ? "Minha empresa" : empresa]
        if !idToken.isEmpty { corpo["idToken"] = idToken }
        if !accessToken.isEmpty { corpo["accessToken"] = accessToken }
        let resposta = try await requisitarJson("POST", "/api/v1/auth/google-login", corpo: corpo, permitirRenovar: false)
        guardarTokens(resposta)
        let usuario = resposta["usuario"] as? [String: Any]
        let email = (usuario?["email"] as? String) ?? ""
        return (email, extrairSessao(resposta).empresas)
    }

    public func solicitarRedefinicao(email: String) async throws {
        _ = try await requisitarJson("POST", "/api/v1/auth/esqueci-senha", corpo: ["email": email], permitirRenovar: false)
    }

    public func redefinirSenha(token: String, novaSenha: String) async throws {
        _ = try await requisitarJson("POST", "/api/v1/auth/redefinir-senha", corpo: [
            "token": token,
            "senha": novaSenha,
        ], permitirRenovar: false)
    }

    public func aceitarConvite(token: String, nome: String = "", senha: String = "") async throws -> String {
        var corpo: [String: Any] = [:]
        if !nome.isEmpty { corpo["nome"] = nome }
        if !senha.isEmpty { corpo["senha"] = senha }
        let codificado = token.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? token
        let resposta = try await requisitarJson("POST", "/api/v1/convites/\(codificado)/aceitar", corpo: corpo)
        guardarTokens(resposta)
        return (resposta["empresaId"] as? String) ?? ""
    }

    public func listarEmpresas() async throws -> [EmpresaResumo] {
        let resposta = try await requisitarJson("GET", "/api/v1/empresas")
        let itens = (resposta["itens"] as? [[String: Any]]) ?? []
        return itens.map {
            EmpresaResumo(
                id: ($0["id"] as? String) ?? "",
                nome: ($0["nome"] as? String) ?? "",
                papel: ($0["papel"] as? String) ?? "leitor"
            )
        }
    }

    public func listarMembros(empresaId: String) async throws -> [MembroEmpresa] {
        let resposta = try await requisitarJson("GET", "/api/v1/empresas/\(empresaId)/membros")
        let itens = (resposta["itens"] as? [[String: Any]]) ?? []
        return itens.map {
            MembroEmpresa(
                usuarioId: ($0["usuarioId"] as? String) ?? "",
                nome: ($0["nome"] as? String) ?? "",
                email: ($0["email"] as? String) ?? "",
                papel: ($0["papel"] as? String) ?? "colaborador"
            )
        }
    }

    public func convidar(empresaId: String, email: String, papel: String) async throws -> (token: String, emailEnviado: Bool) {
        let resposta = try await requisitarJson("POST", "/api/v1/empresas/\(empresaId)/convites", corpo: [
            "email": email,
            "papel": papel,
        ])
        return (
            (resposta["token"] as? String) ?? "",
            (resposta["emailEnviado"] as? Bool) ?? false
        )
    }

    public func puxarSync(empresaId: String, cursor: Int64) async throws -> LotePullSync {
        let resposta = try await requisitarJson("GET", "/api/v1/empresas/\(empresaId)/sync?cursor=\(cursor)")
        let cursorNovo = (resposta["cursor"] as? NSNumber)?.int64Value ?? cursor
        let temMais = (resposta["temMais"] as? Bool) ?? false
        let atdJson = (resposta["atendimentos"] as? [[String: Any]]) ?? []
        let ficJson = (resposta["fichas"] as? [[String: Any]]) ?? []
        let fotJson = (resposta["fotos"] as? [[String: Any]]) ?? []

        return LotePullSync(
            cursor: cursorNovo,
            temMais: temMais,
            atendimentos: atdJson.map(Self.atendimentoDeDicionario),
            fichas: ficJson.map(Self.fichaDeDicionario),
            fotos: fotJson.map { item in
                FotoRemotaSync(
                    uuid: (item["id"] as? String) ?? "",
                    atendimentoUuid: (item["atendimentoId"] as? String) ?? "",
                    fichaUuid: (item["fichaId"] as? String) ?? "",
                    categoria: (item["categoria"] as? String) ?? "CARRO",
                    legenda: (item["legenda"] as? String) ?? "",
                    anotacoes: (item["anotacoes"] as? String) ?? "[]",
                    ordem: (item["ordem"] as? NSNumber)?.intValue ?? 0,
                    revisao: (item["revisao"] as? NSNumber)?.int64Value ?? 0,
                    url: (item["url"] as? String) ?? "",
                    excluido: item["excluidoEm"] != nil && !(item["excluidoEm"] is NSNull)
                )
            }
        )
    }

    public func enviarLoteSync(empresaId: String, operacoes: [[String: Any]]) async throws -> RespostaPushSync {
        let resposta = try await requisitarJson("POST", "/api/v1/empresas/\(empresaId)/sync", corpo: [
            "operacoes": operacoes,
        ])
        let cursorNovo = (resposta["cursor"] as? NSNumber)?.int64Value ?? 0
        let itens = (resposta["resultados"] as? [[String: Any]]) ?? []
        let resultados = itens.map { item -> ResultadoOperacaoSync in
            var snap: String? = nil
            if let reg = item["registro"] as? [String: Any],
               let data = try? JSONSerialization.data(withJSONObject: reg),
               let txt = String(data: data, encoding: .utf8) {
                snap = txt
            }
            return ResultadoOperacaoSync(
                opId: (item["opId"] as? String) ?? "",
                status: (item["status"] as? String) ?? "",
                revisao: (item["revisao"] as? NSNumber)?.int64Value ?? 0,
                registroJson: snap
            )
        }
        return RespostaPushSync(resultados: resultados, cursor: cursorNovo)
    }

    public func enviarFoto(
        empresaId: String,
        atendimentoUuid: String,
        foto: FotoAtendimento,
        bytes: Data
    ) async throws -> Int64 {
        let fronteira = "----ChaveiroCariOS\(UUID().uuidString)"
        var corpo = Data()
        func campo(_ nome: String, _ valor: String) {
            corpo.append("--\(fronteira)\r\nContent-Disposition: form-data; name=\"\(nome)\"\r\n\r\n\(valor)\r\n".data(using: .utf8)!)
        }
        campo("id", foto.uuid)
        campo("categoria", foto.categoria)
        campo("legenda", foto.legenda)
        campo("ordem", String(foto.ordem))
        campo("anotacoes", foto.anotacoes.isEmpty ? "[]" : foto.anotacoes)
        corpo.append("--\(fronteira)\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"foto.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        corpo.append(bytes)
        corpo.append("\r\n--\(fronteira)--\r\n".data(using: .utf8)!)

        let resposta = try await requisitarBruto(
            metodo: "POST",
            caminho: "/api/v1/empresas/\(empresaId)/atendimentos/\(atendimentoUuid)/fotos",
            contentType: "multipart/form-data; boundary=\(fronteira)",
            corpo: corpo,
            permitirRenovar: true
        )
        let json = (try? JSONSerialization.jsonObject(with: resposta) as? [String: Any]) ?? [:]
        return (json["revisao"] as? NSNumber)?.int64Value ?? 1
    }

    public func baixarFoto(urlRelativa: String) async throws -> Data {
        try await requisitarBruto(metodo: "GET", caminho: urlRelativa, contentType: nil, corpo: nil, permitirRenovar: true)
    }

    public func sair() async {
        var corpo: [String: Any] = [:]
        if let refresh = tokenRefresh, !refresh.isEmpty { corpo["refresh"] = refresh }
        _ = try? await requisitarJson("POST", "/api/v1/auth/sair", corpo: corpo, permitirRenovar: false)
        tokenAcesso = nil
        tokenRefresh = nil
    }

    public static func dicionarioDeAtendimento(_ a: Atendimento) -> [String: Any] {
        [
            "id": a.uuid,
            "segmento": a.segmento.rawValue,
            "placa": a.placa,
            "marca": a.marca,
            "modelo": a.modelo,
            "ano": a.ano,
            "chassi": a.chassi,
            "tipoLocal": a.tipoLocal,
            "ambientePorta": a.ambientePorta,
            "marcaFechadura": a.marcaFechadura,
            "modeloFechadura": a.modeloFechadura,
            "tipoCilindro": a.tipoCilindro,
            "enderecoLocal": a.enderecoLocal,
            "dataServico": a.dataServico,
            "tiposServico": a.tiposServico.isEmpty ? [TipoServico.padraoDoSegmento(a.segmento).rawValue] : a.tiposServico,
            "defeito": a.defeito,
            "solucao": a.solucao,
            "abertura": a.abertura,
            "codigoChave": a.codigoChave,
            "codigoConfiguracao": a.codigoConfiguracao,
            "chipTransponder": a.chipTransponder,
            "ferramenta": a.ferramenta,
            "valorCentavos": a.valorCentavos,
            "clienteNome": a.clienteNome,
            "clienteTelefone": a.clienteTelefone,
            "pago": a.pago,
        ]
    }

    public static func atendimentoDeDicionario(_ d: [String: Any]) -> Atendimento {
        let tipos = (d["tiposServico"] as? [String]) ?? ["COPIA"]
        return Atendimento(
            uuid: (d["id"] as? String) ?? UUID().uuidString.lowercased(),
            segmento: SegmentoAtendimento.deTexto(d["segmento"] as? String),
            placa: (d["placa"] as? String) ?? "",
            marca: (d["marca"] as? String) ?? "",
            modelo: (d["modelo"] as? String) ?? "",
            ano: (d["ano"] as? String) ?? "",
            chassi: (d["chassi"] as? String) ?? "",
            tipoLocal: (d["tipoLocal"] as? String) ?? "",
            ambientePorta: (d["ambientePorta"] as? String) ?? "",
            marcaFechadura: (d["marcaFechadura"] as? String) ?? "",
            modeloFechadura: (d["modeloFechadura"] as? String) ?? "",
            tipoCilindro: (d["tipoCilindro"] as? String) ?? "",
            enderecoLocal: (d["enderecoLocal"] as? String) ?? "",
            dataServico: (d["dataServico"] as? NSNumber)?.int64Value ?? 0,
            tiposServico: tipos,
            defeito: (d["defeito"] as? String) ?? "",
            solucao: (d["solucao"] as? String) ?? "",
            abertura: (d["abertura"] as? String) ?? "",
            codigoChave: (d["codigoChave"] as? String) ?? "",
            codigoConfiguracao: (d["codigoConfiguracao"] as? String) ?? "",
            chipTransponder: (d["chipTransponder"] as? String) ?? "",
            ferramenta: (d["ferramenta"] as? String) ?? "",
            valorCentavos: (d["valorCentavos"] as? NSNumber)?.int64Value ?? 0,
            clienteNome: (d["clienteNome"] as? String) ?? "",
            clienteTelefone: (d["clienteTelefone"] as? String) ?? "",
            pago: (d["pago"] as? Bool) ?? true,
            revisao: (d["revisao"] as? NSNumber)?.int64Value ?? 0,
            atualizadoEm: (d["atualizadoEm"] as? NSNumber)?.int64Value ?? 0,
            excluido: d["excluidoEm"] != nil && !(d["excluidoEm"] is NSNull)
        )
    }

    public static func dicionarioDeFicha(_ f: FichaTecnica) -> [String: Any] {
        [
            "id": f.uuid,
            "equipamento": f.equipamento,
            "marca": f.marca,
            "modelo": f.modelo,
            "ano": f.ano,
            "sistema": f.sistema,
            "descricao": f.descricao,
            "chip": f.chip,
            "chave": f.chave,
            "telecomando": f.telecomando,
            "frequencia": f.frequencia,
            "avisos": f.avisos,
            "funcoes": f.funcoes,
            "manual": f.manual,
            "confirmado": f.confirmado,
            "origem": f.origem,
        ]
    }

    public static func fichaDeDicionario(_ d: [String: Any]) -> FichaTecnica {
        FichaTecnica(
            uuid: (d["id"] as? String) ?? UUID().uuidString.lowercased(),
            equipamento: (d["equipamento"] as? String) ?? "",
            marca: (d["marca"] as? String) ?? "",
            modelo: (d["modelo"] as? String) ?? "",
            ano: (d["ano"] as? String) ?? "",
            sistema: (d["sistema"] as? String) ?? "",
            descricao: (d["descricao"] as? String) ?? "",
            chip: (d["chip"] as? String) ?? "",
            chave: (d["chave"] as? String) ?? "",
            telecomando: (d["telecomando"] as? String) ?? "",
            frequencia: (d["frequencia"] as? String) ?? "",
            avisos: (d["avisos"] as? String) ?? "",
            funcoes: (d["funcoes"] as? String) ?? "",
            manual: (d["manual"] as? String) ?? "",
            confirmado: (d["confirmado"] as? Bool) ?? false,
            origem: (d["origem"] as? String) ?? "",
            revisao: (d["revisao"] as? NSNumber)?.int64Value ?? 0,
            atualizadoEm: (d["atualizadoEm"] as? NSNumber)?.int64Value ?? 0,
            excluido: d["excluidoEm"] != nil && !(d["excluidoEm"] is NSNull)
        )
    }

    private func guardarTokens(_ resposta: [String: Any]) {
        guard let tokens = resposta["tokens"] as? [String: Any] else { return }
        if let acesso = tokens["acesso"] as? String, !acesso.isEmpty { tokenAcesso = acesso }
        if let refresh = tokens["refresh"] as? String, !refresh.isEmpty { tokenRefresh = refresh }
    }

    private func extrairSessao(_ resposta: [String: Any]) -> (emailVerificado: Bool, empresas: [EmpresaResumo]) {
        let usuario = resposta["usuario"] as? [String: Any]
        let verificado = (usuario?["emailVerificado"] as? Bool) ?? false
        let lista = (resposta["empresas"] as? [[String: Any]]) ?? []
        let empresas = lista.map {
            EmpresaResumo(
                id: ($0["id"] as? String) ?? "",
                nome: ($0["nome"] as? String) ?? "",
                papel: ($0["papel"] as? String) ?? "proprietario"
            )
        }
        return (verificado, empresas)
    }

    private func renovarSessao() async -> Bool {
        guard let refresh = tokenRefresh, !refresh.isEmpty else { return false }
        guard let resposta = try? await requisitarJson("POST", "/api/v1/auth/renovar", corpo: ["refresh": refresh], permitirRenovar: false) else {
            return false
        }
        guardarTokens(resposta)
        return tokenAcesso != nil
    }

    private func requisitarJson(
        _ metodo: String,
        _ caminho: String,
        corpo: [String: Any]? = nil,
        permitirRenovar: Bool = true
    ) async throws -> [String: Any] {
        let bytesCorpo = try corpo.map { try JSONSerialization.data(withJSONObject: $0) }
        let dados = try await requisitarBruto(
            metodo: metodo,
            caminho: caminho,
            contentType: corpo != nil ? "application/json" : nil,
            corpo: bytesCorpo,
            permitirRenovar: permitirRenovar
        )
        guard !dados.isEmpty else { return [:] }
        return (try? JSONSerialization.jsonObject(with: dados) as? [String: Any]) ?? [:]
    }

    private func requisitarBruto(
        metodo: String,
        caminho: String,
        contentType: String?,
        corpo: Data?,
        permitirRenovar: Bool
    ) async throws -> Data {
        guard let url = URL(string: baseUrl + caminho) else {
            throw ErroClienteApi(status: 400, mensagem: "URL inválida: \(baseUrl + caminho)")
        }
        var req = URLRequest(url: url)
        req.httpMethod = metodo
        req.timeoutInterval = 30
        if let tokenAcesso, !tokenAcesso.isEmpty {
            req.setValue("Bearer \(tokenAcesso)", forHTTPHeaderField: "Authorization")
        }
        if let contentType {
            req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        req.httpBody = corpo

        let (dados, resposta) = try await sessao.data(for: req)
        let status = (resposta as? HTTPURLResponse)?.statusCode ?? 500
        if status == 401 && permitirRenovar {
            if await renovarSessao() {
                return try await requisitarBruto(
                    metodo: metodo,
                    caminho: caminho,
                    contentType: contentType,
                    corpo: corpo,
                    permitirRenovar: false
                )
            }
        }
        if !(200...299).contains(status) {
            let msg = extrairMensagemErro(dados) ?? "Erro HTTP \(status)"
            throw ErroClienteApi(status: status, mensagem: msg)
        }
        return dados
    }

    private func extrairMensagemErro(_ dados: Data) -> String? {
        guard let obj = try? JSONSerialization.jsonObject(with: dados) as? [String: Any],
              let erro = obj["erro"] as? [String: Any],
              let msg = erro["mensagem"] as? String else { return nil }
        return msg
    }
}
