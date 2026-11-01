import XCTest

/// Guida l'app vera contro il backend locale e salva le schermate principali.
/// Cartella e tema arrivano da TEST_RUNNER_EDODOC_SHOTS e TEST_RUNNER_EDODOC_TEMA.
final class PercorsiTests: XCTestCase {
    private var app: XCUIApplication!
    private var cartella: URL!
    private var tema = "scuro"

    override func setUp() {
        continueAfterFailure = true
        let ambiente = ProcessInfo.processInfo.environment
        cartella = URL(fileURLWithPath: ambiente["EDODOC_SHOTS"] ?? NSTemporaryDirectory())
        tema = ambiente["EDODOC_TEMA"] ?? "scuro"
        try? FileManager.default.createDirectory(at: cartella, withIntermediateDirectories: true)
        app = XCUIApplication()
        app.launchArguments = ["-edodoc.azzera", "-edodoc.senzaPush"]
        app.launch()
    }

    private func scatta(_ nome: String) {
        sleep(1)
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: cartella.appendingPathComponent("\(nome)-\(tema).png"))
    }

    private func accedi(_ email: String, _ password: String) {
        let campo = app.textFields["accesso.email"]
        XCTAssertTrue(campo.waitForExistence(timeout: 10))
        scatta("00-accesso")
        campo.tap()
        campo.typeText(email)
        // Con la tastiera che sale il campo si sposta: si aspetta che sia fermo
        let segreto = app.secureTextFields["accesso.password"]
        XCTAssertTrue(segreto.waitForExistence(timeout: 3))
        sleep(1)
        segreto.tap()
        segreto.typeText(password)
        app.buttons["accesso.invia"].tap()
    }

    private func scheda(_ nome: String) {
        let bottone = app.tabBars.buttons[nome]
        XCTAssertTrue(bottone.waitForExistence(timeout: 8), nome)
        bottone.tap()
    }

    private func indietro() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    func testPaziente() {
        accedi("luca.bruno@esempio.it", "demo-paziente-2026")
        XCTAssertTrue(app.staticTexts["oggi.frase"].waitForExistence(timeout: 15))
        scatta("01-paziente-oggi")

        if app.buttons["oggi.azione"].exists, app.buttons["oggi.azione"].label == "Ascolta" {
            app.buttons["oggi.azione"].tap()
            XCTAssertTrue(app.buttons["vocale.play"].waitForExistence(timeout: 10))
            app.buttons["vocale.play"].tap()
            sleep(1)
            scatta("02-paziente-vocale")
            app.swipeDown(velocity: .fast)
        } else {
            let rileggi = app.buttons["oggi.rileggi"]
            if rileggi.waitForExistence(timeout: 3) {
                rileggi.tap()
                XCTAssertTrue(app.buttons["vocale.play"].waitForExistence(timeout: 10))
                app.buttons["vocale.play"].tap()
                scatta("02-paziente-vocale")
                app.swipeDown(velocity: .fast)
                sleep(1)
            }
        }

        scheda("Percorso")
        scatta("03-paziente-percorso")
        let fatture = app.buttons["percorso.fatture"]
        var tentativi = 0
        while !fatture.isHittable && tentativi < 4 { app.swipeUp(); tentativi += 1 }
        fatture.tap()
        scatta("04-paziente-fatture")
        let riga = app.buttons["fattura.riga"].firstMatch
        XCTAssertTrue(riga.waitForExistence(timeout: 8))
        riga.tap()
        sleep(2)
        scatta("05-paziente-pdf")
        app.swipeDown(velocity: .fast)
        if app.buttons["Fine"].exists { app.buttons["Fine"].tap() }
        if app.buttons["Done"].exists { app.buttons["Done"].tap() }

        scheda("Corsi")
        scatta("06-paziente-corsi")
        if app.buttons["corso.scheda"].firstMatch.waitForExistence(timeout: 3) {
            app.buttons["corso.scheda"].firstMatch.tap()
            scatta("07-paziente-corso")
            indietro()
        }

        scheda("Chat")
        XCTAssertTrue(app.textFields["chat.testo"].waitForExistence(timeout: 8))
        scatta("08-paziente-chat")

        scheda("Oggi")
        app.buttons["oggi.profilo"].tap()
        let esci = app.buttons["profilo.esci"]
        XCTAssertTrue(esci.waitForExistence(timeout: 5))
        scatta("08b-paziente-profilo")
        esci.tap()
        let conferma = app.buttons.matching(NSPredicate(format: "label == 'Esci' AND identifier != 'profilo.esci'")).firstMatch
        XCTAssertTrue(conferma.waitForExistence(timeout: 5))
        conferma.tap()
        XCTAssertTrue(app.textFields["accesso.email"].waitForExistence(timeout: 8), "Il logout torna all'accesso")
    }

    /// Riaprendo l'app la sessione riparte dal refresh token nel Keychain, con la rotazione vera del backend.
    func testSessioneRipristinata() {
        accedi("edoardo@edodoc.it", "0000")
        XCTAssertTrue(app.staticTexts["cruscotto.numero"].waitForExistence(timeout: 15))
        app.terminate()
        app.launchArguments = ["-edodoc.senzaPush"]
        app.launch()
        XCTAssertTrue(app.staticTexts["cruscotto.numero"].waitForExistence(timeout: 15))
    }

    /// Primo accesso con il link incollato oppure password già impostata, poi il check intero.
    func testCheckCompleto() {
        let ambiente = ProcessInfo.processInfo.environment
        let email = ambiente["EDODOC_EMAIL"] ?? "prova.collaudo@esempio.it"
        let password = "collaudo-ios-2026"
        if let invito = ambiente["EDODOC_INVITO"], !invito.isEmpty {
            XCTAssertTrue(app.buttons["accesso.primo"].waitForExistence(timeout: 10))
            app.buttons["accesso.primo"].tap()
            let link = app.textFields["primo.link"]
            XCTAssertTrue(link.waitForExistence(timeout: 5))
            link.tap()
            link.typeText(invito)
            let nuova = app.secureTextFields["primo.password"]
            nuova.tap()
            nuova.typeText(password)
            let conferma = app.secureTextFields["primo.conferma"]
            conferma.tap()
            conferma.typeText(password)
            scatta("30-primo-accesso")
            app.buttons["Entra nell'app"].tap()
        } else {
            accedi(email, password)
        }
        XCTAssertTrue(app.staticTexts["oggi.frase"].waitForExistence(timeout: 15))
        let inizia = app.buttons["oggi.azione"]
        if inizia.waitForExistence(timeout: 2), inizia.label == "Inizia il check" {
            scatta("31-check-da-fare")
            inizia.tap()
        } else {
            let modifica = app.buttons["oggi.modifica"]
            XCTAssertTrue(modifica.waitForExistence(timeout: 5))
            modifica.tap()
        }
        let avanti = app.buttons["check.avanti"]
        XCTAssertTrue(avanti.waitForExistence(timeout: 5))
        let quadrante = app.otherElements["check.quadrante"]
        if quadrante.waitForExistence(timeout: 2) {
            quadrante.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.7)).tap()
        }
        scatta("32-check-pasti")
        avanti.tap()
        scatta("33-check-liberi")
        avanti.tap()
        sleep(1)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.62, dy: 0.42)).tap()
        scatta("34-check-energia")
        avanti.tap()
        avanti.tap()
        sleep(1)
        scatta("35-check-peso")
        avanti.tap()
        sleep(1)
        app.buttons["Sì"].tap()
        scatta("36-check-percezione")
        avanti.tap()
        sleep(1)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.72, dy: 0.42)).tap()
        scatta("37-check-impegno")
        avanti.tap()
        XCTAssertTrue(app.staticTexts["check.grazie"].waitForExistence(timeout: 8))
        scatta("38-check-inviato")
        sleep(4)
        XCTAssertTrue(app.staticTexts["oggi.frase"].waitForExistence(timeout: 8))
        scatta("39-oggi-dopo-check")
        XCTAssertFalse(app.buttons["Inizia il check"].exists, "Dopo l'invio Oggi deve aggiornarsi")

        scheda("Chat")
        let testo = app.textFields["chat.testo"]
        XCTAssertTrue(testo.waitForExistence(timeout: 8))
        testo.tap()
        let messaggio = "Collaudo iOS \(tema)"
        testo.typeText(messaggio)
        app.buttons["chat.invia"].tap()
        XCTAssertTrue(app.staticTexts[messaggio].firstMatch.waitForExistence(timeout: 8))
        scatta("40-chat-inviata")
    }

    /// Il dottore registra un vocale vero: serve il microfono concesso con simctl privacy.
    func testVocaleDelDottore() {
        accedi("edoardo@edodoc.it", "0000")
        XCTAssertTrue(app.staticTexts["cruscotto.numero"].waitForExistence(timeout: 15))
        scheda("Check")
        let collaudo = app.staticTexts["Prova Collaudo"]
        XCTAssertTrue(collaudo.waitForExistence(timeout: 8))
        collaudo.tap()
        let rispondi = app.buttons["check.rispondi"]
        XCTAssertTrue(rispondi.waitForExistence(timeout: 8))
        rispondi.tap()
        let registra = app.buttons["feedback.registra"]
        XCTAssertTrue(registra.waitForExistence(timeout: 5))
        registra.tap()
        sleep(3)
        scatta("41-dottore-registra")
        registra.tap()
        sleep(1)
        scatta("42-dottore-riascolto")
        app.buttons["feedback.invia"].tap()
        XCTAssertTrue(app.buttons["check.rispondi"].waitForExistence(timeout: 10))
        sleep(1)
        scatta("43-dottore-feedback-inviato")
    }

    func testPazienteAscoltaIlVocale() {
        accedi("prova.collaudo@esempio.it", "collaudo-ios-2026")
        let azione = app.buttons["oggi.azione"]
        XCTAssertTrue(azione.waitForExistence(timeout: 15))
        XCTAssertEqual(azione.label, "Ascolta")
        scatta("44-paziente-vocale-ricevuto")
        azione.tap()
        XCTAssertTrue(app.buttons["vocale.play"].waitForExistence(timeout: 10))
        app.buttons["vocale.play"].tap()
        sleep(1)
        scatta("45-paziente-vocale-ascolto")
    }

    func testLezioneDiTesto() {
        accedi("luca.bruno@esempio.it", "demo-paziente-2026")
        XCTAssertTrue(app.staticTexts["oggi.frase"].waitForExistence(timeout: 15))
        scheda("Corsi")
        let corso = app.buttons["corso.scheda"].firstMatch
        XCTAssertTrue(corso.waitForExistence(timeout: 8))
        scatta("06-paziente-corsi")
        corso.tap()
        scatta("07-paziente-corso")
        let lezione = app.buttons["Come leggere il piano"]
        XCTAssertTrue(lezione.waitForExistence(timeout: 8))
        lezione.tap()
        sleep(1)
        scatta("09-paziente-lezione")
    }

    func testDeepLinkInvito() {
        XCTAssertTrue(app.textFields["accesso.email"].waitForExistence(timeout: 10))
        XCUIDevice.shared.system.open(URL(string: "edodoc://imposta-password?token=prova")!)
        let apri = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Apri"]
        if apri.waitForExistence(timeout: 3) { apri.tap() }
        XCTAssertTrue(app.staticTexts["Crea la tua password."].waitForExistence(timeout: 8))
        XCTAssertFalse(app.textFields["primo.link"].exists, "Con il link il token è già noto")
        scatta("46-deep-link")
    }

    func testOffline() {
        app.terminate()
        app.launchArguments += ["-edodoc.api", "http://localhost:9/api"]
        app.launch()
        accedi("edoardo@edodoc.it", "0000")
        let errore = app.staticTexts["accesso.errore"]
        XCTAssertTrue(errore.waitForExistence(timeout: 10))
        XCTAssertTrue(errore.label.contains("offline"), errore.label)
        scatta("47-offline")
    }

    private func altro(_ voce: String, _ nome: String) {
        scheda("Altro")
        let riga = app.buttons["altro.\(voce)"]
        XCTAssertTrue(riga.waitForExistence(timeout: 6), voce)
        riga.tap()
        scatta(nome)
    }

    private func tornaAdAltro() {
        indietro()
    }

    func testDottore() {
        accedi("edoardo@edodoc.it", "0000")
        XCTAssertTrue(app.staticTexts["cruscotto.numero"].waitForExistence(timeout: 15))
        scatta("10-dottore-oggi")

        scheda("Pazienti")
        scatta("11-dottore-pazienti")
        let luca = app.staticTexts["Luca Bruno"]
        var giri = 0
        while !(luca.exists && luca.isHittable) && giri < 8 { app.swipeUp(); giri += 1 }
        luca.tap()
        scatta("12-dottore-scheda")
        for sezione in ["Visite", "Check", "Pagamenti", "Fatture", "Task"] {
            let chip = app.buttons["sezione.\(sezione)"]
            if chip.waitForExistence(timeout: 3) {
                if sezione == "Fatture" { app.buttons["sezione.Check"].swipeLeft(); sleep(1) }
                chip.tap()
            }
            scatta("13-dottore-scheda-\(sezione.lowercased())")
        }
        indietro()

        scheda("Check")
        scatta("14-dottore-check")
        app.buttons["Segnali"].tap()
        let riga = app.buttons["check.riga"].firstMatch
        XCTAssertTrue(riga.waitForExistence(timeout: 8))
        riga.tap()
        scatta("15-dottore-check-dettaglio")
        let rispondi = app.buttons["check.rispondi"]
        if rispondi.waitForExistence(timeout: 3) {
            rispondi.tap()
            scatta("16-dottore-feedback")
            app.buttons["Annulla"].tap()
        }
        indietro()

        scheda("Chat")
        scatta("17-dottore-chat")

        altro("Agenda", "18-dottore-agenda"); tornaAdAltro()
        altro("Acquisti", "19-dottore-acquisti"); tornaAdAltro()
        altro("Pagamenti", "20-dottore-pagamenti"); tornaAdAltro()
        altro("Fatture", "21-dottore-fatture"); tornaAdAltro()
        altro("Corsi", "22-dottore-corsi"); tornaAdAltro()
        altro("Finance", "23-dottore-finance"); tornaAdAltro()
        altro("Task", "24-dottore-task"); tornaAdAltro()
        altro("Impostazioni", "25-dottore-impostazioni")
    }
}
