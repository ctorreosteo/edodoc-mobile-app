import Foundation

/// Un valore nuovo aggiunto dal backend non deve far fallire la decodifica di una schermata intera.
protocol EnumServer: RawRepresentable, Codable, Hashable, CaseIterable, Sendable where RawValue == String {
    static var sconosciuto: Self { get }
    var etichetta: String { get }
}

extension EnumServer {
    init(from decoder: Decoder) throws {
        let testo = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: testo) ?? Self.sconosciuto
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(rawValue)
    }

    static var scelte: [Self] { allCases.filter { $0 != Self.sconosciuto } }
}

// MARK: Accesso

struct Utente: Codable, Equatable, Sendable {
    let id: Int
    let username: String
    let nome: String?
    let cognome: String?
    let ruolo: String
    let pazienteId: Int?

    var eDottore: Bool { ruolo == "ADMIN" }
}

struct Sessione: Codable, Sendable {
    let token: String
    let refreshToken: String?
    let utente: Utente
}

// MARK: Check

struct Risposte: Codable, Sendable {
    var pastiOttimali: Int
    var pastiLiberi: String?
    var energia: Int
    var allenamento: String?
    var pesoKg: Decimal?
    var andamentoPeso: String?
    var percezione: String?
    var percezioneMigliorata: Bool?
    var impegno: Int
}

enum FonteCheck: String, EnumServer {
    case app = "APP", gestionale = "GESTIONALE", sconosciuto = ""
    var etichetta: String { self == .app ? "Dall'app" : "Dal gestionale" }
}

struct CheckDettaglio: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let pazienteId: Int
    let pazienteNome: String
    let settimana: Giorno
    let compilatoIl: OraLocale?
    let fonte: FonteCheck?
    let pastiOttimali: Int?
    let pastiLiberi: String?
    let energia: Int?
    let allenamento: String?
    let pesoKg: Decimal?
    let andamentoPeso: String?
    let percezione: String?
    let percezioneMigliorata: Bool?
    let impegno: Int?
    let pesoFermo: Bool
    let percezioneNegativa: Bool
    let energiaBassa: Bool
    let lettoIl: OraLocale?
    let feedback: String?
    let feedbackIl: OraLocale?
    let feedbackLettoIl: OraLocale?
    let feedbackAudioId: UUID?
    let feedbackAudioSecondi: Int?
    let feedbackEmailIl: OraLocale?

    var haFeedback: Bool { feedbackIl != nil }
    var haSegnali: Bool { pesoFermo || percezioneNegativa || energiaBassa }
    var feedbackDaLeggere: Bool { haFeedback && feedbackLettoIl == nil }
}

struct StatoCheck: Codable, Sendable {
    let settimana: Giorno
    let compilato: Bool
    let check: CheckDettaglio?
}

struct RiepilogoApp: Codable, Sendable {
    let settimana: Giorno
    let checkCompilato: Bool
    let feedbackDaLeggere: Int
    let messaggiDaLeggere: Int
}

enum AzioneCheck: String, EnumServer {
    case sollecito = "SOLLECITO", dedicato = "MESSAGGIO_DEDICATO", standard = "MESSAGGIO_STANDARD", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .sollecito: "Sollecitare il check"
        case .dedicato: "Messaggio dedicato"
        case .standard: "Messaggio motivazionale"
        case .sconosciuto: "—"
        }
    }
}

struct RigaSettimana: Codable, Identifiable, Hashable, Sendable {
    let pazienteId: Int
    let pazienteNome: String
    let cellulare: String?
    let statoPaziente: StatoPaziente
    let situazione: String?
    let nota: String?
    let azione: AzioneCheck
    let check: CheckDettaglio?
    let lunediFattoIl: OraLocale?
    let mercolediFattoIl: OraLocale?
    var id: Int { pazienteId }
}

struct SettimanaCheck: Codable, Sendable {
    let settimana: Giorno
    let attesi: Int
    let compilati: Int
    let conSegnali: Int
    let daContattareLunedi: Int
    let daContattareMercoledi: Int
    let righe: [RigaSettimana]
}

struct ContattoCheck: Codable, Sendable {
    let pazienteId: Int
    let settimana: Giorno
    let giorno: String
    let fatto: Bool
}

// MARK: Chat

enum Mittente: String, EnumServer {
    case paziente = "PAZIENTE", dottore = "DOTTORE", sconosciuto = ""
    var etichetta: String { rawValue }
}

struct RigaMessaggio: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let mittente: Mittente
    let testo: String
    let clientId: UUID?
    let inviatoIl: OraLocale
    let lettoIl: OraLocale?
}

struct Invio: Codable, Sendable {
    let testo: String
    let clientId: UUID
}

struct Conversazione: Codable, Identifiable, Hashable, Sendable {
    let pazienteId: Int
    let pazienteNome: String
    let ultimoMessaggio: RigaMessaggio?
    let nonLetti: Int
    var id: Int { pazienteId }
}

// MARK: Corsi

enum TipoLezione: String, EnumServer {
    case video = "VIDEO", testo = "TESTO", sconosciuto = ""
    var etichetta: String { self == .video ? "Video" : "Testo" }
}

enum StatoVideoLezione: String, EnumServer {
    case caricamento = "CARICAMENTO", elaborazione = "ELABORAZIONE", pronto = "PRONTO", errore = "ERRORE", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .caricamento: "In caricamento"
        case .elaborazione: "In elaborazione"
        case .pronto: "Pronto"
        case .errore: "Errore"
        case .sconosciuto: "—"
        }
    }
}

struct AppCorso: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let copertinaId: UUID?
    let lezioni: Int
    let secondiVideo: Int
}

struct AppLezioneRiga: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let tipo: TipoLezione
    let durataSecondi: Int?
}

struct AppModulo: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let copertinaId: UUID?
    let lezioni: [AppLezioneRiga]
}

struct AppCorsoDettaglio: Codable, Sendable {
    let corso: AppCorso
    let moduli: [AppModulo]
}

struct Riproduzione: Codable, Sendable {
    let iframeUrl: String?
    let hlsUrl: String?
}

struct AppLezione: Codable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let tipo: TipoLezione
    let durataSecondi: Int?
    let contenuto: String?
    let riproduzione: Riproduzione?
}

struct RigaCorso: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let copertinaId: UUID?
    let pubblicato: Bool
    let moduli: Int
    let lezioni: Int
    let lezioniPubblicate: Int
    let secondiVideo: Int
}

struct RigaModulo: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let copertinaId: UUID?
    let ordine: Int
}

struct RigaLezione: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let moduloId: Int
    let titolo: String
    let descrizione: String?
    let tipo: TipoLezione
    let ordine: Int
    let pubblicata: Bool
    let completa: Bool
    let contenuto: String?
    let videoStato: StatoVideoLezione?
    let videoErrore: String?
    let durataSecondi: Int?
}

struct ModuloConLezioni: Codable, Identifiable, Hashable, Sendable {
    let modulo: RigaModulo
    let lezioni: [RigaLezione]
    var id: Int { modulo.id }
}

struct StrutturaCorso: Codable, Sendable {
    let corso: RigaCorso
    let moduli: [ModuloConLezioni]
}

struct StatoVideo: Codable, Sendable {
    let stato: StatoVideoLezione?
    let avanzamento: Int?
    let lezione: RigaLezione?
}

struct UploadVideo: Codable, Sendable {
    let uploadUrl: String
}

struct SalvaTitolo: Codable, Sendable {
    let titolo: String
    let descrizione: String?
}

struct SalvaLezione: Codable, Sendable {
    let tipo: TipoLezione
    let titolo: String
    let descrizione: String?
    let contenuto: String?
}

struct Ordine: Codable, Sendable {
    let ids: [Int]
}

// MARK: Pagamenti e fatture

enum TipoPagamento: String, EnumServer {
    case acconto = "ACCONTO", unico = "UNICO", rata = "RATA", mantenimento = "MANTENIMENTO", altro = "ALTRO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .acconto: "Acconto"
        case .unico: "In anticipo"
        case .rata: "Rata"
        case .mantenimento: "Mantenimento"
        case .altro, .sconosciuto: "Altro"
        }
    }
}

enum StatoPagamento: String, EnumServer {
    case daPagare = "DA_PAGARE", pagato = "PAGATO", annullato = "ANNULLATO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .daPagare: "Da pagare"
        case .pagato: "Pagato"
        case .annullato: "Annullato"
        case .sconosciuto: "—"
        }
    }
}

enum MetodoPagamento: String, EnumServer {
    case bonifico = "BONIFICO", carta = "CARTA", contanti = "CONTANTI", paypal = "PAYPAL", altro = "ALTRO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .bonifico: "Bonifico"
        case .carta: "Carta"
        case .contanti: "Contanti"
        case .paypal: "PayPal"
        case .altro, .sconosciuto: "Altro"
        }
    }
}

struct RiepilogoPagamenti: Codable, Sendable {
    let pagato: Decimal
    let daPagare: Decimal
    let scaduto: Decimal
}

struct AppPagamento: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let descrizione: String
    let tipo: TipoPagamento?
    let importo: Decimal
    let scadenza: Giorno?
    let stato: StatoPagamento
    let scaduto: Bool
    let dataPagamento: Giorno?
    let metodo: MetodoPagamento?
    let fatturaId: Int?
}

struct AppListaPagamenti: Codable, Sendable {
    let riepilogo: RiepilogoPagamenti
    let righe: [AppPagamento]
}

struct AppFattura: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let numero: Int
    let dataEmissione: Giorno
    let descrizione: String?
    let importoTotale: Decimal
    let nomeFile: String?
}

struct RigaPagamento: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let pazienteId: Int
    let pazienteNome: String
    let percorsoId: Int?
    let percorsoTipo: TipoPercorso?
    let tipo: TipoPagamento?
    let descrizione: String
    let importo: Decimal
    let scadenza: Giorno?
    let stato: StatoPagamento
    let scaduto: Bool
    let dataPagamento: Giorno?
    let metodo: MetodoPagamento?
    let contabile: Bool
    let note: String?
    let fatturato: Bool
}

struct ListaPagamenti: Codable, Sendable {
    let riepilogo: RiepilogoPagamenti
    let righe: [RigaPagamento]
}

struct SalvaPagamento: Codable, Sendable {
    let pazienteId: Int
    let tipo: TipoPagamento?
    let descrizione: String
    let importo: Decimal
    let scadenza: Giorno
    let note: String?
}

struct RegistraPagamento: Codable, Sendable {
    let dataPagamento: Giorno
    let metodo: MetodoPagamento
    let contabile: Bool?
    let note: String?
}

enum StatoFattura: String, EnumServer {
    case emessa = "EMESSA", annullata = "ANNULLATA", sconosciuto = ""
    var etichetta: String { self == .emessa ? "Emessa" : "Annullata" }
}

enum StatoTessera: String, EnumServer {
    case daInserire = "DA_INSERIRE", inserita = "INSERITA", errore = "ERRORE", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .daInserire: "Da inserire"
        case .inserita: "Inserita"
        case .errore: "Errore"
        case .sconosciuto: "—"
        }
    }
}

struct RigaFattura: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let numero: Int
    let dataEmissione: Giorno
    let stato: StatoFattura
    let pazienteId: Int
    let pagamentoId: Int?
    let intestatario: String?
    let indirizzo: String?
    let codiceFiscale: String?
    let descrizione: String?
    let imponibile: Decimal?
    let contributo: Decimal?
    let bollo: Decimal?
    let importoTotale: Decimal
    let motivoAnnullamento: String?
    let emailPaziente: String?
    let emailDestinatario: String?
    let emailInviataIl: OraLocale?
    let tsStato: StatoTessera?
    let tsInseritaIl: OraLocale?
    let tsOpposizione: Bool
    let nomeFile: String?
}

struct EmettiFattura: Codable, Sendable {
    var pagamentoId: Int?
    var pazienteId: Int?
    var importo: Decimal?
    var data: Giorno?
    var descrizione: String?
    var tsOpposizione: Bool?
}

struct AnteprimaFattura: Codable, Sendable {
    let numero: Int
    let data: Giorno
    let intestatario: String?
    let indirizzo: String?
    let codiceFiscale: String?
    let descrizione: String?
    let imponibile: Decimal
    let contributo: Decimal
    let bollo: Decimal
    let importoTotale: Decimal
}

struct AnnullaFattura: Codable, Sendable {
    let motivo: String
}

struct RigaTessera: Codable, Identifiable, Hashable, Sendable {
    let fatturaId: Int
    let numero: Int
    let data: Giorno
    let codiceFiscale: String?
    let importo: Decimal
    var id: Int { fatturaId }
}

// MARK: Percorsi e visite

enum TipoPercorso: String, EnumServer {
    case bussola = "METODO_BUSSOLA", rinnovo = "RINNOVO", mantenimento = "MANTENIMENTO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .bussola: "Percorso 6 mesi"
        case .rinnovo: "Rinnovo 6 mesi"
        case .mantenimento: "Mantenimento"
        case .sconosciuto: "Percorso"
        }
    }
}

enum PianoPagamento: String, EnumServer {
    case unico = "UNICO", rate = "RATE", sconosciuto = ""
    var etichetta: String { self == .unico ? "In anticipo" : "A rate" }
}

enum StatoPercorso: String, EnumServer {
    case attivo = "ATTIVO", concluso = "CONCLUSO", annullato = "ANNULLATO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .attivo: "In corso"
        case .concluso: "Concluso"
        case .annullato: "Annullato"
        case .sconosciuto: "—"
        }
    }
}

struct AppPercorso: Codable, Hashable, Sendable {
    let id: Int
    let tipo: TipoPercorso
    let dataInizio: Giorno
    let dataFine: Giorno?
    let visiteTotali: Int
    let visiteFissate: Int
    let visiteEffettuate: Int
    let stato: StatoPercorso
}

enum TipoVisita: String, EnumServer {
    case chiamata = "CHIAMATA_CONOSCITIVA", prima = "PRIMA_VISITA", controllo = "CONTROLLO", mantenimento = "MANTENIMENTO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .chiamata: "Chiamata conoscitiva"
        case .prima: "Prima visita"
        case .controllo: "Controllo"
        case .mantenimento: "Mantenimento"
        case .sconosciuto: "Visita"
        }
    }
    var minuti: Int { self == .prima ? 60 : 30 }
}

enum ModalitaVisita: String, EnumServer {
    case online = "ONLINE", presenza = "PRESENZA", sconosciuto = ""
    var etichetta: String { self == .online ? "Online" : "Dal vivo" }
}

enum StatoVisita: String, EnumServer {
    case programmata = "PROGRAMMATA", effettuata = "EFFETTUATA", annullata = "ANNULLATA", nonPresentato = "NON_PRESENTATO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .programmata: "Programmata"
        case .effettuata: "Effettuata"
        case .annullata: "Annullata"
        case .nonPresentato: "Non presentato"
        case .sconosciuto: "—"
        }
    }
}

struct AppVisita: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let numeroVisita: Int?
    let visiteTotali: Int?
    let tipo: TipoVisita
    let modalita: ModalitaVisita
    let stato: StatoVisita
    let inizio: OraLocale
    let fine: OraLocale
    let meetUrl: String?
}

struct PercorsoPaziente: Codable, Sendable {
    let percorso: AppPercorso?
    let visite: [AppVisita]
}

struct SintesiPercorso: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let pazienteId: Int
    let tipo: TipoPercorso
    let pianoPagamento: PianoPagamento?
    let dataInizio: Giorno
    let dataFine: Giorno?
    let visiteTotali: Int
    let visiteFissate: Int
    let visiteEffettuate: Int
    let importoTotale: Decimal
    let stato: StatoPercorso
    let note: String?
}

struct CreaPercorso: Codable, Sendable {
    let pazienteId: Int
    let tipo: TipoPercorso
    let pianoPagamento: PianoPagamento
    let dataInizio: Giorno
    let note: String?
}

struct Acquisto: Codable, Identifiable, Hashable, Sendable {
    let percorso: SintesiPercorso
    let pazienteNome: String
    let pagato: Decimal
    let daPagare: Decimal
    let scaduto: Decimal
    let prossimaScadenza: Giorno?
    let pagamenti: [RigaPagamento]
    var id: Int { percorso.id }
}

struct Acquisti: Codable, Sendable {
    let percorsi: [Acquisto]
    let fuoriPercorso: [RigaPagamento]
}

struct Listino: Codable, Sendable {
    let unico: Decimal
    let rataPercorso: Decimal
    let acconto: Decimal
    let mantenimento: Decimal
    let rataMantenimento: Decimal
}

enum SincronizzazioneGoogle: String, EnumServer {
    case daSincronizzare = "DA_SINCRONIZZARE", sincronizzata = "SINCRONIZZATA", errore = "ERRORE", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .daSincronizzare: "In attesa di Google"
        case .sincronizzata: "Su Google Calendar"
        case .errore: "Errore Google"
        case .sconosciuto: "—"
        }
    }
}

struct RigaVisita: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let pazienteId: Int
    let pazienteNome: String
    let pazienteEmail: String?
    let pazienteCellulare: String?
    let percorsoId: Int?
    let percorsoTipo: TipoPercorso?
    let numeroVisita: Int?
    let visiteTotali: Int?
    let tipo: TipoVisita
    let modalita: ModalitaVisita
    let stato: StatoVisita
    let inizio: OraLocale
    let fine: OraLocale
    let conMeet: Bool
    let meetUrl: String?
    let invitaPaziente: Bool
    let note: String?
    let titoloCalendario: String?
    let googleSyncStato: SincronizzazioneGoogle?
    let googleSyncErrore: String?
}

struct SalvaVisita: Codable, Sendable {
    let pazienteId: Int
    let tipo: TipoVisita
    let modalita: ModalitaVisita
    let inizio: OraLocale
    let durataMinuti: Int?
    let conMeet: Bool?
    let invitaPaziente: Bool?
    let note: String?
}

struct CambiaStatoVisita: Codable, Sendable {
    let stato: StatoVisita
}

// MARK: Pazienti

enum StatoPaziente: String, EnumServer {
    case lead = "LEAD", listaAttesa = "LISTA_ATTESA", attivo = "ATTIVO", mantenimento = "MANTENIMENTO", concluso = "CONCLUSO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .lead: "Lead"
        case .listaAttesa: "Lista d'attesa"
        case .attivo: "Attivo"
        case .mantenimento: "Mantenimento"
        case .concluso: "Concluso"
        case .sconosciuto: "—"
        }
    }
}

enum FontePaziente: String, EnumServer {
    case youtube = "YOUTUBE", instagram = "INSTAGRAM", masterclass = "MASTERCLASS", libro = "LIBRO",
         passaparola = "PASSAPAROLA", sito = "SITO", altro = "ALTRO", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .youtube: "YouTube"
        case .instagram: "Instagram"
        case .masterclass: "Masterclass"
        case .libro: "Libro"
        case .passaparola: "Passaparola"
        case .sito: "Sito"
        case .altro, .sconosciuto: "Altro"
        }
    }
}

struct RigaPaziente: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let nome: String
    let cognome: String
    let email: String?
    let cellulare: String?
    let codiceFiscale: String?
    let dataNascita: Giorno?
    let indirizzo: String?
    let cap: String?
    let citta: String?
    let provincia: String?
    let stato: StatoPaziente
    let fonte: FontePaziente?
    let situazione: String?
    let nota: String?
    let pesoInizialeKg: Decimal?
    let pesoObiettivoKg: Decimal?
    let createdAt: OraLocale?
    let percorso: SintesiPercorso?
    let prossimaVisita: OraLocale?
    let situazionePagamenti: String?
    let residuo: Decimal?
    let checkSettimana: Bool
    let accessoApp: Bool

    var nomeCompleto: String { "\(nome) \(cognome)" }
}

struct SalvaPaziente: Codable, Sendable {
    var nome: String
    var cognome: String
    var email: String?
    var cellulare: String?
    var codiceFiscale: String?
    var dataNascita: Giorno?
    var indirizzo: String?
    var cap: String?
    var citta: String?
    var provincia: String?
    var stato: StatoPaziente?
    var fonte: FontePaziente?
    var situazione: String?
    var nota: String?
    var pesoInizialeKg: Decimal?
    var pesoObiettivoKg: Decimal?
}

struct InvitoApp: Codable, Sendable {
    let link: String
    let emailInviata: Bool
}

// MARK: Task

enum PrioritaTask: String, EnumServer {
    case bassa = "BASSA", media = "MEDIA", alta = "ALTA", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .bassa: "Bassa"
        case .media: "Media"
        case .alta: "Alta"
        case .sconosciuto: "—"
        }
    }
}

enum StatoTask: String, EnumServer {
    case daFare = "DA_FARE", fatto = "FATTO", sconosciuto = ""
    var etichetta: String { self == .fatto ? "Fatto" : "Da fare" }
}

struct TipoTask: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let codice: String
    let etichetta: String
    let titoloDefault: String?
    let giorniDefault: Int
    let priorita: PrioritaTask
    let icona: String?
}

struct RigaTask: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let descrizione: String?
    let tipo: TipoTask
    let stato: StatoTask
    let priorita: PrioritaTask
    let scadenza: Giorno?
    let pazienteId: Int?
    let pazienteNome: String?
    let automatico: Bool
    let completatoIl: OraLocale?
    let inRitardo: Bool
}

struct SalvaTask: Codable, Sendable {
    let tipoCodice: String
    let titolo: String?
    let descrizione: String?
    let scadenza: Giorno?
    let priorita: PrioritaTask?
    let pazienteId: Int?
}

// MARK: Dashboard

struct Rinnovo: Codable, Identifiable, Hashable, Sendable {
    let pazienteId: Int
    let pazienteNome: String
    let percorsoId: Int
    let visiteFissate: Int
    let visiteTotali: Int
    let dataFine: Giorno?
    var id: Int { percorsoId }
}

struct IncassoMese: Codable, Identifiable, Hashable, Sendable {
    let mese: Int
    let importo: Decimal
    var id: Int { mese }
}

struct AndamentoCheck: Codable, Identifiable, Hashable, Sendable {
    let settimana: Giorno
    let compilati: Int
    let segnali: Int
    let energiaMedia: Double?
    let impegnoMedio: Double?
    var id: Giorno { settimana }
}

struct Dashboard: Codable, Sendable {
    let pazientiAttivi: Int
    let pazientiMantenimento: Int
    let lead: Int
    let listaAttesa: Int
    let settimana: Giorno
    let checkAttesi: Int
    let checkCompilati: Int
    let checkConSegnali: Int
    let daContattareOggi: Int
    let visiteSettimana: Int
    let visiteOggi: [RigaVisita]
    let taskAperti: Int
    let taskInRitardo: Int
    let taskUrgenti: [RigaTask]
    let incassatoMese: Decimal
    let incassatoAnno: Decimal
    let daIncassare: Decimal
    let scaduto: Decimal
    let margineSoglia: Decimal?
    let candidatureAperte: Bool
    let rinnovi: [Rinnovo]
    let incassiMensili: [IncassoMese]
    let andamentoCheck: [AndamentoCheck]
    let prossimeScadenze: [ScadenzaFiscale]
}

// MARK: Finance

enum TipoMovimento: String, EnumServer {
    case entrata = "ENTRATA", uscita = "USCITA", sconosciuto = ""
    var etichetta: String { self == .entrata ? "Entrata" : "Uscita" }
}

enum CategoriaMovimento: String, EnumServer {
    case youtubeAds = "YOUTUBE_ADS", consulenza = "CONSULENZA", altroRicavo = "ALTRO_RICAVO",
         imposte = "IMPOSTE", cassaPrevidenza = "CASSA_PREVIDENZA", ordine = "ORDINE",
         commercialista = "COMMERCIALISTA", software = "SOFTWARE", altraSpesa = "ALTRA_SPESA", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .youtubeAds: "YouTube Ads"
        case .consulenza: "Consulenza"
        case .altroRicavo: "Altro ricavo"
        case .imposte: "Imposte"
        case .cassaPrevidenza: "Cassa previdenza"
        case .ordine: "Ordine"
        case .commercialista: "Commercialista"
        case .software: "Software"
        case .altraSpesa: "Altra spesa"
        case .sconosciuto: "—"
        }
    }

    static func di(_ tipo: TipoMovimento) -> [CategoriaMovimento] {
        tipo == .entrata ? [.youtubeAds, .consulenza, .altroRicavo]
            : [.imposte, .cassaPrevidenza, .ordine, .commercialista, .software, .altraSpesa]
    }
}

struct MeseFinance: Codable, Identifiable, Hashable, Sendable {
    let mese: Int
    let pazienti: Decimal
    let youtube: Decimal
    let altro: Decimal
    let lordo: Decimal
    let impostaSostitutiva: Decimal
    let contributoInFattura: Decimal
    let cassaPrevidenziale: Decimal
    let netto: Decimal
    let uscite: Decimal
    var id: Int { mese }
}

struct LivelloFinance: Codable, Identifiable, Hashable, Sendable {
    let nome: String
    let descrizione: String?
    let totale: Decimal
    let margineObiettivo: Decimal
    let margineSoglia: Decimal
    var id: String { nome }
}

struct RiepilogoFinance: Codable, Sendable {
    let anno: Int
    let mesi: [MeseFinance]
    let totali: MeseFinance
    let livelli: [LivelloFinance]
    let soglia: Decimal
    let obiettivo: Decimal
    let valoreNuovoCliente: Decimal
    let candidatureAperte: Bool
    let saldoCassa: Decimal
}

struct Movimento: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let data: Giorno
    let tipo: TipoMovimento
    let categoria: CategoriaMovimento
    let descrizione: String
    let importo: Decimal
    let note: String?
}

struct SalvaMovimento: Codable, Sendable {
    let data: Giorno
    let tipo: TipoMovimento
    let categoria: CategoriaMovimento
    let descrizione: String
    let importo: Decimal
    let note: String?
}

enum StatoScadenza: String, EnumServer {
    case daPagare = "DA_PAGARE", pagata = "PAGATA", sconosciuto = ""
    var etichetta: String { self == .pagata ? "Pagata" : "Da pagare" }
}

struct ScadenzaFiscale: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let titolo: String
    let categoria: CategoriaMovimento
    let dataScadenza: Giorno
    let importoStimato: Decimal?
    let stato: StatoScadenza
    let pagataIl: Giorno?
    let note: String?
}

struct SalvaScadenza: Codable, Sendable {
    let titolo: String
    let categoria: CategoriaMovimento
    let dataScadenza: Giorno
    let importoStimato: Decimal?
    let note: String?
}

struct PagaScadenza: Codable, Sendable {
    let data: Giorno
    let importo: Decimal
}

// MARK: Email e impostazioni

enum TipoEmail: String, EnumServer {
    case sollecito = "SOLLECITO_PAGAMENTO", dati = "DATI_FATTURAZIONE", fattura = "FATTURA", sconosciuto = ""
    var etichetta: String {
        switch self {
        case .sollecito: "Sollecito pagamento"
        case .dati: "Richiesta dati fatturazione"
        case .fattura: "Invio fattura"
        case .sconosciuto: "Email"
        }
    }
}

struct RichiestaEmail: Codable, Hashable, Identifiable, Sendable {
    let tipo: TipoEmail
    var pazienteId: Int?
    var pagamentoId: Int?
    var fatturaId: Int?
    var id: String { "\(tipo.rawValue)-\(pazienteId ?? 0)-\(pagamentoId ?? 0)-\(fatturaId ?? 0)" }
}

struct BozzaEmail: Codable, Sendable {
    let tipo: TipoEmail
    let pazienteId: Int?
    let pazienteNome: String?
    let pagamentoId: Int?
    let fatturaId: Int?
    let mittente: String?
    let destinatario: String?
    let oggetto: String
    let testo: String
    let allegato: String?
    let invioAttivo: Bool
}

struct InviaEmail: Codable, Sendable {
    let tipo: TipoEmail
    let pazienteId: Int?
    let pagamentoId: Int?
    let fatturaId: Int?
    let destinatario: String
    let oggetto: String
    let corpo: String
}

struct VoceImpostazione: Codable, Identifiable, Hashable, Sendable {
    let chiave: String
    let valore: String?
    let descrizione: String?
    var id: String { chiave }
}

struct Integrazione: Codable, Identifiable, Hashable, Sendable {
    let codice: String
    let nome: String
    let attiva: Bool
    let configurata: Bool
    let nota: String?
    var id: String { codice }
}

struct Panoramica: Codable, Sendable {
    let voci: [VoceImpostazione]
    let integrazioni: [Integrazione]
}

struct AggiornaImpostazione: Codable, Sendable {
    let chiave: String
    let valore: String?
}
