import Foundation
#if canImport(Security)
import Security
#endif

public struct SessaoSalva: Codable, Equatable, Sendable {
    public var baseUrl: String
    public var ambiente: AmbienteApi
    public var email: String
    public var emailVerificado: Bool
    public var empresaId: String
    public var acesso: String
    public var refresh: String

    public init(
        baseUrl: String = AmbienteApi.producao.urlPadrao,
        ambiente: AmbienteApi = .producao,
        email: String = "",
        emailVerificado: Bool = false,
        empresaId: String = "",
        acesso: String = "",
        refresh: String = ""
    ) {
        self.baseUrl = baseUrl
        self.ambiente = ambiente
        self.email = email
        self.emailVerificado = emailVerificado
        self.empresaId = empresaId
        self.acesso = acesso
        self.refresh = refresh
    }
}

public enum KeychainArmazenamento {
    private static let servico = "com.willendary.chaveirocar.sessao"
    private static let conta = "sessao_atual"

    public static func salvar(_ sessao: SessaoSalva) {
        guard let dados = try? JSONEncoder().encode(sessao) else { return }
        #if canImport(Security)
        let consulta: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servico,
            kSecAttrAccount as String: conta,
        ]
        SecItemDelete(consulta as CFDictionary)
        var atributos = consulta
        atributos[kSecValueData as String] = dados
        atributos[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(atributos as CFDictionary, nil)
        #endif
    }

    public static func carregar() -> SessaoSalva? {
        #if canImport(Security)
        let consulta: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servico,
            kSecAttrAccount as String: conta,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(consulta as CFDictionary, &item)
        guard status == errSecSuccess, let dados = item as? Data else { return nil }
        return try? JSONDecoder().decode(SessaoSalva.self, from: dados)
        #else
        return nil
        #endif
    }

    public static func limpar() {
        #if canImport(Security)
        let consulta: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servico,
            kSecAttrAccount as String: conta,
        ]
        SecItemDelete(consulta as CFDictionary)
        #endif
    }
}
