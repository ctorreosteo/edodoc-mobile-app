import Observation
import SwiftUI
import UIKit
import UserNotifications

enum Configurazione {
    /// Gli account per Apple e Google vivono sul server demo, che contiene solo dati inventati.
    private static let accountProva: Set<String> = ["prova.dottore@edodoc.it", "prova.paziente@edodoc.it"]
    private static let chiaveAmbienteProva = "edodoc.ambienteProva"

    static func isAccountProva(_ email: String) -> Bool {
        accountProva.contains(email.trimmingCharacters(in: .whitespaces).lowercased())
    }

    static var ambienteProva: Bool {
        get { UserDefaults.standard.bool(forKey: chiaveAmbienteProva) }
        set { UserDefaults.standard.set(newValue, forKey: chiaveAmbienteProva) }
    }

    private static var demoURL: URL? {
        let valore = Bundle.main.object(forInfoDictionaryKey: "EDODOC_API_DEMO_URL") as? String
        return valore.flatMap { $0.isEmpty ? nil : URL(string: $0) }
    }

    static var baseURL: URL {
        #if DEBUG
        if let sostituito = UserDefaults.standard.string(forKey: "edodoc.api"), let url = URL(string: sostituito) {
            return url
        }
        #endif
        if ambienteProva, let demoURL { return demoURL }
        let valore = Bundle.main.object(forInfoDictionaryKey: "EDODOC_API_BASE_URL") as? String
        return URL(string: valore ?? "")!
    }
}

/// Dove deve arrivare chi tocca una notifica.
enum Destinazione: Equatable {
    case chat(pazienteId: Int?)
    case feedback(checkId: Int?)

    init?(userInfo: [AnyHashable: Any]) {
        guard let dati = userInfo["data"] as? [String: Any], let tipo = dati["tipo"] as? String else { return nil }
        switch tipo {
        case "MESSAGGIO": self = .chat(pazienteId: Self.numero(dati["pazienteId"]))
        case "FEEDBACK": self = .feedback(checkId: Self.numero(dati["checkId"]))
        default: return nil
        }
    }

    private static func numero(_ valore: Any?) -> Int? {
        (valore as? Int) ?? (valore as? NSNumber)?.intValue ?? (valore as? String).flatMap { Int($0) }
    }
}

enum InvitoLink {
    /// Accetta il link completo, anche incollato con spazi attorno, oppure il solo token.
    static func token(da testo: String) -> String? {
        let pulito = testo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !pulito.isEmpty else { return nil }
        if let url = URL(string: pulito), url.scheme != nil {
            return URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?.first { $0.name == "token" }?.value
        }
        return pulito.contains(" ") ? nil : pulito
    }
}

@MainActor
@Observable
final class AppModel {
    enum Stato: Equatable {
        case anonimo
        case paziente(Utente)
        case dottore(Utente)
    }

    private(set) var stato: Stato = .anonimo
    var invitoInSospeso: String?
    var destinazione: Destinazione?
    var avviso: String?
    private(set) var aggiornamento: Aggiornamento?
    private(set) var pushToken: String?

    @ObservationIgnored let archivio: ArchivioSessione
    @ObservationIgnored private(set) var api: APIClient!
    @ObservationIgnored private var taskAvviso: Task<Void, Never>?

    init(archivio: ArchivioSessione = ArchivioKeychain()) {
        self.archivio = archivio
        api = creaClient()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-edodoc.azzera") { archivio.svuota() }
        #endif
        if archivio.refreshToken != nil, let utente = archivio.utente {
            entra(utente)
        }
    }

    var utente: Utente? {
        switch stato {
        case .anonimo: nil
        case .paziente(let u), .dottore(let u): u
        }
    }

    // MARK: Accesso

    private func creaClient() -> APIClient {
        APIClient(base: Configurazione.baseURL, archivio: archivio) { [weak self] in
            await self?.sessioneTerminata()
        }
    }

    private func scegliAmbiente(prova: Bool) {
        guard Configurazione.ambienteProva != prova else { return }
        Configurazione.ambienteProva = prova
        api = creaClient()
    }

    func accedi(email: String, password: String) async throws {
        struct Login: Encodable { let username, password: String; let mobile: Bool }
        scegliAmbiente(prova: Configurazione.isAccountProva(email))
        let sessione: Sessione = try await api.pubblico("/auth/login", Login(
            username: email.trimmingCharacters(in: .whitespaces), password: password, mobile: true))
        await api.apri(sessione)
        entra(sessione.utente)
    }

    func impostaPassword(token: String, password: String) async throws {
        struct Imposta: Encodable { let token, password: String }
        scegliAmbiente(prova: false)
        let sessione: Sessione = try await api.pubblico("/auth/app/imposta-password", Imposta(token: token, password: password))
        await api.apri(sessione)
        invitoInSospeso = nil
        entra(sessione.utente)
    }

    func esci() async {
        struct Logout: Encodable { let refreshToken: String; let pushToken: String? }
        if let refreshToken = archivio.refreshToken {
            let _: Vuoto? = try? await api.pubblico("/auth/logout", Logout(refreshToken: refreshToken, pushToken: pushToken))
        }
        await api.chiudi()
        stato = .anonimo
    }

    func cambiaPassword(attuale: String, nuova: String) async throws {
        struct Cambia: Encodable { let attuale, nuova: String }
        let _: Vuoto = try await api.post("/auth/cambia-password", Cambia(attuale: attuale, nuova: nuova))
        // Il backend chiude tutti i telefoni dell'utente, compreso questo
        await api.chiudi()
        stato = .anonimo
        mostra("Password aggiornata. Accedi con quella nuova.")
    }

    private func entra(_ utente: Utente) {
        stato = utente.eDottore ? .dottore(utente) : .paziente(utente)
        Task { await chiediNotifiche() }
    }

    private func sessioneTerminata() {
        guard stato != .anonimo else { return }
        stato = .anonimo
        mostra("La sessione è scaduta. Accedi di nuovo.")
    }

    // MARK: Notifiche

    private func chiediNotifiche() async {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-edodoc.senzaPush") { return }
        #endif
        let concesse = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        if concesse { UIApplication.shared.registerForRemoteNotifications() }
    }

    /// Arriva a ogni apertura: il backend scarta i token invalidati da Apple.
    func registra(pushToken token: Data) {
        let esadecimale = token.map { String(format: "%02x", $0) }.joined()
        pushToken = esadecimale
        guard stato != .anonimo else { return }
        struct Dispositivo: Encodable { let pushToken, piattaforma: String }
        Task {
            let _: Vuoto? = try? await api.post("/auth/dispositivi", Dispositivo(pushToken: esadecimale, piattaforma: "IOS"))
        }
    }

    // MARK: Aggiornamento

    /// All'avvio e a ogni ritorno in primo piano. Una volta trovato il blocco non si toglie: basterebbe andare offline.
    func controllaAggiornamento() async {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-edodoc.aggiornamento") {
            aggiornamento = Aggiornamento(versione: "9.9", url: URL(string: "https://apps.apple.com")!)
        }
        #else
        guard aggiornamento == nil, let bundle = Bundle.main.bundleIdentifier,
              let installata = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String else { return }
        aggiornamento = await ControlloAggiornamento().cerca(
            bundle: bundle, installata: installata, sistema: UIDevice.current.systemVersion)
        #endif
    }

    // MARK: Link

    func apri(url: URL) {
        guard url.scheme == "edodoc", url.host == "imposta-password", let token = InvitoLink.token(da: url.absoluteString) else { return }
        invitoInSospeso = token
    }

    // MARK: Avvisi

    func mostra(_ testo: String) {
        taskAvviso?.cancel()
        withAnimation(.spring(duration: 0.4)) { avviso = testo }
        taskAvviso = Task {
            try? await Task.sleep(for: .seconds(3.5))
            guard !Task.isCancelled else { return }
            withAnimation(.spring(duration: 0.4)) { avviso = nil }
        }
    }

    /// Esegue un'azione del dottore e mostra il messaggio del server. true se è andata a buon fine.
    @discardableResult
    func esegui(_ azione: () async throws -> Void, successo: String? = nil) async -> Bool {
        do {
            try await azione()
            if let successo {
                mostra(successo)
                Tocco.conferma()
            }
            return true
        } catch {
            if !Self.annullato(error) {
                mostra(error.localizedDescription)
                Tocco.errore()
            }
            return false
        }
    }

    static func annullato(_ errore: Error) -> Bool {
        errore is CancellationError || (errore as? URLError)?.code == .cancelled
    }
}
