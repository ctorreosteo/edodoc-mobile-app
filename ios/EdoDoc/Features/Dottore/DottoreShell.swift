import Charts
import SwiftUI

struct DottoreShell: View {
    @Environment(AppModel.self) private var app
    @Environment(\.scenePhase) private var fase
    @State private var scheda = 0
    @State private var nonLetti = 0
    @State private var chatAperta: Int?

    var body: some View {
        TabView(selection: $scheda) {
            NavigationStack { CruscottoView(vaiAlCheck: { scheda = 2 }) }
                .tabItem { Label("Oggi", systemImage: "sun.horizon") }
                .tag(0)
            NavigationStack { PazientiView() }
                .tabItem { Label("Pazienti", systemImage: "person.2") }
                .tag(1)
            NavigationStack { CheckSettimanaView() }
                .tabItem { Label("Check", systemImage: "checklist") }
                .tag(2)
            NavigationStack { ConversazioniView(apri: $chatAperta) }
                .tabItem { Label("Chat", systemImage: "bubble.left") }
                .badge(nonLetti)
                .tag(3)
            NavigationStack { AltroView() }
                .tabItem { Label("Altro", systemImage: "square.grid.2x2") }
                .tag(4)
        }
        .task(id: fase) {
            guard fase == .active else { return }
            let conversazioni: [Conversazione]? = try? await app.api.get("/chat/conversazioni")
            nonLetti = conversazioni?.reduce(0) { $0 + $1.nonLetti } ?? nonLetti
        }
        .onChange(of: app.destinazione) { _, _ in segui() }
        .onAppear { segui() }
    }

    private func segui() {
        guard let destinazione = app.destinazione else { return }
        app.destinazione = nil
        switch destinazione {
        case .chat(let pazienteId):
            scheda = 3
            chatAperta = pazienteId
        case .feedback:
            scheda = 2
        }
    }
}

struct CruscottoView: View {
    let vaiAlCheck: () -> Void
    @Environment(AppModel.self) private var app

    var body: some View {
        Caricamento {
            let dati: Dashboard = try await app.api.get("/dashboard")
            return dati
        } contenuto: { dati, aggiorna in
            contenuto(dati).refreshable { await aggiorna() }
        }
        .background { Brace(fuoco: .init(x: 0.8, y: 0.12), intensita: 0.9) }
        .toolbar(.hidden, for: .navigationBar)
    }

    private func attenzione(_ d: Dashboard) -> Int { d.checkConSegnali + d.taskInRitardo + (d.scaduto > 0 ? 1 : 0) }

    private func contenuto(_ d: Dashboard) -> some View {
        let quante = attenzione(d)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Overline(Giorno.oggi.esteso).padding(.bottom, 44)
                Text("\(quante)")
                    .font(Tipografia.display(112))
                    .foregroundStyle(Tema.ink)
                    .accessibilityIdentifier("cruscotto.numero")
                Text(quante == 1 ? "cosa chiede la tua attenzione." : "cose chiedono la tua attenzione.")
                    .font(Tipografia.headline)
                    .foregroundStyle(Tema.inkSecondario)
                    .padding(.bottom, 32)

                VStack(spacing: 0) {
                    if d.checkConSegnali > 0 {
                        Button(action: vaiAlCheck) {
                            rigaAttenzione("\(d.checkConSegnali) check con segnali", colore: Tema.ember)
                        }.buttonStyle(Premibile())
                        Filetto()
                    }
                    if d.taskInRitardo > 0 {
                        NavigationLink { TaskView() } label: {
                            rigaAttenzione("\(d.taskInRitardo) task in ritardo", colore: Tema.critico)
                        }.buttonStyle(Premibile())
                        Filetto()
                    }
                    if d.scaduto > 0 {
                        NavigationLink { PagamentiView() } label: {
                            rigaAttenzione("\(Formati.euro(d.scaduto)) di pagamenti scaduti", colore: Tema.critico)
                        }.buttonStyle(Premibile())
                        Filetto()
                    }
                }
                .padding(.bottom, 30)

                HStack(alignment: .top, spacing: 16) {
                    NumeroEroe(valore: "\(d.checkCompilati)/\(d.checkAttesi)", didascalia: "Check settimana")
                    NumeroEroe(valore: "\(d.pazientiAttivi)", didascalia: "Pazienti attivi")
                }
                .padding(.bottom, 22)
                HStack(alignment: .top, spacing: 16) {
                    NumeroEroe(valore: Formati.euroIntero(d.incassatoMese), didascalia: "Incassato nel mese")
                    NumeroEroe(valore: Formati.euroIntero(d.daIncassare), didascalia: "Da incassare")
                }
                .padding(.bottom, 34)

                sezione("Oggi in agenda", vuota: d.visiteOggi.isEmpty) {
                    ForEach(d.visiteOggi) { visita in
                        NavigationLink { VisitaDettaglioView(visita: visita) } label: {
                            RigaVisitaView(visita: visita)
                        }.buttonStyle(Premibile())
                        Filetto()
                    }
                }
                sezione("Task urgenti", vuota: d.taskUrgenti.isEmpty) {
                    ForEach(d.taskUrgenti) { task in
                        NavigationLink { TaskView() } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(task.titolo).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                Text([task.pazienteNome, task.scadenza?.breve].compactMap { $0 }.joined(separator: " · "))
                                    .font(Tipografia.nota)
                                    .foregroundStyle(task.inRitardo ? Tema.critico : Tema.inkSecondario)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 12)
                        }.buttonStyle(Premibile())
                        Filetto()
                    }
                }
                sezione("Rinnovi da proporre", vuota: d.rinnovi.isEmpty) {
                    ForEach(d.rinnovi) { rinnovo in
                        RigaDato(etichetta: rinnovo.pazienteNome, valore: "\(rinnovo.visiteFissate) di \(rinnovo.visiteTotali)")
                        Filetto()
                    }
                }
                if !d.incassiMensili.isEmpty {
                    Overline("Incassi dell'anno").padding(.bottom, 14)
                    Chart(d.incassiMensili) { riga in
                        BarMark(x: .value("Mese", riga.mese), y: .value("Incasso", NSDecimalNumber(decimal: riga.importo).doubleValue), width: 12)
                            .cornerRadius(6)
                            .foregroundStyle(riga.mese == Giorno.oggi.mese ? Tema.ember : Tema.inkTerziario)
                    }
                    .chartXAxis(.hidden)
                    .chartYAxis(.hidden)
                    .frame(height: 120)
                    .accessibilityLabel("Incassi mensili dell'anno")
                    .padding(.bottom, 30)
                }
                sezione("Scadenze fiscali", vuota: d.prossimeScadenze.isEmpty) {
                    ForEach(d.prossimeScadenze) { scadenza in
                        RigaDato(etichetta: scadenza.titolo, valore: scadenza.dataScadenza.breve)
                        Filetto()
                    }
                }
            }
            .padding(.horizontal, Tema.margine + 2)
            .padding(.top, 14)
            .padding(.bottom, 30)
        }
    }

    private func rigaAttenzione(_ testo: String, colore: Color) -> some View {
        HStack(spacing: 12) {
            Ellipse().fill(colore).frame(width: 11, height: 8)
            Text(testo).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Tema.inkTerziario)
        }
        .frame(minHeight: 54)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func sezione<C: View>(_ titolo: String, vuota: Bool, @ViewBuilder contenuto: () -> C) -> some View {
        if !vuota {
            VStack(alignment: .leading, spacing: 0) {
                Overline(titolo).padding(.bottom, 6)
                contenuto()
            }
            .padding(.bottom, 30)
        }
    }
}

struct AltroView: View {
    private struct Voce: Identifiable {
        let titolo: String, icona: String
        let destinazione: AnyView
        var id: String { titolo }
    }

    private let voci: [Voce] = [
        Voce(titolo: "Agenda", icona: "calendar", destinazione: AnyView(AgendaView())),
        Voce(titolo: "Acquisti", icona: "bag", destinazione: AnyView(AcquistiView())),
        Voce(titolo: "Pagamenti", icona: "creditcard", destinazione: AnyView(PagamentiView())),
        Voce(titolo: "Fatture", icona: "doc.text", destinazione: AnyView(FattureDottoreView())),
        Voce(titolo: "Corsi", icona: "play.rectangle", destinazione: AnyView(CorsiDottoreView())),
        Voce(titolo: "Finance", icona: "chart.bar", destinazione: AnyView(FinanceView())),
        Voce(titolo: "Task", icona: "checkmark.circle", destinazione: AnyView(TaskView())),
        Voce(titolo: "Impostazioni", icona: "gearshape", destinazione: AnyView(ImpostazioniView())),
    ]

    var body: some View {
        List(voci) { voce in
            NavigationLink {
                voce.destinazione
            } label: {
                HStack(spacing: 16) {
                    Image(systemName: voce.icona)
                        .font(.system(size: 19, weight: .light))
                        .foregroundStyle(Tema.ember)
                        .frame(width: 30)
                    Text(voce.titolo)
                        .font(Tipografia.bodyMedio)
                        .foregroundStyle(Tema.ink)
                }
                .frame(minHeight: 40)
            }
            .rigaEdo()
            .accessibilityIdentifier("altro.\(voce.titolo)")
        }
        .listaEdo()
        .telaEdo()
        .navigationTitle("Altro")
    }
}

// MARK: Conversazioni

struct ConversazioniView: View {
    @Binding var apri: Int?
    @Environment(AppModel.self) private var app
    @State private var percorso = NavigationPath()

    var body: some View {
        Caricamento {
            let elenco: [Conversazione] = try await app.api.get("/chat/conversazioni")
            return elenco
        } contenuto: { elenco, aggiorna in
            Group {
                if elenco.isEmpty {
                    StatoVuoto(titolo: "Nessuna conversazione",
                               testo: "I messaggi dei pazienti arriveranno qui. Per iniziare tu, apri la scheda del paziente.",
                               icona: "bubble.left")
                } else {
                    List(elenco) { c in
                        NavigationLink {
                            ChatView(canale: .dottore(pazienteId: c.pazienteId), titolo: c.pazienteNome)
                        } label: {
                            HStack(spacing: 14) {
                                Avatar(nome: c.pazienteNome)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(c.pazienteNome).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                    Text(c.ultimoMessaggio?.testo ?? "")
                                        .font(Tipografia.nota)
                                        .foregroundStyle(Tema.inkSecondario)
                                        .lineLimit(1)
                                }
                                Spacer()
                                if c.nonLetti > 0 {
                                    Text("\(c.nonLetti)")
                                        .font(Tipografia.overline)
                                        .foregroundStyle(Tema.suEmber)
                                        .frame(minWidth: 22, minHeight: 22)
                                        .background(Tema.ember, in: Capsule())
                                }
                            }
                        }
                        .rigaEdo()
                    }
                    .listaEdo()
                    .refreshable { await aggiorna() }
                }
            }
        }
        .telaEdo()
        .navigationTitle("Chat")
        .navigationDestination(item: $apri) { pazienteId in
            ChatView(canale: .dottore(pazienteId: pazienteId), titolo: "Paziente")
        }
    }
}

// MARK: Pezzi comuni dell'area dottore

extension String {
    var nonVuoto: String? {
        let pulito = trimmingCharacters(in: .whitespacesAndNewlines)
        return pulito.isEmpty ? nil : pulito
    }
}

extension Optional where Wrapped == String {
    var testo: String { self ?? "" }
}

extension Decimal {
    /// Accetta sia la virgola sia il punto.
    init?(italiano testo: String) {
        let pulito = testo.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard !pulito.isEmpty, let valore = Decimal(string: pulito, locale: Locale(identifier: "en_US_POSIX")) else { return nil }
        self = valore
    }

    var testoModulo: String { "\(self)".replacingOccurrences(of: ".", with: ",") }
}

/// Foglio con un modulo: Annulla, Salva e gli errori del server mostrati sopra al modulo.
struct ModuloFoglio<Contenuto: View>: View {
    let titolo: String
    var etichettaSalva = "Salva"
    let valido: Bool
    let salva: () async throws -> Void
    @ViewBuilder let contenuto: () -> Contenuto
    @Environment(\.dismiss) private var chiudi
    @Environment(AppModel.self) private var app
    @State private var inCorso = false

    var body: some View {
        NavigationStack {
            Form { contenuto() }
                .moduloEdo()
                .telaEdo()
                .navigationTitle(titolo)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Annulla") { chiudi() } }
                    ToolbarItem(placement: .confirmationAction) {
                        if inCorso {
                            ProgressView()
                        } else {
                            Button(etichettaSalva) {
                                inCorso = true
                                Task {
                                    let riuscito = await app.esegui { try await salva() }
                                    inCorso = false
                                    if riuscito { chiudi() }
                                }
                            }
                            .disabled(!valido)
                        }
                    }
                }
        }
    }
}

/// Scelta del giorno con il calendario di sistema, sempre in ora di Roma.
struct CampoGiorno: View {
    let etichetta: String
    @Binding var giorno: Giorno

    var body: some View {
        DatePicker(etichetta, selection: Binding(get: { giorno.data }, set: { giorno = Giorno($0) }), displayedComponents: .date)
            .environment(\.timeZone, Roma.fuso)
            .environment(\.locale, Roma.locale)
    }
}

struct SceltaPaziente: View {
    @Binding var selezione: Int?
    @Environment(AppModel.self) private var app
    @State private var pazienti: [RigaPaziente] = []

    var body: some View {
        Picker("Paziente", selection: $selezione) {
            Text("Scegli").tag(Int?.none)
            ForEach(pazienti) { Text($0.nomeCompleto).tag(Int?.some($0.id)) }
        }
        .task {
            if let elenco: [RigaPaziente] = try? await app.api.get("/pazienti") { pazienti = elenco }
        }
    }
}

/// Avanti e indietro di un mese.
struct SelettoreMese: View {
    @Binding var mese: Giorno

    var body: some View {
        HStack {
            Button { mese = mese.aggiungendo(mesi: -1) } label: {
                Image(systemName: "chevron.left").frame(width: 44, height: 44)
            }.accessibilityLabel("Mese precedente")
            Spacer()
            Text(mese.meseAnno.capitalized(with: Roma.locale))
                .font(Tipografia.bodyMedio)
                .foregroundStyle(Tema.ink)
            Spacer()
            Button { mese = mese.aggiungendo(mesi: 1) } label: {
                Image(systemName: "chevron.right").frame(width: 44, height: 44)
            }.accessibilityLabel("Mese successivo")
        }
    }
}

extension URLQueryItem {
    static func periodo(_ mese: Giorno) -> [URLQueryItem] {
        [URLQueryItem(name: "da", value: mese.primoDelMese.testo), URLQueryItem(name: "a", value: mese.ultimoDelMese.testo)]
    }
}

struct EmailView: View {
    let richiesta: RichiestaEmail
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var bozza: BozzaEmail?
    @State private var destinatario = ""
    @State private var oggetto = ""
    @State private var corpo = ""
    @State private var errore: String?

    var body: some View {
        if let bozza {
            ModuloFoglio(titolo: bozza.tipo.etichetta, etichettaSalva: "Invia",
                         valido: bozza.invioAttivo && destinatario.nonVuoto != nil && oggetto.nonVuoto != nil && corpo.nonVuoto != nil) {
                let _: Vuoto = try await app.api.post("/email/invia", InviaEmail(
                    tipo: bozza.tipo, pazienteId: bozza.pazienteId, pagamentoId: bozza.pagamentoId, fatturaId: bozza.fatturaId,
                    destinatario: destinatario, oggetto: oggetto, corpo: corpo))
                app.mostra("Email inviata")
            } contenuto: {
                if !bozza.invioAttivo {
                    Section {
                        Text("L'invio da Gmail è spento. Si accende da Altro, Impostazioni.")
                            .foregroundStyle(Tema.attenzione)
                    }
                }
                Section {
                    TextField("Destinatario", text: $destinatario)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    TextField("Oggetto", text: $oggetto)
                }
                Section {
                    TextField("Testo", text: $corpo, axis: .vertical)
                        .lineLimit(8...20)
                }
                if let allegato = bozza.allegato {
                    Section { Label(allegato, systemImage: "paperclip") }
                }
            }
        } else {
            VStack {
                if let errore {
                    StatoVuoto(titolo: "Bozza non disponibile", testo: errore, azione: ("Chiudi", { chiudi() }))
                } else {
                    Scheletro()
                }
            }
            .task {
                do {
                    let caricata: BozzaEmail = try await app.api.post("/email/bozza", richiesta)
                    destinatario = caricata.destinatario.testo
                    oggetto = caricata.oggetto
                    corpo = caricata.testo
                    bozza = caricata
                } catch {
                    errore = error.localizedDescription
                }
            }
        }
    }
}
