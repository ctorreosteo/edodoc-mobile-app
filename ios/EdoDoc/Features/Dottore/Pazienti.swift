import SwiftUI

struct PazientiView: View {
    @Environment(AppModel.self) private var app
    @State private var cerca = ""
    @State private var nuovo = false

    var body: some View {
        Caricamento {
            let elenco: [RigaPaziente] = try await app.api.get("/pazienti")
            return elenco
        } contenuto: { elenco, aggiorna in
            let filtrati = cerca.isEmpty ? elenco : elenco.filter {
                $0.nomeCompleto.localizedCaseInsensitiveContains(cerca) || $0.email.testo.localizedCaseInsensitiveContains(cerca)
            }
            Group {
                if filtrati.isEmpty {
                    StatoVuoto(titolo: cerca.isEmpty ? "Nessun paziente" : "Nessun risultato", icona: "person.2")
                } else {
                    List(filtrati) { paziente in
                        NavigationLink { SchedaPazienteView(id: paziente.id) } label: {
                            HStack(spacing: 14) {
                                Avatar(nome: paziente.nomeCompleto)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(paziente.nomeCompleto).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                    Text(sottotitolo(paziente)).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                                }
                                Spacer()
                                if paziente.situazionePagamenti == "SCADUTO" {
                                    Ellipse().fill(Tema.critico).frame(width: 11, height: 8)
                                        .accessibilityLabel("Pagamento scaduto")
                                }
                            }
                        }
                        .rigaEdo()
                    }
                    .listaEdo()
                    .refreshable { await aggiorna() }
                }
            }
            .sheet(isPresented: $nuovo, onDismiss: { Task { await aggiorna() } }) {
                PazienteFormView(paziente: nil)
            }
        }
        .telaEdo()
        .navigationTitle("Pazienti")
        .searchable(text: $cerca, prompt: "Cerca")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuovo = true } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Nuovo paziente")
            }
        }
    }

    private func sottotitolo(_ p: RigaPaziente) -> String {
        var parti = [p.stato.etichetta]
        if let visita = p.prossimaVisita { parti.append("visita \(visita.giorno.breve)") }
        return parti.joined(separator: " · ")
    }
}

struct SchedaPazienteView: View {
    let id: Int
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var sezione = Sezione.scheda
    @State private var modifica = false
    @State private var nuovaVisita = false
    @State private var nuovoPercorso = false
    @State private var nuovoPagamento = false
    @State private var email: RichiestaEmail?
    @State private var invito: InvitoApp?
    @State private var archivia = false
    @State private var versione = 0

    private enum Sezione: String, CaseIterable {
        case scheda = "Scheda", visite = "Visite", check = "Check", soldi = "Pagamenti", fatture = "Fatture", task = "Task"
    }

    var body: some View {
        Caricamento(chiave: "\(id)-\(versione)") {
            let paziente: RigaPaziente = try await app.api.get("/pazienti/\(id)")
            return paziente
        } contenuto: { paziente, _ in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    testata(paziente)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Sezione.allCases, id: \.self) { voce in
                                Button {
                                    Tocco.leggero()
                                    withAnimation(.edo) { sezione = voce }
                                } label: {
                                    Text(voce.rawValue)
                                        .font(Tipografia.label)
                                        .foregroundStyle(sezione == voce ? Tema.suEmber : Tema.ink)
                                        .padding(.horizontal, 15)
                                        .frame(minHeight: 40)
                                        .background(sezione == voce ? AnyShapeStyle(Tema.ember) : AnyShapeStyle(Tema.hairline), in: Capsule())
                                }
                                .accessibilityAddTraits(sezione == voce ? .isSelected : [])
                                .accessibilityIdentifier("sezione.\(voce.rawValue)")
                            }
                        }
                        .padding(.horizontal, Tema.margine)
                    }
                    .padding(.vertical, 18)
                    Group {
                        switch sezione {
                        case .scheda: scheda(paziente)
                        case .visite: visite
                        case .check: storicoCheck
                        case .soldi: pagamenti
                        case .fatture: fatture
                        case .task: compiti
                        }
                    }
                    .id("\(sezione.rawValue)-\(versione)")
                    .padding(.horizontal, Tema.margine)
                    .padding(.bottom, 30)
                }
            }
            .sheet(isPresented: $modifica, onDismiss: ricarica) { PazienteFormView(paziente: paziente) }
            .sheet(isPresented: $nuovaVisita, onDismiss: ricarica) { VisitaFormView(visita: nil, pazienteId: id) }
            .sheet(isPresented: $nuovoPercorso, onDismiss: ricarica) { PercorsoFormView(pazienteId: id) }
            .sheet(isPresented: $nuovoPagamento, onDismiss: ricarica) { PagamentoFormView(pagamento: nil, pazienteId: id) }
            .sheet(item: $email) { EmailView(richiesta: $0) }
            .sheet(item: Binding(get: { invito.map { Foglio(invito: $0) } }, set: { _ in invito = nil })) { InvitoView(invito: $0.invito) }
        }
        .telaEdo(fuoco: .init(x: 0.85, y: 0.03), intensita: 0.6)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Modifica", systemImage: "pencil") { modifica = true }
                    Button("Nuova visita", systemImage: "calendar.badge.plus") { nuovaVisita = true }
                    Button("Nuovo percorso", systemImage: "bag.badge.plus") { nuovoPercorso = true }
                    Button("Nuovo pagamento", systemImage: "creditcard") { nuovoPagamento = true }
                    Button("Chiedi i dati di fatturazione", systemImage: "envelope") {
                        email = RichiestaEmail(tipo: .dati, pazienteId: id)
                    }
                    Button("Invia l'accesso all'app", systemImage: "iphone") {
                        Task {
                            await app.esegui { invito = try await app.api.post("/pazienti/\(id)/invito-app") }
                        }
                    }
                    Button("Archivia", systemImage: "archivebox", role: .destructive) { archivia = true }
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel("Azioni")
            }
        }
        .confirmationDialog("Archiviare il paziente?", isPresented: $archivia, titleVisibility: .visible) {
            Button("Archivia", role: .destructive) {
                Task {
                    let riuscito = await app.esegui({ try await app.api.delete("/pazienti/\(id)") }, successo: "Paziente archiviato")
                    if riuscito { chiudi() }
                }
            }
        }
    }

    private struct Foglio: Identifiable {
        let invito: InvitoApp
        var id: String { invito.link }
    }

    private func ricarica() { versione += 1 }

    private func testata(_ p: RigaPaziente) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(p.nomeCompleto)
                .font(Tipografia.display(38))
                .foregroundStyle(Tema.ink)
            HStack(spacing: 8) {
                ChipStato(testo: p.stato.etichetta, colore: p.stato.colore)
                if p.accessoApp { ChipStato(testo: "App attiva", colore: Tema.positivo) }
                if p.checkSettimana { ChipStato(testo: "Check fatto", colore: Tema.positivo) }
            }
            NavigationLink {
                ChatView(canale: .dottore(pazienteId: p.id), titolo: p.nomeCompleto)
            } label: {
                Label("Scrivi", systemImage: "bubble.left")
            }
            .buttonStyle(BottoneVetro(larghezzaPiena: false))
            .padding(.top, 6)
        }
        .padding(.horizontal, Tema.margine)
    }

    private func scheda(_ p: RigaPaziente) -> some View {
        VStack(spacing: 0) {
            RigaDato(etichetta: "Percorso", valore: p.percorso.map { "\($0.tipo.etichetta), \($0.visiteEffettuate) di \($0.visiteTotali)" })
            RigaDato(etichetta: "Prossima visita", valore: p.prossimaVisita?.giornoOra)
            RigaDato(etichetta: "Residuo", valore: p.residuo.flatMap { $0 > 0 ? Formati.euro($0) : nil })
            RigaDato(etichetta: "Email", valore: p.email)
            RigaDato(etichetta: "Cellulare", valore: p.cellulare)
            RigaDato(etichetta: "Codice fiscale", valore: p.codiceFiscale)
            RigaDato(etichetta: "Data di nascita", valore: p.dataNascita?.breve)
            RigaDato(etichetta: "Indirizzo", valore: [p.indirizzo, p.cap, p.citta, p.provincia].compactMap { $0?.nonVuoto }.joined(separator: " "))
            RigaDato(etichetta: "Fonte", valore: p.fonte?.etichetta)
            RigaDato(etichetta: "Peso iniziale", valore: p.pesoInizialeKg.map { Formati.peso($0) })
            RigaDato(etichetta: "Peso obiettivo", valore: p.pesoObiettivoKg.map { Formati.peso($0) })
            RigaDato(etichetta: "Situazione", valore: p.situazione)
            RigaDato(etichetta: "Nota", valore: p.nota)
        }
    }

    private var visite: some View {
        Caricamento {
            let elenco: [RigaVisita] = try await app.api.get("/pazienti/\(id)/visite")
            return elenco
        } contenuto: { elenco, _ in
            LazyVStack(spacing: 0) {
                if elenco.isEmpty { StatoVuoto(titolo: "Nessuna visita", icona: "calendar") }
                ForEach(elenco) { visita in
                    NavigationLink { VisitaDettaglioView(visita: visita) } label: {
                        RigaVisitaView(visita: visita)
                    }.buttonStyle(Premibile())
                    Filetto()
                }
            }
        }
    }

    private var storicoCheck: some View {
        Caricamento {
            let elenco: [CheckDettaglio] = try await app.api.get("/pazienti/\(id)/check")
            return elenco
        } contenuto: { elenco, _ in
            LazyVStack(spacing: 0) {
                if elenco.isEmpty { StatoVuoto(titolo: "Nessun check", icona: "checklist") }
                ForEach(elenco) { check in
                    NavigationLink { CheckDottoreView(check: check) } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Settimana del \(check.settimana.breve)")
                                    .font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                Text(check.haFeedback ? "Feedback inviato" : "Da rispondere")
                                    .font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                            }
                            Spacer()
                            if check.haSegnali { Ellipse().fill(Tema.ember).frame(width: 11, height: 8) }
                        }
                        .padding(.vertical, 12)
                    }.buttonStyle(Premibile())
                    Filetto()
                }
            }
        }
    }

    private var pagamenti: some View {
        Caricamento {
            let lista: ListaPagamenti = try await app.api.get("/pazienti/\(id)/pagamenti")
            return lista
        } contenuto: { lista, aggiorna in
            LazyVStack(spacing: 0) {
                HStack {
                    NumeroEroe(valore: Formati.euroIntero(lista.riepilogo.pagato), didascalia: "Pagato", dimensione: 28)
                    NumeroEroe(valore: Formati.euroIntero(lista.riepilogo.daPagare), didascalia: "Da pagare", dimensione: 28)
                }
                .padding(.bottom, 12)
                ForEach(lista.righe) { riga in
                    RigaPagamentoView(riga: riga, mostraPaziente: false, aggiorna: aggiorna)
                    Filetto()
                }
            }
        }
    }

    private var fatture: some View {
        Caricamento {
            let elenco: [RigaFattura] = try await app.api.get("/pazienti/\(id)/fatture")
            return elenco
        } contenuto: { elenco, aggiorna in
            LazyVStack(spacing: 0) {
                if elenco.isEmpty { StatoVuoto(titolo: "Nessuna fattura", icona: "doc.text") }
                ForEach(elenco) { fattura in
                    NavigationLink { FatturaDettaglioView(fattura: fattura) } label: {
                        RigaFatturaView(fattura: fattura)
                    }.buttonStyle(Premibile())
                    Filetto()
                }
            }
        }
    }

    private var compiti: some View {
        Caricamento {
            let elenco: [RigaTask] = try await app.api.get("/pazienti/\(id)/task")
            return elenco
        } contenuto: { elenco, aggiorna in
            LazyVStack(spacing: 0) {
                if elenco.isEmpty { StatoVuoto(titolo: "Nessun task", icona: "checkmark.circle") }
                ForEach(elenco) { task in
                    RigaTaskView(task: task, aggiorna: aggiorna)
                    Filetto()
                }
            }
        }
    }
}

struct InvitoView: View {
    let invito: InvitoApp
    @Environment(\.dismiss) private var chiudi

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 18) {
                Text("Accesso pronto.")
                    .font(Tipografia.display(38))
                    .foregroundStyle(Tema.ink)
                Text(invito.emailInviata
                     ? "Il paziente ha ricevuto l'email. Se il link non si apre, può incollarlo nella schermata di primo accesso."
                     : "L'email è spenta: manda tu questo link al paziente. Va incollato nella schermata di primo accesso dell'app.")
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.inkSecondario)
                Text(invito.link)
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.ink)
                    .textSelection(.enabled)
                    .padding(16)
                    .vetro(raggio: Tema.raggioControllo)
                ShareLink(item: invito.link) {
                    Text("Condividi il link")
                }
                .buttonStyle(BottoneEmber())
                Spacer()
            }
            .padding(Tema.margine)
            .telaEdo()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Chiudi") { chiudi() } }
            }
        }
    }
}

struct PazienteFormView: View {
    let paziente: RigaPaziente?
    @Environment(AppModel.self) private var app
    @State private var v = SalvaPaziente(nome: "", cognome: "", stato: .lead)
    @State private var pesoIniziale = ""
    @State private var pesoObiettivo = ""
    @State private var conNascita = false
    @State private var nascita = Giorno(anno: 1985, mese: 1, giorno: 1)

    private func campo(_ chiave: WritableKeyPath<SalvaPaziente, String?>) -> Binding<String> {
        Binding(get: { v[keyPath: chiave].testo }, set: { v[keyPath: chiave] = $0 })
    }

    private func vuotiANil(_ s: SalvaPaziente) -> SalvaPaziente {
        var r = s
        for chiave: WritableKeyPath<SalvaPaziente, String?> in [\.email, \.cellulare, \.codiceFiscale, \.indirizzo, \.cap, \.citta, \.provincia, \.situazione, \.nota] {
            r[keyPath: chiave] = r[keyPath: chiave]?.nonVuoto
        }
        return r
    }

    var body: some View {
        ModuloFoglio(titolo: paziente == nil ? "Nuovo paziente" : "Modifica paziente",
                     valido: v.nome.nonVuoto != nil && v.cognome.nonVuoto != nil) {
            var corpo = vuotiANil(v)
            corpo.pesoInizialeKg = Decimal(italiano: pesoIniziale)
            corpo.pesoObiettivoKg = Decimal(italiano: pesoObiettivo)
            corpo.dataNascita = conNascita ? nascita : nil
            if let paziente {
                let _: RigaPaziente = try await app.api.put("/pazienti/\(paziente.id)", corpo)
            } else {
                let _: RigaPaziente = try await app.api.post("/pazienti", corpo)
            }
            app.mostra("Paziente salvato")
        } contenuto: {
            Section {
                TextField("Nome", text: $v.nome)
                TextField("Cognome", text: $v.cognome)
                TextField("Email", text: campo(\.email))
                    .keyboardType(.emailAddress).textInputAutocapitalization(.never)
                TextField("Cellulare", text: campo(\.cellulare)).keyboardType(.phonePad)
            }
            Section("Stato") {
                Picker("Stato", selection: Binding(get: { v.stato ?? .lead }, set: { v.stato = $0 })) {
                    ForEach(StatoPaziente.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                Picker("Fonte", selection: $v.fonte) {
                    Text("Non indicata").tag(FontePaziente?.none)
                    ForEach(FontePaziente.scelte, id: \.self) { Text($0.etichetta).tag(FontePaziente?.some($0)) }
                }
            }
            Section("Dati per la fattura") {
                TextField("Codice fiscale", text: campo(\.codiceFiscale)).textInputAutocapitalization(.characters)
                TextField("Indirizzo", text: campo(\.indirizzo))
                TextField("CAP", text: campo(\.cap)).keyboardType(.numberPad)
                TextField("Città", text: campo(\.citta))
                TextField("Provincia", text: campo(\.provincia)).textInputAutocapitalization(.characters)
                Toggle("Data di nascita", isOn: $conNascita)
                if conNascita { CampoGiorno(etichetta: "Nata o nato il", giorno: $nascita) }
            }
            Section("Obiettivi") {
                TextField("Peso iniziale (kg)", text: $pesoIniziale).keyboardType(.decimalPad)
                TextField("Peso obiettivo (kg)", text: $pesoObiettivo).keyboardType(.decimalPad)
            }
            Section("Note") {
                TextField("Situazione", text: campo(\.situazione), axis: .vertical)
                TextField("Nota", text: campo(\.nota), axis: .vertical)
            }
        }
        .onAppear {
            guard let p = paziente else { return }
            v = SalvaPaziente(nome: p.nome, cognome: p.cognome, email: p.email, cellulare: p.cellulare, codiceFiscale: p.codiceFiscale,
                              dataNascita: p.dataNascita, indirizzo: p.indirizzo, cap: p.cap, citta: p.citta, provincia: p.provincia,
                              stato: p.stato, fonte: p.fonte, situazione: p.situazione, nota: p.nota,
                              pesoInizialeKg: p.pesoInizialeKg, pesoObiettivoKg: p.pesoObiettivoKg)
            pesoIniziale = p.pesoInizialeKg?.testoModulo ?? ""
            pesoObiettivo = p.pesoObiettivoKg?.testoModulo ?? ""
            if let n = p.dataNascita {
                conNascita = true
                nascita = n
            }
        }
    }
}
