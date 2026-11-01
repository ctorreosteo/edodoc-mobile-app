import XCTest
@testable import EdoDoc

final class AmbienteTests: XCTestCase {
    func testRiconosceGliAccountDiProvaSenzaBadareAlleMaiuscole() {
        XCTAssertTrue(Configurazione.isAccountProva(" Prova.Dottore@Edodoc.it "))
        XCTAssertTrue(Configurazione.isAccountProva("prova.paziente@edodoc.it"))
    }

    func testIPazientiVeriRestanoSulServerReale() {
        XCTAssertFalse(Configurazione.isAccountProva("edoardo@edodoc.it"))
        XCTAssertFalse(Configurazione.isAccountProva("prova.paziente@edodoc.it.esempio.it"))
    }
}
