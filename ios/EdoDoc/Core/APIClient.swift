import Foundation

protocol Trasporto: Sendable {
    func invia(_ richiesta: URLRequest) async throws -> (Data, HTTPURLResponse)
}

struct TrasportoURLSession: Trasporto {
    let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func invia(_ richiesta: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (dati, risposta) = try await session.data(for: richiesta)
        guard let http = risposta as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (dati, http)
    }
}

enum ErroreAPI: LocalizedError, Equatable {
    case offline
    case sessioneScaduta
    case server(stato: Int, messaggio: String)
    case risposta

    var errorDescription: String? {
        switch self {
        case .offline: "Sei offline. Controlla la connessione e riprova."
        case .sessioneScaduta: "La sessione è scaduta. Accedi di nuovo."
        case .server(_, let messaggio): messaggio
        case .risposta: "Risposta inattesa dal server. Riprova tra poco."
        }
    }

    var conflitto: Bool {
        if case .server(409, _) = self { return true }
        return false
    }
}

struct Busta<T: Decodable>: Decodable {
    let success: Bool
    let message: String?
    let data: T?
    let error: String?
}

/// Per le risposte che portano solo l'esito.
struct Vuoto: Codable, Sendable {}

struct Risposta<T> {
    let valore: T
    let messaggio: String?
}

struct ParteFile {
    let campo: String
    let nomeFile: String
    let tipo: String
    let dati: Data
}

/// Unico punto di contatto con il backend. Il token vive in memoria, il refresh token nel Keychain.
/// Con più richieste in corso sul 401 parte un solo /rinnova: un secondo rinnovo con lo stesso token sarebbe rifiutato.
actor APIClient {
    private let base: URL
    private let trasporto: Trasporto
    private let archivio: ArchivioSessione
    private let sessioneChiusa: @Sendable () async -> Void
    private var token: String?
    private var rinnovo: Task<String, Error>?

    private static let decoder = JSONDecoder()
    private static let encoder = JSONEncoder()

    init(base: URL, trasporto: Trasporto = TrasportoURLSession(), archivio: ArchivioSessione,
         sessioneChiusa: @escaping @Sendable () async -> Void = {}) {
        self.base = base
        self.trasporto = trasporto
        self.archivio = archivio
        self.sessioneChiusa = sessioneChiusa
    }

    // MARK: Sessione

    func apri(_ sessione: Sessione) {
        token = sessione.token
        archivio.salva(refreshToken: sessione.refreshToken, utente: sessione.utente)
    }

    func chiudi() {
        token = nil
        rinnovo?.cancel()
        rinnovo = nil
        archivio.svuota()
    }

    private func tokenValido() async throws -> String {
        if let token { return token }
        return try await rinnova(dopo: nil)
    }

    private func rinnova(dopo usato: String?) async throws -> String {
        // Un'altra richiesta ha già rinnovato mentre questa aspettava il suo 401
        if let token, token != usato { return token }
        if let rinnovo { return try await rinnovo.value }
        let lavoro = Task { () throws -> String in
            try await self.eseguiRinnovo()
        }
        rinnovo = lavoro
        defer { rinnovo = nil }
        return try await lavoro.value
    }

    private func eseguiRinnovo() async throws -> String {
        guard let refreshToken = archivio.refreshToken else {
            await scaduta()
            throw ErroreAPI.sessioneScaduta
        }
        let corpo = try Self.encoder.encode(["refreshToken": refreshToken])
        var richiesta = URLRequest(url: url("/auth/rinnova", query: []))
        richiesta.httpMethod = "POST"
        richiesta.httpBody = corpo
        richiesta.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let (dati, http) = try await inviaConErroriDiRete(richiesta)
        // 400: refresh token scaduto o già usato. Un 5xx non deve buttare fuori nessuno.
        if http.statusCode == 400 || http.statusCode == 401 {
            await scaduta()
            throw ErroreAPI.sessioneScaduta
        }
        guard (200..<300).contains(http.statusCode),
              let sessione = try? Self.decoder.decode(Busta<Sessione>.self, from: dati).data else {
            throw Self.errore(stato: http.statusCode, dati: dati)
        }
        apri(sessione)
        return sessione.token
    }

    private func scaduta() async {
        chiudi()
        await sessioneChiusa()
    }

    // MARK: Richieste

    func get<T: Decodable>(_ percorso: String, _ query: [URLQueryItem] = []) async throws -> T {
        try await risposta("GET", percorso, query: query, corpo: nil).valore
    }

    func post<T: Decodable>(_ percorso: String, _ query: [URLQueryItem] = []) async throws -> T {
        try await risposta("POST", percorso, query: query, corpo: nil).valore
    }

    func post<T: Decodable, C: Encodable>(_ percorso: String, _ corpo: C) async throws -> T {
        try await risposta("POST", percorso, query: [], corpo: Self.encoder.encode(corpo)).valore
    }

    func put<T: Decodable, C: Encodable>(_ percorso: String, _ corpo: C) async throws -> T {
        try await risposta("PUT", percorso, query: [], corpo: Self.encoder.encode(corpo)).valore
    }

    /// Come post e put, ma restituisce anche il messaggio del server.
    func invia<T: Decodable, C: Encodable>(_ metodo: String, _ percorso: String, _ corpo: C) async throws -> Risposta<T> {
        try await risposta(metodo, percorso, corpo: Self.encoder.encode(corpo))
    }

    func delete(_ percorso: String) async throws {
        let _: Risposta<Vuoto> = try await risposta("DELETE", percorso, query: [], corpo: nil)
    }

    /// Accesso, primo accesso: niente token e niente rinnovo sul 401.
    func pubblico<T: Decodable, C: Encodable>(_ percorso: String, _ corpo: C) async throws -> T {
        var richiesta = URLRequest(url: url(percorso, query: []))
        richiesta.httpMethod = "POST"
        richiesta.httpBody = try Self.encoder.encode(corpo)
        richiesta.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let (dati, http) = try await inviaConErroriDiRete(richiesta)
        guard (200..<300).contains(http.statusCode) else { throw Self.errore(stato: http.statusCode, dati: dati) }
        return try Self.decodifica(dati).valore
    }

    func risposta<T: Decodable>(_ metodo: String, _ percorso: String, query: [URLQueryItem] = [], corpo: Data?,
                                contentType: String = "application/json") async throws -> Risposta<T> {
        var richiesta = URLRequest(url: url(percorso, query: query))
        richiesta.httpMethod = metodo
        richiesta.httpBody = corpo
        if corpo != nil { richiesta.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        return try Self.decodifica(try await autenticata(richiesta))
    }

    func multipart<T: Decodable>(_ percorso: String, campi: [String: String], file: ParteFile) async throws -> Risposta<T> {
        let confine = "edodoc-\(UUID().uuidString)"
        var corpo = Data()
        func riga(_ testo: String) { corpo.append(Data(testo.utf8)) }
        for (nome, valore) in campi.sorted(by: { $0.key < $1.key }) {
            riga("--\(confine)\r\nContent-Disposition: form-data; name=\"\(nome)\"\r\n\r\n\(valore)\r\n")
        }
        riga("--\(confine)\r\nContent-Disposition: form-data; name=\"\(file.campo)\"; filename=\"\(file.nomeFile)\"\r\n")
        riga("Content-Type: \(file.tipo)\r\n\r\n")
        corpo.append(file.dati)
        riga("\r\n--\(confine)--\r\n")
        return try await risposta("POST", percorso, corpo: corpo, contentType: "multipart/form-data; boundary=\(confine)")
    }

    /// PDF e audio: scarica su un file temporaneo con Bearer.
    func scarica(_ percorso: String, nomeFile: String) async throws -> URL {
        let dati = try await autenticata(URLRequest(url: url(percorso, query: [])))
        let cartella = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: cartella, withIntermediateDirectories: true)
        let destinazione = cartella.appendingPathComponent(nomeFile)
        try dati.write(to: destinazione, options: [.atomic, .completeFileProtection])
        return destinazione
    }

    nonisolated func urlCopertina(_ id: UUID) -> URL {
        base.appendingPathComponent("media/copertine/\(id.uuidString.lowercased())")
    }

    private func autenticata(_ originale: URLRequest) async throws -> Data {
        var richiesta = originale
        let usato = try await tokenValido()
        richiesta.setValue("Bearer \(usato)", forHTTPHeaderField: "Authorization")
        var (dati, http) = try await inviaConErroriDiRete(richiesta)
        if http.statusCode == 401 {
            let nuovo = try await rinnova(dopo: usato)
            richiesta.setValue("Bearer \(nuovo)", forHTTPHeaderField: "Authorization")
            (dati, http) = try await inviaConErroriDiRete(richiesta)
            if http.statusCode == 401 {
                await scaduta()
                throw ErroreAPI.sessioneScaduta
            }
        }
        guard (200..<300).contains(http.statusCode) else { throw Self.errore(stato: http.statusCode, dati: dati) }
        return dati
    }

    private func inviaConErroriDiRete(_ richiesta: URLRequest) async throws -> (Data, HTTPURLResponse) {
        do {
            return try await trasporto.invia(richiesta)
        } catch let errore as URLError where errore.code != .cancelled {
            throw ErroreAPI.offline
        }
    }

    private func url(_ percorso: String, query: [URLQueryItem]) -> URL {
        var componenti = URLComponents(url: base.appendingPathComponent(percorso), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { componenti.queryItems = query }
        return componenti.url!
    }

    private static func decodifica<T: Decodable>(_ dati: Data) throws -> Risposta<T> {
        do {
            let busta = try decoder.decode(Busta<T>.self, from: dati)
            if let valore = busta.data {
                return Risposta(valore: valore, messaggio: busta.message)
            }
            if let vuoto = Vuoto() as? T {
                return Risposta(valore: vuoto, messaggio: busta.message)
            }
            throw ErroreAPI.risposta
        } catch let errore as DecodingError {
            #if DEBUG
            print("Decodifica \(T.self): \(errore)")
            #endif
            throw ErroreAPI.risposta
        }
    }

    private static func errore(stato: Int, dati: Data) -> ErroreAPI {
        let busta = try? decoder.decode(Busta<Vuoto>.self, from: dati)
        return .server(stato: stato, messaggio: busta?.message ?? busta?.error ?? "Qualcosa non ha funzionato. Riprova tra poco.")
    }
}
