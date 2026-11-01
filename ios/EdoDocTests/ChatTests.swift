import XCTest
@testable import EdoDoc

@MainActor
final class ChatTests: XCTestCase {
    private func riga(id: Int, clientId: String?, mittente: String = "PAZIENTE") -> String {
        let client = clientId.map { "\"\($0)\"" } ?? "null"
        return #"{"id":\#(id),"mittente":"\#(mittente)","testo":"Ciao","clientId":\#(client),"inviatoIl":"2026-11-01T10:00:00.5","lettoIl":null}"#
    }

    func testIlReinvioUsaLoStessoClientIdESenzaDoppioni() async throws {
        let trasporto = TrasportoFinto { richiesta, numero in
            guard richiesta.httpMethod == "POST", let corpo = richiesta.httpBody else { return (200, #"{"success":true,"data":[]}"#) }
            if numero == 1 { throw URLError(.notConnectedToInternet) }
            let invio = try JSONDecoder().decode(Invio.self, from: corpo)
            return (200, #"{"success":true,"data":\#(self.rigaStatica(clientId: invio.clientId.uuidString))}"#)
        }
        let api = APIClient(base: base, trasporto: trasporto, archivio: ArchivioMemoria())
        await api.apri(sessione("t"))
        let modello = ChatModel(api: api, canale: .paziente)

        await modello.invia("Ciao")
        XCTAssertEqual(modello.bolle.count, 1)
        XCTAssertEqual(modello.bolle[0].stato, .fallito)

        await modello.riprova(modello.bolle[0])
        XCTAssertEqual(modello.bolle.count, 1)
        XCTAssertEqual(modello.bolle[0].stato, .inviato)
        XCTAssertEqual(modello.bolle[0].serverId, 7)

        let invii = await trasporto.richieste.compactMap { $0.httpBody }.map { try! JSONDecoder().decode(Invio.self, from: $0).clientId }
        XCTAssertEqual(invii.count, 2)
        XCTAssertEqual(Set(invii).count, 1, "Il reinvio deve riusare il clientId")
    }

    nonisolated private func rigaStatica(clientId: String) -> String {
        #"{"id":7,"mittente":"PAZIENTE","testo":"Ciao","clientId":"\#(clientId)","inviatoIl":"2026-11-01T10:00:00","lettoIl":null}"#
    }

    func testAperturaUnisceSenzaDuplicareEIndicaLaPaginaPrecedente() async throws {
        let righe = (1...50).map { riga(id: $0, clientId: nil, mittente: "DOTTORE") }.joined(separator: ",")
        let corpo = #"{"success":true,"data":[\#(righe)]}"#
        let trasporto = TrasportoFinto { richiesta, _ in
            richiesta.httpMethod == "POST" ? (200, #"{"success":true,"data":null}"#) : (200, corpo)
        }
        let api = APIClient(base: base, trasporto: trasporto, archivio: ArchivioMemoria())
        await api.apri(sessione("t"))
        let modello = ChatModel(api: api, canale: .paziente)

        await modello.apri()
        await modello.apri()
        XCTAssertEqual(modello.bolle.count, 50)
        XCTAssertTrue(modello.ciSonoPrecedenti)
        XCTAssertFalse(modello.bolle[0].mia)
        let letti = await trasporto.quante("/app/messaggi/letti")
        XCTAssertEqual(letti, 2)
    }
}
