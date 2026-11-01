import XCTest
@testable import EdoDoc

final class AggiornamentoTests: XCTestCase {
    private func controllo(stato: Int = 200, _ corpo: String) -> ControlloAggiornamento {
        ControlloAggiornamento(trasporto: TrasportoFinto { _, _ in (stato, corpo) })
    }

    private func scheda(_ versione: String, minimo: String = "17.0") -> String {
        #"{"resultCount":1,"results":[{"version":"\#(versione)","trackViewUrl":"https://apps.apple.com/it/app/id1","minimumOsVersion":"\#(minimo)"}]}"#
    }

    private func cerca(_ controllo: ControlloAggiornamento, installata: String = "1.0", sistema: String = "26.0") async -> Aggiornamento? {
        await controllo.cerca(bundle: "it.edodoc.app", installata: installata, sistema: sistema)
    }

    func testVersioneNuovaObbliga() async {
        let trovato = await cerca(controllo(scheda("1.1")))
        XCTAssertEqual(trovato, Aggiornamento(versione: "1.1", url: URL(string: "https://apps.apple.com/it/app/id1")!))
    }

    func testStessaVersioneNonBlocca() async {
        let trovato = await cerca(controllo(scheda("1.0.0")))
        XCTAssertNil(trovato)
    }

    func testVersioneInstallataPiuAvantiNonBlocca() async {
        let trovato = await cerca(controllo(scheda("1.0")), installata: "1.1")
        XCTAssertNil(trovato)
    }

    func testSistemaTroppoVecchioNonBlocca() async {
        let trovato = await cerca(controllo(scheda("2.0", minimo: "18.0")), sistema: "17.5")
        XCTAssertNil(trovato)
    }

    func testAppNonInStoreNonBlocca() async {
        let trovato = await cerca(controllo(#"{"resultCount":0,"results":[]}"#))
        XCTAssertNil(trovato)
    }

    func testErroreDelloStoreNonBlocca() async {
        let errore = await cerca(controllo(stato: 503, scheda("9.0")))
        let illeggibile = await cerca(controllo("<html>"))
        let offline = await cerca(ControlloAggiornamento(trasporto: TrasportoFinto { _, _ in throw URLError(.notConnectedToInternet) }))
        XCTAssertNil(errore)
        XCTAssertNil(illeggibile)
        XCTAssertNil(offline)
    }

    func testConfrontoNumerico() {
        XCTAssertTrue(ControlloAggiornamento.successiva("1.10", a: "1.9"))
        XCTAssertTrue(ControlloAggiornamento.successiva("1.0.1", a: "1.0"))
        XCTAssertFalse(ControlloAggiornamento.successiva("1.0", a: "1.0.0"))
        XCTAssertFalse(ControlloAggiornamento.successiva("1.9", a: "1.10"))
    }
}
