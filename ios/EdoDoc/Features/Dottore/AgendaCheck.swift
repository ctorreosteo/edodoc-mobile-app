import AVFoundation
import Observation
import SwiftUI

// MARK: Agenda

struct RigaVisitaView: View {
    let visita: RigaVisita

    var body: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 1) {
                Text(visita.inizio.ora).font(Tipografia.bodyMedio).monospacedDigit().foregroundStyle(Tema.ink)
                Text(visita.inizio.giorno.breve).font(Tipografia.nota).foregroundStyle(Tema.inkTerziario)
            }
            .frame(width: 66, alignment: .leading)
            VStack(alignment: .leading, spacing: 3) {
                Text(visita.pazienteNome).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                Text(descrizione).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
            }
            Spacer()
            Ellipse().fill(visita.stato.colore).frame(width: 11, height: 8)
                .accessibilityLabel(visita.stato.etichetta)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private var descrizione: String {
        var parti = [visita.tipo.etichetta]
        if let n = visita.numeroVisita, let t = visita.visiteTotali { parti.append("\(n) di \(t)") }
        parti.append(visita.modalita.etichetta)
        return parti.joined(separator: " · ")
    }
}

struct AgendaView: View {
    @Environment(AppModel.self) private var app
    @State private var settimana = Giorno.oggi.lunedi
    @State private var nuova = false

    var body: some View {
        Caricamento(chiave: settimana) {
            let elenco: [RigaVisita] = try await app.api.get("/visite", [
                URLQueryItem(name: "da", value: settimana.testo),
                URLQueryItem(name: "a", value: settimana.aggiungendo(giorni: 6).testo)])
            return elenco.sorted { $0.inizio < $1.inizio }
        } contenuto: { elenco, aggiorna in
            List {
                HStack {
                    Button { settimana = settimana.aggiungendo(giorni: -7) } label: {
                        Image(systemName: "chevron.left").frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Settimana precedente")
                    Spacer()
                    Text("\(settimana.breve) – \(settimana.aggiungendo(giorni: 6).breve)")
                        .font(Tipografia.bodyMedio)
                        .foregroundStyle(Tema.ink)
                    Spacer()
                    Button { settimana = settimana.aggiungendo(giorni: 7) } label: {
                        Image(systemName: "chevron.right").frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Settimana successiva")
                }
                .foregroundStyle(Tema.ember)
                .rigaEdo()
                .listRowSeparator(.hidden)
                if elenco.isEmpty {
                    StatoVuoto(titolo: "Settimana libera", icona: "calendar").rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(Array(Dictionary(grouping: elenco, by: \.inizio.giorno).keys.sorted(by: <)), id: \.self) { giorno in
                    Section {
                        ForEach(elenco.filter { $0.inizio.giorno == giorno }) { visita in
                            NavigationLink { VisitaDettaglioView(visita: visita) } label: {
                                RigaVisitaView(visita: visita)
                            }
                            .rigaEdo()
                        }
                    } header: {
                        Overline(giorno.esteso)
                    }
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $nuova, onDismiss: { Task { await aggiorna() } }) {
                VisitaFormView(visita: nil, pazienteId: nil)
            }
        }
        .telaEdo()
        .navigationTitle("Agenda")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuova = true } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Nuova visita")
            }
        }
    }
}

struct VisitaDettaglioView: View {
    @State var visita: RigaVisita
    @Environment(AppModel.self) private var app
    @State private var modifica = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Overline(visita.inizio.giorno.esteso).padding(.bottom, 10)
                Text(visita.inizio.ora)
                    .font(Tipografia.display(72))
                    .foregroundStyle(Tema.ink)
                NavigationLink { SchedaPazienteView(id: visita.pazienteId) } label: {
                    Text(visita.pazienteNome).font(Tipografia.title).foregroundStyle(Tema.ink)
                }
                .padding(.bottom, 12)
                ChipStato(testo: visita.stato.etichetta, colore: visita.stato.colore).padding(.bottom, 20)
                if let meet = visita.meetUrl.flatMap(URL.init(string:)) {
                    Link("Apri Meet", destination: meet).buttonStyle(BottoneEmber()).padding(.bottom, 12)
                }
                if visita.stato == .programmata {
                    Button("Segna come effettuata") { Task { await cambia(.effettuata) } }
                        .buttonStyle(BottoneEmber())
                        .padding(.bottom, 12)
                }
                RigaDato(etichetta: "Tipo", valore: visita.tipo.etichetta)
                RigaDato(etichetta: "Modalità", valore: visita.modalita.etichetta)
                RigaDato(etichetta: "Fine", valore: visita.fine.ora)
                RigaDato(etichetta: "Percorso", valore: visita.percorsoTipo?.etichetta)
                RigaDato(etichetta: "Numero", valore: visita.numeroVisita.map { "\($0) di \(visita.visiteTotali ?? 0)" })
                RigaDato(etichetta: "Calendario", valore: visita.googleSyncStato?.etichetta)
                RigaDato(etichetta: "Errore Google", valore: visita.googleSyncErrore)
                RigaDato(etichetta: "Note", valore: visita.note)
            }
            .padding(Tema.margine)
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Modifica", systemImage: "pencil") { modifica = true }
                    ForEach(StatoVisita.scelte.filter { $0 != visita.stato }, id: \.self) { stato in
                        Button(stato.etichetta) { Task { await cambia(stato) } }
                    }
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel("Azioni")
            }
        }
        .sheet(isPresented: $modifica, onDismiss: { Task { await ricarica() } }) {
            VisitaFormView(visita: visita, pazienteId: visita.pazienteId)
        }
    }

    private func cambia(_ stato: StatoVisita) async {
        await app.esegui({
            visita = try await app.api.post("/visite/\(visita.id)/stato", CambiaStatoVisita(stato: stato))
        }, successo: "Visita aggiornata")
    }

    private func ricarica() async {
        let elenco: [RigaVisita]? = try? await app.api.get("/pazienti/\(visita.pazienteId)/visite")
        if let aggiornata = elenco?.first(where: { $0.id == visita.id }) { visita = aggiornata }
    }
}

struct VisitaFormView: View {
    let visita: RigaVisita?
    @State var pazienteId: Int?
    @Environment(AppModel.self) private var app
    @State private var tipo = TipoVisita.controllo
    @State private var modalita = ModalitaVisita.online
    @State private var inizio = Date().addingTimeInterval(86400)
    @State private var durata = 30
    @State private var conMeet = true
    @State private var invita = true
    @State private var note = ""

    var body: some View {
        ModuloFoglio(titolo: visita == nil ? "Nuova visita" : "Modifica visita", valido: pazienteId != nil) {
            guard let pazienteId else { return }
            let corpo = SalvaVisita(pazienteId: pazienteId, tipo: tipo, modalita: modalita, inizio: OraLocale(inizio),
                                    durataMinuti: durata, conMeet: conMeet, invitaPaziente: invita, note: note.nonVuoto)
            if let visita {
                let _: RigaVisita = try await app.api.put("/visite/\(visita.id)", corpo)
            } else {
                let _: RigaVisita = try await app.api.post("/visite", corpo)
            }
            app.mostra("Visita salvata")
        } contenuto: {
            if visita == nil { Section { SceltaPaziente(selezione: $pazienteId) } }
            Section {
                Picker("Tipo", selection: $tipo) {
                    ForEach(TipoVisita.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                .onChange(of: tipo) { _, nuovo in durata = nuovo.minuti }
                Picker("Modalità", selection: $modalita) {
                    ForEach(ModalitaVisita.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                DatePicker("Data e ora", selection: $inizio)
                    .environment(\.timeZone, Roma.fuso)
                    .environment(\.locale, Roma.locale)
                Stepper("Durata: \(durata) minuti", value: $durata, in: 15...120, step: 15)
            }
            Section {
                Toggle("Link Meet", isOn: $conMeet)
                Toggle("Invita il paziente", isOn: $invita)
                TextField("Note", text: $note, axis: .vertical)
            }
        }
        .onAppear {
            guard let visita else { return }
            tipo = visita.tipo
            modalita = visita.modalita
            inizio = visita.inizio.data
            durata = Int(visita.fine.data.timeIntervalSince(visita.inizio.data) / 60)
            conMeet = visita.conMeet
            invita = visita.invitaPaziente
            note = visita.note.testo
        }
    }
}

// MARK: Check della settimana

struct CheckSettimanaView: View {
    @Environment(AppModel.self) private var app
    @State private var giorno = Giorno.oggi
    @State private var filtro = Filtro.tutti

    private enum Filtro: String, CaseIterable {
        case tutti = "Tutti", segnali = "Segnali", mancanti = "Mancanti", risposta = "Da rispondere"
    }

    var body: some View {
        Caricamento(chiave: giorno) {
            let dati: SettimanaCheck = try await app.api.get("/check/settimana", [URLQueryItem(name: "data", value: giorno.testo)])
            return dati
        } contenuto: { dati, aggiorna in
            List {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Button { giorno = dati.settimana.aggiungendo(giorni: -7) } label: {
                            Image(systemName: "chevron.left").frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Settimana precedente")
                        Spacer()
                        Overline("Lunedì \(dati.settimana.breve)")
                        Spacer()
                        Button { giorno = dati.settimana.aggiungendo(giorni: 7) } label: {
                            Image(systemName: "chevron.right").frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Settimana successiva")
                    }
                    .foregroundStyle(Tema.ember)
                    HStack(alignment: .top) {
                        NumeroEroe(valore: "\(dati.compilati)/\(dati.attesi)", didascalia: "Compilati", dimensione: 40)
                        NumeroEroe(valore: "\(dati.conSegnali)", didascalia: "Con segnali", dimensione: 40,
                                   colore: dati.conSegnali > 0 ? Tema.ember : Tema.ink)
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Filtro.allCases, id: \.self) { voce in
                                Button {
                                    Tocco.leggero()
                                    withAnimation(.edo) { filtro = voce }
                                } label: {
                                    Text(voce.rawValue)
                                        .font(Tipografia.label)
                                        .foregroundStyle(filtro == voce ? Tema.suEmber : Tema.ink)
                                        .padding(.horizontal, 14)
                                        .frame(minHeight: 38)
                                        .background(filtro == voce ? AnyShapeStyle(Tema.ember) : AnyShapeStyle(Tema.hairline), in: Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .rigaEdo()
                .listRowSeparator(.hidden)
                let righe = dati.righe.filter(passa)
                if righe.isEmpty {
                    StatoVuoto(titolo: "Nessun paziente qui", icona: "checkmark.circle").rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(righe) { riga in
                    NavigationLink {
                        RigaCheckDottoreView(riga: riga, settimana: dati.settimana)
                    } label: {
                        rigaPaziente(riga)
                    }
                    .rigaEdo()
                    .accessibilityIdentifier("check.riga")
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
        }
        .telaEdo()
        .navigationTitle("Check")
    }

    private func passa(_ riga: RigaSettimana) -> Bool {
        switch filtro {
        case .tutti: true
        case .segnali: riga.check?.haSegnali == true
        case .mancanti: riga.check == nil
        case .risposta: riga.check != nil && riga.check?.haFeedback == false
        }
    }

    private func rigaPaziente(_ riga: RigaSettimana) -> some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 3) {
                Text(riga.pazienteNome).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                Text(riga.azione.etichetta).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
            }
            Spacer()
            if riga.check?.feedbackAudioId != nil {
                Image(systemName: "waveform").foregroundStyle(Tema.positivo).accessibilityLabel("Vocale inviato")
            } else if riga.check?.haFeedback == true {
                Image(systemName: "checkmark").foregroundStyle(Tema.positivo).accessibilityLabel("Feedback inviato")
            }
            if riga.check?.haSegnali == true {
                Ellipse().fill(Tema.ember).frame(width: 11, height: 8).accessibilityLabel("Con segnali")
            } else if riga.check == nil {
                Ellipse().strokeBorder(Tema.inkTerziario, lineWidth: 1.5).frame(width: 11, height: 8).accessibilityLabel("Non compilato")
            }
        }
    }
}

struct SegnaliView: View {
    let check: CheckDettaglio

    var body: some View {
        HStack(spacing: 6) {
            if check.pesoFermo { ChipStato(testo: "Peso fermo", colore: Tema.attenzione) }
            if check.percezioneNegativa { ChipStato(testo: "Nessun miglioramento", colore: Tema.critico) }
            if check.energiaBassa { ChipStato(testo: "Energia bassa", colore: Tema.attenzione) }
            if !check.haSegnali { ChipStato(testo: "Nessun segnale", colore: Tema.positivo) }
        }
    }
}

/// Riga del lunedì: i contatti di lunedì e mercoledì, poi il check se c'è.
struct RigaCheckDottoreView: View {
    let riga: RigaSettimana
    let settimana: Giorno
    @Environment(AppModel.self) private var app
    @State private var lunedi = false
    @State private var mercoledi = false
    @State private var inserisci = false

    var body: some View {
        Group {
            if let check = riga.check {
                CheckDottoreView(check: check) { contatti }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        Text(riga.pazienteNome).font(Tipografia.display(38)).foregroundStyle(Tema.ink)
                        Text("Non ha ancora compilato il check di questa settimana.")
                            .font(Tipografia.body)
                            .foregroundStyle(Tema.inkSecondario)
                        NavigationLink {
                            ChatView(canale: .dottore(pazienteId: riga.pazienteId), titolo: riga.pazienteNome)
                        } label: {
                            Text("Scrivi un sollecito")
                        }
                        .buttonStyle(BottoneEmber())
                        Button("Inserisci le risposte") { inserisci = true }
                            .buttonStyle(BottoneVetro())
                        contatti
                    }
                    .padding(Tema.margine)
                }
                .telaEdo()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            lunedi = riga.lunediFattoIl != nil
            mercoledi = riga.mercolediFattoIl != nil
        }
        .sheet(isPresented: $inserisci) {
            InserisciCheckView(pazienteId: riga.pazienteId, settimana: settimana)
        }
    }

    private var contatti: some View {
        VStack(spacing: 0) {
            Toggle("Contatto del lunedì", isOn: Binding(get: { lunedi }, set: { valore in
                lunedi = valore
                Task { await segna("LUNEDI", valore) }
            }))
            .padding(.vertical, 8)
            Filetto()
            Toggle("Contatto del mercoledì", isOn: Binding(get: { mercoledi }, set: { valore in
                mercoledi = valore
                Task { await segna("MERCOLEDI", valore) }
            }))
            .padding(.vertical, 8)
        }
        .font(Tipografia.body)
        .foregroundStyle(Tema.ink)
    }

    private func segna(_ giorno: String, _ fatto: Bool) async {
        await app.esegui {
            let _: Vuoto = try await app.api.post("/check/contatto", ContattoCheck(
                pazienteId: riga.pazienteId, settimana: settimana, giorno: giorno, fatto: fatto))
        }
    }
}

/// Check dettato a voce o ricevuto per messaggio.
struct InserisciCheckView: View {
    let pazienteId: Int
    let settimana: Giorno
    @Environment(AppModel.self) private var app
    @State private var pasti = 14
    @State private var energia = 3
    @State private var impegno = 3
    @State private var peso = ""
    @State private var liberi = ""
    @State private var allenamento = ""
    @State private var andamento = ""
    @State private var migliorata = true
    @State private var percezione = ""

    var body: some View {
        ModuloFoglio(titolo: "Inserisci check", valido: peso.isEmpty || Decimal(italiano: peso) != nil) {
            let risposte = Risposte(pastiOttimali: pasti, pastiLiberi: liberi.nonVuoto, energia: energia, allenamento: allenamento.nonVuoto,
                                    pesoKg: Decimal(italiano: peso), andamentoPeso: andamento.nonVuoto, percezione: percezione.nonVuoto,
                                    percezioneMigliorata: migliorata, impegno: impegno)
            let _: CheckDettaglio = try await app.api.risposta("POST", "/check/paziente/\(pazienteId)",
                query: [URLQueryItem(name: "data", value: settimana.testo)], corpo: JSONEncoder().encode(risposte)).valore
            app.mostra("Check salvato")
        } contenuto: {
            Section {
                Stepper("Pasti ottimali: \(pasti)", value: $pasti, in: 0...21)
                Stepper("Energia: \(energia) su 5", value: $energia, in: 1...5)
                Stepper("Impegno: \(impegno) su 5", value: $impegno, in: 1...5)
                TextField("Peso (kg)", text: $peso).keyboardType(.decimalPad)
                Toggle("Nota miglioramenti", isOn: $migliorata)
            }
            Section {
                TextField("Pasti liberi", text: $liberi, axis: .vertical)
                TextField("Allenamento", text: $allenamento, axis: .vertical)
                TextField("Andamento del peso", text: $andamento, axis: .vertical)
                TextField("Percezione", text: $percezione, axis: .vertical)
            }
        }
    }
}

struct CheckDottoreView<Extra: View>: View {
    @State var check: CheckDettaglio
    @ViewBuilder var extra: () -> Extra
    @Environment(AppModel.self) private var app
    @State private var rispondi = false

    init(check: CheckDettaglio, @ViewBuilder extra: @escaping () -> Extra) {
        _check = State(initialValue: check)
        self.extra = extra
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Overline("Settimana del \(check.settimana.breve)").padding(.bottom, 8)
                Text(check.pazienteNome)
                    .font(Tipografia.display(38))
                    .foregroundStyle(Tema.ink)
                    .padding(.bottom, 12)
                ScrollView(.horizontal, showsIndicators: false) { SegnaliView(check: check) }
                    .padding(.bottom, 24)

                if check.haFeedback {
                    VStack(alignment: .leading, spacing: 12) {
                        Overline("Il tuo feedback")
                        if check.feedbackAudioId != nil {
                            LettoreVocale(percorso: "/check/\(check.id)/feedback/audio")
                        }
                        if let testo = check.feedback, !testo.isEmpty {
                            Text(testo).font(Tipografia.body).foregroundStyle(Tema.ink)
                        }
                        Text(statoFeedback).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                    }
                    .padding(20)
                    .vetro()
                    .padding(.bottom, 16)
                }
                Button(check.haFeedback ? "Sostituisci il feedback" : "Rispondi") { rispondi = true }
                    .buttonStyle(check.haFeedback ? AnyButtonStyle(BottoneVetro()) : AnyButtonStyle(BottoneEmber()))
                    .padding(.bottom, 26)
                    .accessibilityIdentifier("check.rispondi")

                RigaDato(etichetta: "Pasti ottimali", valore: check.pastiOttimali.map { "\($0) su 21" })
                RigaDato(etichetta: "Pasti liberi", valore: check.pastiLiberi)
                RigaDato(etichetta: "Energia", valore: check.energia.map { "\($0) su 5" })
                RigaDato(etichetta: "Allenamento", valore: check.allenamento)
                RigaDato(etichetta: "Peso", valore: check.pesoKg.map { Formati.peso($0) })
                RigaDato(etichetta: "Andamento del peso", valore: check.andamentoPeso)
                RigaDato(etichetta: "Miglioramenti", valore: check.percezioneMigliorata.map { $0 ? "Sì" : "No" })
                RigaDato(etichetta: "Percezione", valore: check.percezione)
                RigaDato(etichetta: "Impegno", valore: check.impegno.map { "\($0) su 5" })
                RigaDato(etichetta: "Compilato", valore: check.compilatoIl?.giornoOra)
                RigaDato(etichetta: "Fonte", valore: check.fonte?.etichetta)
                extra().padding(.top, 16)
            }
            .padding(Tema.margine)
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $rispondi) {
            FeedbackComposerView(check: check) { check = $0 }
        }
        .task {
            guard check.lettoIl == nil else { return }
            await app.esegui { let _: Vuoto = try await app.api.post("/check/\(check.id)/letto") }
        }
    }

    private var statoFeedback: String {
        var parti: [String] = []
        if let il = check.feedbackIl { parti.append("Inviato \(il.giornoOra)") }
        parti.append(check.feedbackLettoIl != nil ? "letto" : "non ancora letto")
        if check.feedbackEmailIl != nil { parti.append("anche via email") }
        return parti.joined(separator: " · ")
    }
}

extension CheckDottoreView where Extra == EmptyView {
    init(check: CheckDettaglio) {
        self.init(check: check) { EmptyView() }
    }
}

struct AnyButtonStyle: ButtonStyle {
    private let crea: (Configuration) -> AnyView

    init<S: ButtonStyle>(_ stile: S) {
        crea = { AnyView(stile.makeBody(configuration: $0)) }
    }

    func makeBody(configuration: Configuration) -> some View { crea(configuration) }
}

// MARK: Feedback del dottore

@MainActor
@Observable
final class RegistratoreModel: NSObject {
    enum Fase: Equatable { case pronto, registra, fatto, negato, errore(String) }

    private(set) var fase = Fase.pronto
    private(set) var secondi: Double = 0
    private(set) var livelli: [Float] = []
    private(set) var file: URL?
    @ObservationIgnored private var recorder: AVAudioRecorder?
    @ObservationIgnored private var misura: Task<Void, Never>?

    static let massimoSecondi: Double = 600

    func avvia() async {
        let concesso = await AVAudioApplication.requestRecordPermission()
        guard concesso else {
            fase = .negato
            return
        }
        do {
            let sessione = AVAudioSession.sharedInstance()
            try sessione.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker])
            try sessione.setActive(true)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("feedback-\(UUID().uuidString).m4a")
            // 32 kbps: dieci minuti di voce restano sotto i 5 MB accettati dal server
            let recorder = try AVAudioRecorder(url: url, settings: [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVSampleRateKey: 22_050,
                AVNumberOfChannelsKey: 1,
                AVEncoderBitRateKey: 32_000,
            ])
            recorder.isMeteringEnabled = true
            guard recorder.record() else {
                fase = .errore("Il microfono non è disponibile.")
                return
            }
            self.recorder = recorder
            file = url
            livelli = []
            secondi = 0
            fase = .registra
            Tocco.impatto()
            misura = Task { [weak self] in
                while !Task.isCancelled {
                    self?.campiona()
                    try? await Task.sleep(for: .milliseconds(90))
                }
            }
        } catch {
            fase = .errore("Il microfono non è disponibile.")
        }
    }

    private func campiona() {
        guard let recorder else { return }
        recorder.updateMeters()
        let decibel = recorder.averagePower(forChannel: 0)
        livelli.append(max(0.05, pow(10, decibel / 28)))
        if livelli.count > 56 { livelli.removeFirst() }
        secondi = recorder.currentTime
        if secondi >= Self.massimoSecondi { ferma() }
    }

    func ferma() {
        secondi = recorder?.currentTime ?? secondi
        recorder?.stop()
        recorder = nil
        misura?.cancel()
        fase = .fatto
        Tocco.conferma()
    }

    func scarta() {
        recorder?.stop()
        recorder = nil
        misura?.cancel()
        if let file { try? FileManager.default.removeItem(at: file) }
        file = nil
        secondi = 0
        livelli = []
        fase = .pronto
    }
}

struct FeedbackComposerView: View {
    let check: CheckDettaglio
    let salvato: (CheckDettaglio) -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var canale = Canale.vocale
    @State private var testo = ""
    @State private var email = false
    @State private var registratore = RegistratoreModel()
    @State private var inCorso = false

    private enum Canale: String, CaseIterable { case vocale = "Vocale", testo = "Messaggio" }

    private var pronto: Bool {
        canale == .testo ? testo.nonVuoto != nil : registratore.fase == .fatto
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text(check.pazienteNome)
                        .font(Tipografia.display(34))
                        .foregroundStyle(Tema.ink)
                    Picker("Canale", selection: $canale) {
                        ForEach(Canale.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    if canale == .vocale { registrazione }
                    TextField(canale == .vocale ? "Aggiungi un testo, se vuoi" : "Scrivi il feedback", text: $testo, axis: .vertical)
                        .font(Tipografia.body)
                        .lineLimit(canale == .vocale ? 2...4 : 6...14)
                        .padding(16)
                        .vetro(raggio: Tema.raggioControllo, interattivo: true)
                        .accessibilityIdentifier("feedback.testo")
                    Toggle("Invia anche via email", isOn: $email)
                        .font(Tipografia.body)
                        .foregroundStyle(Tema.ink)
                    if canale == .vocale && email {
                        Text("L'email avvisa il paziente che c'è un vocale nell'app: l'audio non viene allegato.")
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.inkSecondario)
                    }
                    Button {
                        Task { await invia() }
                    } label: {
                        if inCorso { ProgressView().tint(Tema.suEmber) } else { Text("Invia") }
                    }
                    .buttonStyle(BottoneEmber())
                    .disabled(!pronto || inCorso)
                    .accessibilityIdentifier("feedback.invia")
                }
                .padding(Tema.margine)
            }
            .telaEdo(fuoco: .init(x: 0.5, y: 0.2), intensita: 0.7, animata: registratore.fase == .registra)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annulla") {
                        registratore.scarta()
                        chiudi()
                    }
                }
            }
        }
        .interactiveDismissDisabled(registratore.fase == .registra)
    }

    @ViewBuilder
    private var registrazione: some View {
        VStack(spacing: 18) {
            switch registratore.fase {
            case .fatto:
                if let file = registratore.file {
                    LettoreVocale(locale: file)
                    Button("Registra di nuovo") { registratore.scarta() }
                        .font(Tipografia.label)
                        .frame(minHeight: 44)
                }
            case .negato:
                StatoVuoto(titolo: "Microfono negato", testo: "Attivalo nelle Impostazioni di iOS per registrare un vocale.", icona: "mic.slash")
            default:
                GeometryReader { spazio in
                    HStack(alignment: .center, spacing: 3) {
                        ForEach(Array(registratore.livelli.enumerated()), id: \.offset) { _, livello in
                            Capsule()
                                .fill(Tema.ember)
                                .frame(width: 3, height: max(4, CGFloat(livello) * spazio.size.height))
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                }
                .frame(height: 84)
                .accessibilityHidden(true)
                Text(Formati.cronometro(registratore.secondi))
                    .font(Tipografia.display(40))
                    .monospacedDigit()
                    .foregroundStyle(Tema.ink)
                Button {
                    Task {
                        if registratore.fase == .registra { registratore.ferma() } else { await registratore.avvia() }
                    }
                } label: {
                    Image(systemName: registratore.fase == .registra ? "stop.fill" : "mic.fill")
                        .font(.system(size: 26))
                        .foregroundStyle(Tema.suEmber)
                        .frame(width: 84, height: 84)
                        .background(Tema.ember, in: Circle())
                }
                .accessibilityLabel(registratore.fase == .registra ? "Ferma la registrazione" : "Registra")
                .accessibilityIdentifier("feedback.registra")
                if case .errore(let messaggio) = registratore.fase {
                    Text(messaggio).font(Tipografia.nota).foregroundStyle(Tema.critico)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func invia() async {
        inCorso = true
        defer { inCorso = false }
        await app.esegui {
            let risposta: Risposta<CheckDettaglio>
            if canale == .testo {
                struct Corpo: Encodable { let testo: String; let inviaEmail: Bool }
                risposta = try await app.api.invia("PUT", "/check/\(check.id)/feedback", Corpo(testo: testo, inviaEmail: email))
            } else {
                guard let file = registratore.file else { return }
                let dati = try Data(contentsOf: file)
                guard dati.count <= 5 * 1024 * 1024 else {
                    throw ErroreAPI.server(stato: 413, messaggio: "Il vocale supera i 5 MB. Registrane uno più breve.")
                }
                var campi = ["secondi": "\(max(1, Int(registratore.secondi.rounded())))", "inviaEmail": email ? "true" : "false"]
                if let extra = testo.nonVuoto { campi["testo"] = extra }
                risposta = try await app.api.multipart("/check/\(check.id)/feedback/audio", campi: campi,
                    file: ParteFile(campo: "file", nomeFile: "feedback.m4a", tipo: "audio/mp4", dati: dati))
                try? FileManager.default.removeItem(at: file)
            }
            salvato(risposta.valore)
            app.mostra(risposta.messaggio ?? "Feedback inviato")
            Tocco.conferma()
            chiudi()
        }
    }
}
