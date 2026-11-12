import Foundation
import Security

/// Cliente da API ChaveiroCar (contracts/openapi.json).
/// Tokens em Keychain; fila offline (Core Data + outbox) entra na próxima iteração.
public final class API: Sendable {
    private let base: URL
    private let sessao: URLSession
    private var acesso: String?

    public init(base: URL = URL(string: "http://localhost:8787")!,
                sessao: URLSession = .shared) {
        self.base = base
        self.sessao = sessao
        self.acesso = ArmazenamentoSeguro.tokenAcesso
    }

    public struct Tokens: Decodable {
        public let acesso: String
        public let refresh: String
        public let expiraEm: Int
    }

    public struct RespostaSessao: Decodable {
        public let tokens: Tokens
    }

    public func entrar(email: String, senha: String) async throws -> Tokens {
        var requisicao = URLRequest(url: base.appendingPathComponent("/api/v1/auth/login"))
        requisicao.httpMethod = "POST"
        requisicao.setValue("application/json", forHTTPHeaderField: "Content-Type")
        requisicao.httpBody = try JSONEncoder().encode(["email": email, "senha": senha])
        let (dados, resposta) = try await sessao.data(for: requisicao)
        guard (resposta as? HTTPURLResponse)?.statusCode == 200 else {
            throw NSError(domain: "ChaveiroCar", code: 401, userInfo: nil)
        }
        let sessaoResposta = try JSONDecoder().decode(RespostaSessao.self, from: dados)
        acesso = sessaoResposta.tokens.acesso
        ArmazenamentoSeguro.salvar(tokenAcesso: acesso!, tokenRefresh: sessaoResposta.tokens.refresh)
        return sessaoResposta.tokens
    }

    public func atendimentos(empresaId: String, busca: String = "") async throws -> [String: Any] {
        var componentes = URLComponents(url: base.appendingPathComponent("/api/v1/empresas/\(empresaId)/atendimentos"), resolvingAgainstBaseURL: false)!
        if !busca.isEmpty { componentes.queryItems = [URLQueryItem(name: "busca", value: busca)] }
        return try await json(url: componentes.url!, metodo: "GET")
    }

    public func sincronizar(empresaId: String, cursor: Int) async throws -> [String: Any] {
        let url = base.appendingPathComponent("/api/v1/empresas/\(empresaId)/sync?cursor=\(cursor)")
        return try await json(url: url, metodo: "GET")
    }

    private func json(url: URL, metodo: String) async throws -> [String: Any] {
        var requisicao = URLRequest(url: url)
        requisicao.httpMethod = metodo
        if let acesso { requisicao.setValue("Bearer \(acesso)", forHTTPHeaderField: "Authorization") }
        let (dados, resposta) = try await sessao.data(for: requisicao)
        guard let http = resposta as? HTTPURLResponse, http.statusCode == 200 else {
            throw NSError(domain: "ChaveiroCar", code: 500, userInfo: nil)
        }
        return try JSONSerialization.jsonObject(with: dados) as? [String: Any] ?? [:]
    }
}

/// Keychain mínimo: tokens nunca em UserDefaults.
public enum ArmazenamentoSeguro {
    private static let servico = "com.willendary.chaveirocar"
    private static let contaAcesso = "api_acesso"
    private static let contaRefresh = "api_refresh"

    public static var tokenAcesso: String? { ler(contaAcesso) }
    public static var tokenRefresh: String? { ler(contaRefresh) }

    public static func salvar(tokenAcesso: String, tokenRefresh: String) {
        gravar(contaAcesso, tokenAcesso)
        gravar(contaRefresh, tokenRefresh)
    }

    public static func limpar() {
        apagar(contaAcesso)
        apagar(contaRefresh)
    }

    private static func ler(_ conta: String) -> String? {
        var consulta = base(conta)
        consulta[kSecAttrReturnData as String] = true
        consulta[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(consulta as CFDictionary, &item) == errSecSuccess,
              let dados = item as? Data,
              let texto = String(data: dados, encoding: .utf8) else { return nil }
        return texto
    }

    private static func gravar(_ conta: String, _ valor: String) {
        apagar(conta)
        guard let dados = valor.data(using: .utf8) else { return }
        var item = base(conta)
        item[kSecValueData as String] = dados
        SecItemAdd(item as CFDictionary, nil)
    }

    private static func apagar(_ conta: String) {
        SecItemDelete(base(conta) as CFDictionary)
    }

    private static func base(_ conta: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servico,
            kSecAttrAccount as String: conta,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
    }
}
