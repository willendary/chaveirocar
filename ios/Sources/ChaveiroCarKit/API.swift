import Foundation

/// Cliente da API ChaveiroCar (contracts/openapi.json).
/// Tokens em Keychain; fila offline e cache entram na próxima iteração.
public final class API: Sendable {
    private let base: URL
    private let sessao: URLSession
    private var acesso: String?

    public init(base: URL = URL(string: "http://localhost:8787")!,
                sessao: URLSession = .shared) {
        self.base = base
        self.sessao = sessao
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
