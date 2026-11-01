import XCTest
@testable import EdoDoc

final class ModelliTests: XCTestCase {
    private let decoder = JSONDecoder()

    func testGiornoNonDipendeDalFusoDelTelefono() throws {
        let giorno = try decoder.decode(Giorno.self, from: Data(#""2026-11-02""#.utf8))
        XCTAssertEqual(giorno.testo, "2026-11-02")
        XCTAssertEqual(Giorno(giorno.data), giorno)
        let codificato = String(data: try JSONEncoder().encode(giorno), encoding: .utf8)
        XCTAssertEqual(codificato, #""2026-11-02""#)
    }

    func testOraLocaleAccettaLeFrazioniDiSecondo() throws {
        let ora = try decoder.decode(OraLocale.self, from: Data(#""2026-11-01T17:27:25.694561""#.utf8))
        XCTAssertEqual(ora.testo, "2026-11-01T17:27:25")
        XCTAssertEqual(ora.ora, "17:27")
        XCTAssertEqual(ora.giorno.testo, "2026-11-01")
    }

    func testLunediDellaSettimana() {
        XCTAssertEqual(Giorno(testo: "2026-11-01")!.lunedi.testo, "2026-10-26")
        XCTAssertEqual(Giorno(testo: "2026-11-02")!.lunedi.testo, "2026-11-02")
        XCTAssertEqual(Giorno(testo: "2026-11-08")!.lunedi.testo, "2026-11-02")
    }

    func testValoreNuovoDelServerNonRompeLaDecodifica() throws {
        let stato = try decoder.decode(StatoVisita.self, from: Data(#""RIMANDATA""#.utf8))
        XCTAssertEqual(stato, .sconosciuto)
        XCTAssertFalse(StatoVisita.scelte.contains(.sconosciuto))
    }

    func testCheckConVocale() throws {
        let json = #"""
        {"id":69,"pazienteId":16,"pazienteNome":"Luca Bruno","settimana":"2026-11-02","compilatoIl":"2026-11-01T18:00:00.123",
         "fonte":"APP","pastiOttimali":18,"pastiLiberi":null,"energia":4,"allenamento":null,"pesoKg":80.2,"andamentoPeso":null,
         "percezione":null,"percezioneMigliorata":true,"impegno":4,"pesoFermo":false,"percezioneNegativa":false,"energiaBassa":false,
         "lettoIl":null,"feedback":"","feedbackIl":"2026-11-01T18:42:00","feedbackLettoIl":null,
         "feedbackAudioId":"7f3b6a52-3a7e-4a0a-9a51-111111111111","feedbackAudioSecondi":2,"feedbackEmailIl":null}
        """#
        let check = try decoder.decode(CheckDettaglio.self, from: Data(json.utf8))
        XCTAssertTrue(check.haFeedback)
        XCTAssertTrue(check.feedbackDaLeggere)
        XCTAssertEqual(check.pesoKg, Decimal(string: "80.2"))
        XCTAssertEqual(Formati.peso(check.pesoKg), "80,2 kg")
    }

    func testPercorsoNullo() throws {
        let vuoto = try decoder.decode(PercorsoPaziente.self, from: Data(#"{"percorso":null,"visite":[]}"#.utf8))
        XCTAssertNil(vuoto.percorso)
    }

    func testDecimaleItaliano() {
        XCTAssertEqual(Decimal(italiano: "1500,50"), Decimal(string: "1500.50"))
        XCTAssertEqual(Decimal(italiano: "80.2"), Decimal(string: "80.2"))
        XCTAssertNil(Decimal(italiano: ""))
        XCTAssertNil(Decimal(italiano: "abc"))
    }

    func testTokenDalLinkDiInvito() {
        XCTAssertEqual(InvitoLink.token(da: "  edodoc://imposta-password?token=abc-123\n"), "abc-123")
        XCTAssertEqual(InvitoLink.token(da: "abc-123"), "abc-123")
        XCTAssertNil(InvitoLink.token(da: "edodoc://imposta-password"))
        XCTAssertNil(InvitoLink.token(da: "   "))
    }

    func testDestinazioneDalPayloadAPNs() {
        XCTAssertEqual(Destinazione(userInfo: ["data": ["tipo": "FEEDBACK", "checkId": 69]]), .feedback(checkId: 69))
        XCTAssertEqual(Destinazione(userInfo: ["data": ["tipo": "MESSAGGIO", "pazienteId": 16]]), .chat(pazienteId: 16))
        XCTAssertEqual(Destinazione(userInfo: ["data": ["tipo": "MESSAGGIO"]]), .chat(pazienteId: nil))
        XCTAssertNil(Destinazione(userInfo: ["aps": [:]]))
    }
}
