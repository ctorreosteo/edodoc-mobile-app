import XCTest

/// Non serve il backend: l'aggiornamento è simulato all'avvio.
final class AggiornamentoUITests: XCTestCase {
    func testVersioneNuovaChiudeFuoriDallApp() {
        let app = XCUIApplication()
        app.launchArguments = ["-edodoc.azzera", "-edodoc.senzaPush", "-edodoc.aggiornamento"]
        app.launch()
        XCTAssertTrue(app.buttons["aggiornamento.apri"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.textFields["accesso.email"].exists)

        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.buttons["aggiornamento.apri"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.textFields["accesso.email"].exists)
    }
}
