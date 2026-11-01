import Foundation
@testable import EdoDoc

final class ArchivioMemoria: ArchivioSessione, @unchecked Sendable {
    var refreshToken: String?
    var utente: Utente?
    var svuotato = 0

    init(refreshToken: String? = "r1") {
        self.refreshToken = refreshToken
    }

    func salva(refreshToken: String?, utente: Utente) {
        if let refreshToken { self.refreshToken = refreshToken }
        self.utente = utente
    }

    func svuota() {
        refreshToken = nil
        utente = nil
        svuotato += 1
    }
}

/// Backend finto: risponde con la closure e conta le chiamate.
actor TrasportoFinto: Trasporto {
    private(set) var richieste: [URLRequest] = []
    private let risposta: @Sendable (URLRequest, Int) async throws -> (Int, String)

    init(risposta: @escaping @Sendable (URLRequest, Int) async throws -> (Int, String)) {
        self.risposta = risposta
    }

    func invia(_ richiesta: URLRequest) async throws -> (Data, HTTPURLResponse) {
        richieste.append(richiesta)
        let (stato, corpo) = try await risposta(richiesta, richieste.count)
        return (Data(corpo.utf8), HTTPURLResponse(url: richiesta.url!, statusCode: stato, httpVersion: nil, headerFields: nil)!)
    }

    func quante(_ percorso: String) -> Int {
        richieste.filter { $0.url!.path.hasSuffix(percorso) }.count
    }
}

let utenteJSON = #"{"id":1,"username":"a@b.it","nome":"A","cognome":"B","ruolo":"PAZIENTE","pazienteId":16}"#

func sessioneJSON(_ token: String, refresh: String) -> String {
    #"{"success":true,"data":{"token":"\#(token)","refreshToken":"\#(refresh)","utente":\#(utenteJSON)}}"#
}

func sessione(_ token: String) -> Sessione {
    Sessione(token: token, refreshToken: "r1", utente: Utente(id: 1, username: "a", nome: nil, cognome: nil, ruolo: "PAZIENTE", pazienteId: 16))
}

let base = URL(string: "http://test.local/api")!
