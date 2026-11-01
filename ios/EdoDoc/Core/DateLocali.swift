import Foundation

/// Il backend parla in ora di Roma senza fuso: LocalDate e LocalDateTime di Java.
/// Restano valori di calendario, così un paziente all'estero non vede cambiare il giorno di una fattura.
enum Roma {
    static let fuso = TimeZone(identifier: "Europe/Rome")!
    static let locale = Locale(identifier: "it_IT")
    static let calendario: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = fuso
        c.locale = locale
        c.firstWeekday = 2
        return c
    }()

    static func formato(_ schema: String) -> DateFormatter {
        let f = DateFormatter()
        f.calendar = calendario
        f.timeZone = fuso
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate(schema)
        return f
    }
}

/// `2026-11-02`
struct Giorno: Codable, Hashable, Comparable, Sendable {
    let anno: Int
    let mese: Int
    let giorno: Int

    init(anno: Int, mese: Int, giorno: Int) {
        self.anno = anno
        self.mese = mese
        self.giorno = giorno
    }

    init(_ data: Date) {
        let c = Roma.calendario.dateComponents([.year, .month, .day], from: data)
        self.init(anno: c.year!, mese: c.month!, giorno: c.day!)
    }

    init?(testo: String) {
        let parti = testo.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard parti.count == 3 else { return nil }
        self.init(anno: parti[0], mese: parti[1], giorno: parti[2])
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        let testo = try c.decode(String.self)
        guard let valore = Giorno(testo: testo) else {
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "Giorno non valido: \(testo)")
        }
        self = valore
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(testo)
    }

    static var oggi: Giorno { Giorno(Date()) }
    var testo: String { String(format: "%04d-%02d-%02d", anno, mese, giorno) }
    var data: Date { Roma.calendario.date(from: DateComponents(year: anno, month: mese, day: giorno, hour: 12))! }

    func aggiungendo(giorni: Int) -> Giorno { Giorno(Roma.calendario.date(byAdding: .day, value: giorni, to: data)!) }
    func aggiungendo(mesi: Int) -> Giorno { Giorno(Roma.calendario.date(byAdding: .month, value: mesi, to: data)!) }

    /// Il lunedì della settimana che contiene il giorno.
    var lunedi: Giorno {
        let giornoSettimana = Roma.calendario.component(.weekday, from: data)
        return aggiungendo(giorni: -((giornoSettimana + 5) % 7))
    }

    var primoDelMese: Giorno { Giorno(anno: anno, mese: mese, giorno: 1) }
    var ultimoDelMese: Giorno { primoDelMese.aggiungendo(mesi: 1).aggiungendo(giorni: -1) }

    static func < (a: Giorno, b: Giorno) -> Bool { (a.anno, a.mese, a.giorno) < (b.anno, b.mese, b.giorno) }

    var breve: String { Formati.giornoBreve.string(from: data) }
    var esteso: String { Formati.giornoEsteso.string(from: data) }
    var meseAnno: String { Formati.meseAnno.string(from: data) }
}

/// `2026-11-01T15:00:00`, con o senza frazioni di secondo.
struct OraLocale: Codable, Hashable, Comparable, Sendable {
    let data: Date

    init(_ data: Date) {
        self.data = data
    }

    init?(testo: String) {
        let pulito = testo.split(separator: ".").first.map(String.init) ?? testo
        guard let data = Formati.iso.date(from: pulito) else { return nil }
        self.data = data
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        let testo = try c.decode(String.self)
        guard let valore = OraLocale(testo: testo) else {
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "Data non valida: \(testo)")
        }
        self = valore
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(testo)
    }

    var testo: String { Formati.iso.string(from: data) }
    var giorno: Giorno { Giorno(data) }
    var ora: String { Formati.ora.string(from: data) }
    var giornoOra: String { Formati.giornoOra.string(from: data) }

    static func < (a: OraLocale, b: OraLocale) -> Bool { a.data < b.data }
}

enum Formati {
    static let iso: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = Roma.fuso
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return f
    }()
    static let giornoBreve = Roma.formato("dMMM")
    static let giornoEsteso = Roma.formato("EEEEdMMMM")
    static let meseAnno = Roma.formato("MMMMyyyy")
    static let ora = Roma.formato("HHmm")
    static let giornoOra = Roma.formato("EEEdMMMHHmm")

    static func euro(_ valore: Decimal?) -> String {
        (valore ?? 0).formatted(.currency(code: "EUR").locale(Roma.locale))
    }

    static func euroIntero(_ valore: Decimal?) -> String {
        (valore ?? 0).formatted(.currency(code: "EUR").locale(Roma.locale).precision(.fractionLength(0)))
    }

    static func peso(_ valore: Decimal?) -> String {
        (valore ?? 0).formatted(.number.locale(Roma.locale).precision(.fractionLength(0...1))) + " kg"
    }

    static func durata(_ secondi: Int) -> String {
        if secondi >= 3600 {
            return "\(secondi / 3600) h \((secondi % 3600) / 60) min"
        }
        return "\(max(1, secondi / 60)) min"
    }

    static func cronometro(_ secondi: Double) -> String {
        let s = Int(secondi.rounded(.down))
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}
