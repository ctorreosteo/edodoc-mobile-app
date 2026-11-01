import XCTest
@testable import EdoDoc

final class RinnovoTests: XCTestCase {
    private func backendConRotazione() -> TrasportoFinto {
        TrasportoFinto { richiesta, _ in
            if richiesta.url!.path.hasSuffix("/auth/rinnova") {
                try await Task.sleep(for: .milliseconds(50))
                return (200, sessioneJSON("nuovo", refresh: "r2"))
            }
            if richiesta.value(forHTTPHeaderField: "Authorization") == "Bearer nuovo" {
                return (200, #"{"success":true,"data":{"id":1}}"#)
            }
            return (401, #"{"success":false,"message":"Sessione scaduta"}"#)
        }
    }

    private struct Riga: Decodable { let id: Int }

    func testConRichiesteConcorrentiPartUnSoloRinnovo() async throws {
        let trasporto = backendConRotazione()
        let archivio = ArchivioMemoria()
        let api = APIClient(base: base, trasporto: trasporto, archivio: archivio)
        await api.apri(sessione("vecchio"))

        let risultati = try await withThrowingTaskGroup(of: Int.self) { gruppo in
            for _ in 0..<12 {
                gruppo.addTask {
                    let riga: Riga = try await api.get("/app/riepilogo")
                    return riga.id
                }
            }
            return try await gruppo.reduce(into: [Int]()) { $0.append($1) }
        }

        XCTAssertEqual(risultati.count, 12)
        let rinnovi = await trasporto.quante("/auth/rinnova")
        XCTAssertEqual(rinnovi, 1)
        XCTAssertEqual(archivio.refreshToken, "r2")
    }

    func testSenzaTokenInMemoriaRinnovaPrimaDellaRichiesta() async throws {
        let trasporto = backendConRotazione()
        let api = APIClient(base: base, trasporto: trasporto, archivio: ArchivioMemoria())
        let _: Riga = try await api.get("/app/riepilogo")
        let rinnovi = await trasporto.quante("/auth/rinnova")
        let richieste = await trasporto.quante("/app/riepilogo")
        XCTAssertEqual(rinnovi, 1)
        XCTAssertEqual(richieste, 1)
    }

    func testRinnovoRifiutatoTornaAlLoginUnaVolta() async {
        let trasporto = TrasportoFinto { richiesta, _ in
            richiesta.url!.path.hasSuffix("/auth/rinnova") ? (400, #"{"success":false,"message":"Sessione scaduta"}"#) : (401, "{}")
        }
        let archivio = ArchivioMemoria()
        let chiusure = Contatore()
        let api = APIClient(base: base, trasporto: trasporto, archivio: archivio) { await chiusure.incrementa() }
        await api.apri(sessione("vecchio"))

        do {
            let _: Riga = try await api.get("/app/riepilogo")
            XCTFail("Doveva fallire")
        } catch {
            XCTAssertEqual(error as? ErroreAPI, .sessioneScaduta)
        }
        XCTAssertNil(archivio.refreshToken)
        let quante = await chiusure.valore
        XCTAssertEqual(quante, 1)
    }

    func testUnErroreDelServerSulRinnovoNonButtaFuori() async {
        let trasporto = TrasportoFinto { richiesta, _ in
            richiesta.url!.path.hasSuffix("/auth/rinnova") ? (503, "{}") : (401, "{}")
        }
        let archivio = ArchivioMemoria()
        let api = APIClient(base: base, trasporto: trasporto, archivio: archivio)
        await api.apri(sessione("vecchio"))

        do {
            let _: Riga = try await api.get("/app/riepilogo")
            XCTFail("Doveva fallire")
        } catch {}
        XCTAssertEqual(archivio.refreshToken, "r1")
        XCTAssertEqual(archivio.svuotato, 0)
    }

    func testPostConQueryVaNellURLENonNelCorpo() async throws {
        let trasporto = TrasportoFinto { _, _ in (200, #"{"success":true,"data":{"id":1}}"#) }
        let api = APIClient(base: base, trasporto: trasporto, archivio: ArchivioMemoria())
        await api.apri(sessione("t"))
        let _: Riga = try await api.post("/task/1/stato", [URLQueryItem(name: "fatto", value: "true")])
        let richiesta = await trasporto.richieste.last!
        XCTAssertEqual(richiesta.url?.query, "fatto=true")
        XCTAssertNil(richiesta.httpBody)
    }
}

actor Contatore {
    private(set) var valore = 0
    func incrementa() { valore += 1 }
}
