import Foundation
import Security

protocol ArchivioSessione: AnyObject, Sendable {
    var refreshToken: String? { get }
    var utente: Utente? { get }
    func salva(refreshToken: String?, utente: Utente)
    func svuota()
}

/// Refresh token e profilo nel Keychain, leggibili solo con il telefono sbloccato e mai copiati su altri dispositivi.
final class ArchivioKeychain: ArchivioSessione, @unchecked Sendable {
    private let servizio = "it.edodoc.app.sessione"

    var refreshToken: String? {
        leggi("refresh").flatMap { String(data: $0, encoding: .utf8) }
    }

    var utente: Utente? {
        leggi("utente").flatMap { try? JSONDecoder().decode(Utente.self, from: $0) }
    }

    func salva(refreshToken: String?, utente: Utente) {
        if let refreshToken { scrivi("refresh", Data(refreshToken.utf8)) }
        if let dati = try? JSONEncoder().encode(utente) { scrivi("utente", dati) }
    }

    func svuota() {
        for account in ["refresh", "utente"] {
            SecItemDelete(chiave(account) as CFDictionary)
        }
    }

    private func chiave(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: servizio,
         kSecAttrAccount as String: account]
    }

    private func leggi(_ account: String) -> Data? {
        var query = chiave(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var risultato: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &risultato) == errSecSuccess else { return nil }
        return risultato as? Data
    }

    private func scrivi(_ account: String, _ dati: Data) {
        SecItemDelete(chiave(account) as CFDictionary)
        var query = chiave(account)
        query[kSecValueData as String] = dati
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }
}
