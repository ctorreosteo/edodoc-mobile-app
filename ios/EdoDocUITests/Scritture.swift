import XCTest

/// Giro di scrittura del dottore contro il backend locale, su dati creati qui e cancellati alla fine.
/// La cartella condivisa con lo script esterno arriva da TEST_RUNNER_EDODOC_GIRO.
/// I passi vanno lanciati uno alla volta e in ordine: testE richiede che il primo accesso sia già fatto,
/// testF i 60 messaggi "Seme NNN" seminati da fuori, testG una risposta al check data da fuori.
final class ScrittureTests: XCTestCase {
    private var app: XCUIApplication!
    private var cartella: URL!
    private let nome = "Collaudo iOS"
    private let cognome = "Giro"
    private let password = "collaudo-ios-giro"
    private var completo: String { "\(nome) \(cognome) Due" }

    override func setUpWithError() throws {
        continueAfterFailure = false
        // Emette una fattura che consuma un numero: gira solo se chiesto esplicitamente
        let giro = ProcessInfo.processInfo.environment["EDODOC_GIRO"]
        try XCTSkipIf(giro == nil, "Giro di scrittura non richiesto")
        cartella = URL(fileURLWithPath: giro ?? NSTemporaryDirectory())
        try? FileManager.default.createDirectory(at: cartella, withIntermediateDirectories: true)
        app = XCUIApplication()
        app.launchArguments = ["-edodoc.azzera", "-edodoc.senzaPush"]
        app.launch()
    }

    // MARK: Aiuti

    private func accedi(_ email: String, _ segreto: String) {
        let campo = app.textFields["accesso.email"]
        XCTAssertTrue(campo.waitForExistence(timeout: 10))
        campo.tap()
        campo.typeText(email)
        let chiave = app.secureTextFields["accesso.password"]
        sleep(1)
        chiave.tap()
        chiave.typeText(segreto)
        app.buttons["accesso.invia"].tap()
    }

    private func dottore() {
        accedi("edoardo@edodoc.it", "0000")
        XCTAssertTrue(app.staticTexts["cruscotto.numero"].waitForExistence(timeout: 15))
    }

    private func paziente() {
        accedi("collaudo.ios.giro@esempio.it", password)
        XCTAssertTrue(app.staticTexts["oggi.frase"].waitForExistence(timeout: 15))
    }

    private func foto(_ nome: String) {
        try? XCUIScreen.main.screenshot().pngRepresentation.write(to: cartella.appendingPathComponent("\(nome).png"))
    }

    private func scheda(_ nome: String) {
        let bottone = app.tabBars.buttons[nome]
        XCTAssertTrue(bottone.waitForExistence(timeout: 8), nome)
        bottone.tap()
    }

    private func altro(_ voce: String) {
        scheda("Altro")
        let riga = app.buttons["altro.\(voce)"]
        if !riga.waitForExistence(timeout: 2) {
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }
        XCTAssertTrue(riga.waitForExistence(timeout: 6), voce)
        riga.tap()
    }

    private func campo(_ etichetta: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(
            format: "(elementType == %d OR elementType == %d) AND (label == %@ OR placeholderValue == %@)",
            XCUIElement.ElementType.textField.rawValue, XCUIElement.ElementType.textView.rawValue, etichetta, etichetta)).firstMatch
    }

    private func scrivi(_ etichetta: String, _ testo: String) {
        let elemento = campo(etichetta)
        XCTAssertTrue(elemento.waitForExistence(timeout: 5), etichetta)
        elemento.tap()
        elemento.typeText(testo)
    }

    private func tocca(_ etichetta: String, timeout: TimeInterval = 8, _ riga: UInt = #line) {
        let bottone = app.buttons[etichetta].firstMatch
        XCTAssertTrue(bottone.waitForExistence(timeout: timeout), "Manca \(etichetta)", line: riga)
        bottone.tap()
    }

    private func scegli(_ etichetta: String, _ voce: String, _ linea: UInt = #line) {
        let picker = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", etichetta)).firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 6), "Manca \(etichetta)", line: linea)
        picker.tap()
        tocca(voce, linea)
    }

    /// Salva il foglio e verifica l'avviso del server.
    private func salva(_ etichetta: String = "Salva", avviso: String, _ riga: UInt = #line) {
        tocca(etichetta, riga)
        XCTAssertTrue(app.staticTexts[avviso].waitForExistence(timeout: 8), "Manca l'avviso \(avviso): \(erroreVisibile())", line: riga)
    }

    private func erroreVisibile() -> String {
        foto("errore")
        return app.staticTexts.allElementsBoundByIndex.prefix(6).map(\.label).joined(separator: " | ")
    }

    /// Le righe sono Menu o Button etichettati con tutto il testo della riga.
    private func riga(_ testo: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", testo)).firstMatch
    }

    private func trova(_ elemento: XCUIElement, _ testo: String = "", _ linea: UInt = #line) {
        var giri = 0
        while !(elemento.exists && elemento.isHittable) && giri < 14 {
            app.swipeUp(velocity: .slow)
            giri += 1
        }
        XCTAssertTrue(elemento.exists, "Non trovato \(testo)", line: linea)
    }

    private func conferma(_ etichetta: String, _ linea: UInt = #line) {
        let elenco = app.buttons.matching(NSPredicate(format: "label == %@", etichetta))
        let esiste = NSPredicate { _, _ in elenco.count > 0 }
        _ = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: esiste, object: nil)], timeout: 5)
        // Il pulsante del dialogo è l'unico raggiungibile: quello omonimo della schermata sta sotto
        for indice in 0..<elenco.count where elenco.element(boundBy: indice).isHittable {
            elenco.element(boundBy: indice).tap()
            return
        }
        XCTFail("Nessun \(etichetta) raggiungibile", line: linea)
    }

    private func azioni(_ voce: String) {
        tocca("Azioni")
        tocca(voce)
    }

    private func apriScheda() {
        scheda("Pazienti")
        let cerca = app.searchFields.firstMatch
        XCTAssertTrue(cerca.waitForExistence(timeout: 8))
        cerca.tap()
        cerca.typeText("Collaudo iOS")
        let voce = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Collaudo iOS Giro")).firstMatch
        XCTAssertTrue(voce.waitForExistence(timeout: 8))
        voce.tap()
    }

    private func sezione(_ nome: String) {
        if nome == "Fatture" || nome == "Task" { app.buttons["sezione.Check"].swipeLeft(); sleep(1) }
        app.buttons["sezione.\(nome)"].tap()
    }

    private func salvaFile(_ nome: String, _ testo: String) {
        try? testo.write(to: cartella.appendingPathComponent(nome), atomically: true, encoding: .utf8)
    }

    private func leggiFile(_ nome: String) -> String? {
        try? String(contentsOf: cartella.appendingPathComponent(nome), encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Paziente, invito, percorso, visita

    func testA_Paziente() {
        dottore()
        scheda("Pazienti")
        tocca("Nuovo paziente")
        scrivi("Nome", nome)
        scrivi("Cognome", cognome)
        scrivi("Email", "collaudo.ios.giro@esempio.it")
        scegli("Stato", "Attivo")
        scrivi("Codice fiscale", "CLLGRI80A01F205X")
        scrivi("Indirizzo", "Via Collaudo 1")
        scrivi("CAP", "20100")
        scrivi("Città", "Milano")
        scrivi("Provincia", "MI")
        salva(avviso: "Paziente salvato")

        apriScheda()
        azioni("Modifica")
        let elemento = campo("Cognome")
        XCTAssertTrue(elemento.waitForExistence(timeout: 5))
        elemento.tap()
        elemento.typeText(" Due")
        salva(avviso: "Paziente salvato")
        XCTAssertTrue(app.staticTexts[completo].waitForExistence(timeout: 8), "La scheda non si è aggiornata")

        azioni("Invia l'accesso all'app")
        let link = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "edodoc://")).firstMatch
        XCTAssertTrue(link.waitForExistence(timeout: 8), erroreVisibile())
        salvaFile("invito.txt", link.label)
        tocca("Chiudi")

        azioni("Nuovo percorso")
        sleep(1)
        salva(avviso: "Percorso creato")

        azioni("Nuova visita")
        sleep(1)
        salva(avviso: "Visita salvata")
        sezione("Visite")
        let visita = riga("Controllo").exists ? riga("Controllo") : app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Online")).firstMatch
        XCTAssertTrue(visita.waitForExistence(timeout: 8), erroreVisibile())
        visita.tap()
        tocca("Segna come effettuata")
        XCTAssertTrue(app.staticTexts["Visita aggiornata"].waitForExistence(timeout: 8), erroreVisibile())
        XCTAssertTrue(app.staticTexts["EFFETTUATA"].waitForExistence(timeout: 5) || app.staticTexts["Effettuata"].exists)
        foto("A-visita")
    }

    // MARK: Pagamenti e fattura

    private func nuovoPagamento(_ descrizione: String, _ importo: String) {
        azioni("Nuovo pagamento")
        sleep(1)
        scrivi("Descrizione", descrizione)
        scrivi("Importo (€)", importo)
        salva(avviso: "Pagamento salvato")
    }

    func testB_PagamentiEFattura() {
        dottore()
        apriScheda()
        nuovoPagamento("Collaudo pagamento A", "120")
        nuovoPagamento("Collaudo pagamento B", "80")
        sezione("Pagamenti")

        let a = riga("Collaudo pagamento A")
        trova(a, "pagamento A")
        a.tap()
        tocca("Registra pagamento")
        salva("Registra", avviso: "Pagamento registrato")

        trova(a, "pagamento A")
        a.tap()
        tocca("Annulla registrazione")
        XCTAssertTrue(app.staticTexts["Registrazione annullata"].waitForExistence(timeout: 8), erroreVisibile())

        sleep(1)
        trova(a, "pagamento A")
        a.tap()
        tocca("Registra pagamento")
        salva("Registra", avviso: "Pagamento registrato")

        let b = riga("Collaudo pagamento B")
        trova(b, "pagamento B")
        b.tap()
        tocca("Annulla il pagamento")
        sleep(1)
        conferma("Annulla il pagamento")
        XCTAssertTrue(app.staticTexts["Pagamento annullato"].waitForExistence(timeout: 8), erroreVisibile())

        sleep(1)
        trova(a, "pagamento A")
        a.tap()
        tocca("Emetti fattura")
        tocca("Calcola anteprima")
        XCTAssertTrue(app.staticTexts["Totale"].waitForExistence(timeout: 8), erroreVisibile())
        foto("B-anteprima")
        salva("Emetti", avviso: "Fattura emessa")

        sezione("Fatture")
        let fattura = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "N. ")).firstMatch
        XCTAssertTrue(fattura.waitForExistence(timeout: 8), erroreVisibile())
        let numero = fattura.label.dropFirst(3).prefix { $0.isNumber }
        salvaFile("fattura.txt", String(numero))
    }

    func testB2_TesseraEAnnullamento() {
        guard let numero = leggiFile("fattura.txt") else { return XCTFail("Manca la fattura") }
        dottore()
        apriScheda()
        sezione("Fatture")
        if leggiFile("tessera.txt") == nil {
        altro("Fatture")
        tocca("Tessera Sanitaria")
        let voce = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "N. \(numero) ")).firstMatch
        sleep(2)
        trova(voce, "tessera")
        voce.swipeLeft()
        tocca("Inserita")
        XCTAssertTrue(app.staticTexts["Segnata come inserita"].waitForExistence(timeout: 8), erroreVisibile())
        sleep(1)
        XCTAssertFalse(voce.exists, "Dopo inserita esce dall'elenco")
        scheda("Pazienti")
        }

        let riga = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "N. \(numero) ")).firstMatch
        XCTAssertTrue(riga.waitForExistence(timeout: 8), erroreVisibile())
        riga.tap()
        let annulla = app.buttons["Annulla la fattura"]
        trova(annulla, "annulla")
        annulla.tap()
        scrivi("Motivo", "Collaudo iOS")
        sleep(1)
        app.navigationBars.buttons["Annulla la fattura"].tap()
        XCTAssertTrue(app.staticTexts["Fattura annullata"].waitForExistence(timeout: 8), erroreVisibile())
        foto("B-annullata")
    }

    // MARK: Finance, task, impostazioni

    func testC_FinanceTaskImpostazioni() {
        dottore()
        altro("Finance")
        sleep(1)
        if leggiFile("movimento.txt") == nil {
            tocca("Aggiungi")
            tocca("Nuovo movimento")
            scrivi("Descrizione", "Collaudo iOS movimento")
            scrivi("Importo (€)", "10")
            salva(avviso: "Movimento salvato")
            tocca("Movimenti")
            let movimento = riga("Collaudo iOS movimento")
            trova(movimento, "movimento")
            movimento.tap()
            scrivi("Note", "modificato")
            salva(avviso: "Movimento salvato")
            trova(movimento, "movimento")
            movimento.swipeLeft()
            tocca("Elimina")
            XCTAssertTrue(app.staticTexts["Movimento eliminato"].waitForExistence(timeout: 8), erroreVisibile())
            app.swipeDown(velocity: .fast)
            app.swipeDown(velocity: .fast)
        }
        if leggiFile("scadenza.txt") == nil {
            tocca("Aggiungi")
            tocca("Nuova scadenza")
            scrivi("Titolo", "Collaudo iOS scadenza")
            scrivi("Importo stimato (€)", "20")
            salva(avviso: "Scadenza salvata")
        }
        if leggiFile("scadenza-pagata.txt") == nil {
            tocca("Scadenze")
            let scadenza = riga("Collaudo iOS scadenza")
            trova(scadenza, "scadenza")
            scadenza.swipeLeft()
            tocca("Paga")
            sleep(1)
            app.navigationBars.buttons["Paga"].tap()
            XCTAssertTrue(app.staticTexts["Scadenza pagata"].waitForExistence(timeout: 8), erroreVisibile())
            app.swipeDown(velocity: .fast)
            app.swipeDown(velocity: .fast)
        }
        tocca("Aggiungi")
        tocca("Nuova scadenza")
        scrivi("Titolo", "Collaudo iOS da eliminare")
        salva(avviso: "Scadenza salvata")
        tocca("Scadenze")
        let daEliminare = riga("Collaudo iOS da eliminare")
        trova(daEliminare, "scadenza da eliminare")
        daEliminare.swipeLeft()
        tocca("Elimina")
        XCTAssertTrue(app.staticTexts["Scadenza eliminata"].waitForExistence(timeout: 8), erroreVisibile())
        sleep(1)
        let pagata = riga("Collaudo iOS scadenza")
        trova(pagata, "scadenza pagata")
        pagata.swipeLeft()
        sleep(1)
        XCTAssertFalse(app.buttons["Elimina"].exists, "Una scadenza pagata non offre Elimina")
        pagata.swipeRight()
        app.swipeDown(velocity: .fast)
        app.swipeDown(velocity: .fast)

        // Il pagamento della scadenza ha creato questo movimento
        tocca("Movimenti")
        let movimentoScadenza = riga("Collaudo iOS scadenza")
        trova(movimentoScadenza, "movimento della scadenza")
        movimentoScadenza.swipeLeft()
        tocca("Elimina")
        sleep(2)
        foto("C-movimento-scadenza")

        altro("Task")
        tocca("Nuovo task")
        sleep(1)
        let tipo = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Tipo")).firstMatch
        XCTAssertTrue(tipo.waitForExistence(timeout: 5))
        tipo.tap()
        tocca("Invio piano")
        scrivi("Titolo (facoltativo)", "Collaudo iOS task")
        salva(avviso: "Task creato")
        let stato = app.buttons["task.stato.Collaudo iOS task"]
        trova(stato, "task")
        stato.tap()
        sleep(2)
        XCTAssertFalse(stato.exists, "Il task fatto esce dall'elenco")
    }

    func testC2_TaskCompletatoEImpostazione() {
        dottore()
        altro("Task")
        let stato = app.buttons["task.stato.Collaudo iOS task"]
        let completati = app.switches.firstMatch
        XCTAssertTrue(completati.waitForExistence(timeout: 8), erroreVisibile())
        completati.switches.firstMatch.tap()
        sleep(2)
        trova(stato, "task completato")
        app.staticTexts["Collaudo iOS task"].firstMatch.swipeLeft()
        tocca("Elimina")
        XCTAssertTrue(app.staticTexts["Task eliminato"].waitForExistence(timeout: 8), erroreVisibile())

        altro("Impostazioni")
        let voce = riga("Energia uguale o inferiore")
        trova(voce, "impostazione")
        voce.tap()
        sleep(1)
        salva(avviso: "Impostazione salvata")
    }

    // MARK: Corsi

    func testD_Corsi() {
        dottore()
        altro("Corsi")
        tocca("Nuovo corso")
        scrivi("Titolo", "Collaudo iOS corso")
        tocca("Salva")
        let corso = app.staticTexts["Collaudo iOS corso"].firstMatch
        XCTAssertTrue(corso.waitForExistence(timeout: 8), erroreVisibile())
        corso.tap()
        azioni("Modifica")
        let titolo = campo("Titolo")
        XCTAssertTrue(titolo.waitForExistence(timeout: 5))
        titolo.tap()
        titolo.typeText(" due")
        tocca("Salva")
        XCTAssertTrue(app.staticTexts["Collaudo iOS corso due"].waitForExistence(timeout: 8), erroreVisibile())

        tocca("Aggiungi un modulo")
        scrivi("Titolo", "Collaudo modulo")
        tocca("Salva")
        let aggiungi = app.buttons["Aggiungi una lezione"]
        XCTAssertTrue(aggiungi.waitForExistence(timeout: 8), erroreVisibile())
        aggiungi.tap()
        scrivi("Titolo", "Collaudo lezione")
        scrivi("Scrivi la lezione", "Testo di collaudo")
        tocca("Salva")
        let lezione = riga("Collaudo lezione")
        XCTAssertTrue(lezione.waitForExistence(timeout: 8), erroreVisibile())
        lezione.tap()
        let pubblicata = app.switches["Pubblicata"]
        XCTAssertTrue(pubblicata.waitForExistence(timeout: 8))
        pubblicata.switches.firstMatch.tap()
        sleep(1)
        foto("D-lezione")
        XCTAssertEqual(pubblicata.value as? String, "1", erroreVisibile())

        app.navigationBars.buttons.element(boundBy: 0).tap()
        let pubblicato = app.switches["Pubblicato"]
        XCTAssertTrue(pubblicato.waitForExistence(timeout: 8))
        pubblicato.switches.firstMatch.tap()
        sleep(2)
        XCTAssertEqual(pubblicato.value as? String, "1", erroreVisibile())
        pubblicato.switches.firstMatch.tap()
        sleep(2)
        XCTAssertEqual(pubblicato.value as? String, "0", erroreVisibile())

        lezione.tap()
        azioni("Elimina")
        sleep(1)
        conferma("Elimina")
        XCTAssertTrue(app.staticTexts["Lezione eliminata"].waitForExistence(timeout: 8), erroreVisibile())
        sleep(1)
        tocca("Azioni del modulo")
        tocca("Elimina")
        XCTAssertTrue(app.staticTexts["Modulo eliminato"].waitForExistence(timeout: 8), erroreVisibile())
        sleep(1)
        azioni("Elimina il corso")
        sleep(1)
        conferma("Elimina")
        XCTAssertTrue(app.staticTexts["Corso eliminato"].waitForExistence(timeout: 8), erroreVisibile())
    }

    // MARK: Check, chat e 409

    private func avanti() {
        app.buttons["check.avanti"].tap()
        sleep(1)
    }

    func testE_PazienteCompilaIlCheck() {
        // Il primo accesso con il link incollato è già fatto: l'invito vale una volta sola
        paziente()
        tocca("oggi.azione")
        compila()
        app.buttons["check.avanti"].tap()
        XCTAssertTrue(app.staticTexts["check.grazie"].waitForExistence(timeout: 8), erroreVisibile())
    }

    private func compila() {
        XCTAssertTrue(app.buttons["check.avanti"].waitForExistence(timeout: 5))
        avanti()
        avanti()
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.62, dy: 0.42)).tap()
        avanti()
        avanti()
        avanti()
        avanti()
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.72, dy: 0.42)).tap()
        sleep(1)
    }

    /// I 60 messaggi li semina lo script esterno: la prima pagina ne porta 50.
    func testF_ChatDelDottore() {
        dottore()
        apriScheda()
        tocca("Scrivi")
        let testo = app.textFields["chat.testo"]
        XCTAssertTrue(testo.waitForExistence(timeout: 8))
        sleep(2)
        if leggiFile("chat.txt") == nil {
            testo.tap()
            testo.typeText("Collaudo iOS dal dottore")
            app.buttons["chat.invia"].tap()
            XCTAssertTrue(messaggio("Collaudo iOS dal dottore").waitForExistence(timeout: 8), erroreVisibile())
            salvaFile("chat.txt", "1")
        }
        XCTAssertTrue(messaggio("Seme 060").waitForExistence(timeout: 5), "Manca l'ultimo messaggio seminato")
        sleep(1)
        var giri = 0
        while !app.buttons["Messaggi precedenti"].exists && giri < 16 { app.swipeDown(velocity: .fast); giri += 1 }
        XCTAssertFalse(messaggio("Seme 005").exists, "Il messaggio 5 non doveva essere nella prima pagina")
        foto("F-prima-pagina")
        tocca("Messaggi precedenti")
        sleep(2)
        giri = 0
        while !messaggio("Seme 001").exists && giri < 8 { app.swipeDown(velocity: .fast); giri += 1 }
        foto("F-pagina-precedente")
        XCTAssertTrue(messaggio("Seme 001").exists, erroreVisibile())
        XCTAssertFalse(app.buttons["Messaggi precedenti"].exists, "Non ci sono altre pagine")
    }

    private func messaggio(_ testo: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", testo)).firstMatch
    }

    /// Lo script risponde al check mentre il paziente lo sta modificando.
    func testG_ConflittoSulCheck() {
        paziente()
        let modifica = app.buttons["oggi.modifica"]
        XCTAssertTrue(modifica.waitForExistence(timeout: 8), erroreVisibile())
        modifica.tap()
        compila()
        salvaFile("pronto-409.txt", "1")
        let fatto = cartella.appendingPathComponent("risposto-409.txt")
        var attesa = 0
        while !FileManager.default.fileExists(atPath: fatto.path) && attesa < 30 { sleep(1); attesa += 1 }
        XCTAssertLessThan(attesa, 30, "Lo script non ha risposto")
        app.buttons["check.avanti"].tap()
        let avviso = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "ha già risposto")).firstMatch
        XCTAssertTrue(avviso.waitForExistence(timeout: 8), erroreVisibile())
        foto("G-409")
        XCTAssertTrue(app.staticTexts["oggi.frase"].waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["oggi.modifica"].exists, "Con il feedback il check non è più modificabile")
    }

    func testH_FeedbackDiTesto() {
        dottore()
        scheda("Check")
        let voce = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Collaudo iOS")).firstMatch
        trova(voce, "riga check")
        voce.tap()
        tocca("check.rispondi")
        tocca("Messaggio")
        scrivi("Scrivi il feedback", "Collaudo iOS feedback di testo")
        tocca("feedback.invia")
        XCTAssertTrue(app.staticTexts["Collaudo iOS feedback di testo"].firstMatch.waitForExistence(timeout: 8), erroreVisibile())
        foto("H-feedback")
    }

    // MARK: Pulizia

    func testZ_Pulizia() {
        dottore()
        altro("Acquisti")
        let acquisto = riga("Collaudo iOS Giro")
        trova(acquisto, "acquisto")
        acquisto.tap()
        let annulla = app.buttons["Annulla il percorso"]
        trova(annulla, "annulla percorso")
        annulla.tap()
        sleep(1)
        conferma("Annulla il percorso")
        XCTAssertTrue(app.staticTexts["Percorso annullato"].waitForExistence(timeout: 8), erroreVisibile())
        apriScheda()
        azioni("Archivia")
        sleep(1)
        conferma("Archivia")
        XCTAssertTrue(app.staticTexts["Paziente archiviato"].waitForExistence(timeout: 8), erroreVisibile())
    }
}
