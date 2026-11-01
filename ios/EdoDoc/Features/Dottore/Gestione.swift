import Charts
import SwiftUI

// MARK: Task

struct RigaTaskView: View {
    let task: RigaTask
    let aggiorna: () async -> Void
    @Environment(AppModel.self) private var app

    var body: some View {
        HStack(spacing: 14) {
            Button {
                Task {
                    await app.esegui {
                        let _: RigaTask = try await app.api.post("/task/\(task.id)/stato", [URLQueryItem(name: "fatto", value: task.stato == .fatto ? "false" : "true")])
                    }
                    Tocco.leggero()
                    await aggiorna()
                }
            } label: {
                Group {
                    if task.stato == .fatto {
                        Ellipse().fill(Tema.ember)
                    } else {
                        Ellipse().strokeBorder(Tema.inkTerziario, lineWidth: 1.5)
                    }
                }
                .frame(width: 28, height: 22)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(task.stato == .fatto ? "Segna da fare" : "Segna fatto")
            .accessibilityIdentifier("task.stato.\(task.titolo)")
            VStack(alignment: .leading, spacing: 3) {
                Text(task.titolo)
                    .font(Tipografia.bodyMedio)
                    .foregroundStyle(task.stato == .fatto ? Tema.inkTerziario : Tema.ink)
                    .strikethrough(task.stato == .fatto)
                Text([task.pazienteNome, task.scadenza?.breve].compactMap { $0 }.joined(separator: " · "))
                    .font(Tipografia.nota)
                    .foregroundStyle(task.inRitardo ? Tema.critico : Tema.inkSecondario)
            }
            Spacer(minLength: 0)
            if task.priorita == .alta && task.stato != .fatto {
                ChipStato(testo: "Alta", colore: Tema.critico)
            }
        }
    }
}

struct TaskView: View {
    @Environment(AppModel.self) private var app
    @State private var completati = false
    @State private var nuovo = false

    var body: some View {
        Caricamento(chiave: completati) {
            let elenco: [RigaTask] = try await app.api.get("/task", [URLQueryItem(name: "completati", value: completati ? "true" : "false")])
            return elenco
        } contenuto: { elenco, aggiorna in
            List {
                Toggle("Mostra i completati", isOn: $completati)
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.ink)
                    .rigaEdo()
                if elenco.isEmpty {
                    StatoVuoto(titolo: "Niente da fare", icona: "checkmark.circle").rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(elenco) { task in
                    RigaTaskView(task: task, aggiorna: aggiorna)
                        .rigaEdo()
                        .swipeActions {
                            Button("Elimina", role: .destructive) {
                                Task {
                                    await app.esegui({ try await app.api.delete("/task/\(task.id)") }, successo: "Task eliminato")
                                    await aggiorna()
                                }
                            }
                        }
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $nuovo, onDismiss: { Task { await aggiorna() } }) { TaskFormView() }
        }
        .telaEdo()
        .navigationTitle("Task")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuovo = true } label: { Image(systemName: "plus") }.accessibilityLabel("Nuovo task")
            }
        }
    }
}

struct TaskFormView: View {
    @Environment(AppModel.self) private var app
    @State private var tipi: [TipoTask] = []
    @State private var tipo: String?
    @State private var titolo = ""
    @State private var descrizione = ""
    @State private var conScadenza = false
    @State private var scadenza = Giorno.oggi
    @State private var pazienteId: Int?

    var body: some View {
        ModuloFoglio(titolo: "Nuovo task", valido: tipo != nil) {
            guard let tipo else { return }
            let _: RigaTask = try await app.api.post("/task", SalvaTask(
                tipoCodice: tipo, titolo: titolo.nonVuoto, descrizione: descrizione.nonVuoto,
                scadenza: conScadenza ? scadenza : nil, priorita: nil, pazienteId: pazienteId))
            app.mostra("Task creato")
        } contenuto: {
            Section {
                Picker("Tipo", selection: $tipo) {
                    Text("Scegli").tag(String?.none)
                    ForEach(tipi) { Text($0.etichetta).tag(String?.some($0.codice)) }
                }
                TextField("Titolo (facoltativo)", text: $titolo)
                TextField("Descrizione", text: $descrizione, axis: .vertical)
            }
            Section {
                SceltaPaziente(selezione: $pazienteId)
                Toggle("Scadenza", isOn: $conScadenza)
                if conScadenza { CampoGiorno(etichetta: "Il", giorno: $scadenza) }
            }
        }
        .task { tipi = (try? await app.api.get("/task/tipi")) ?? [] }
    }
}

// MARK: Finance

struct FinanceView: View {
    @Environment(AppModel.self) private var app
    @State private var anno = Giorno.oggi.anno
    @State private var sezione = 0
    @State private var nuovoMovimento = false
    @State private var nuovaScadenza = false

    private struct Dati {
        let riepilogo: RiepilogoFinance
        let movimenti: [Movimento]
        let scadenze: [ScadenzaFiscale]
    }

    var body: some View {
        Caricamento(chiave: anno) {
            let q = [URLQueryItem(name: "anno", value: "\(anno)")]
            async let riepilogo: RiepilogoFinance = app.api.get("/finance/riepilogo", q)
            async let movimenti: [Movimento] = app.api.get("/finance/movimenti", q)
            async let scadenze: [ScadenzaFiscale] = app.api.get("/finance/scadenze", q)
            return try await Dati(riepilogo: riepilogo, movimenti: movimenti, scadenze: scadenze)
        } contenuto: { dati, aggiorna in
            List {
                panoramica(dati.riepilogo).rigaEdo().listRowSeparator(.hidden)
                Picker("Sezione", selection: $sezione) {
                    Text("Scenari").tag(0)
                    Text("Movimenti").tag(1)
                    Text("Scadenze").tag(2)
                }
                .pickerStyle(.segmented)
                .rigaEdo()
                .listRowSeparator(.hidden)
                switch sezione {
                case 0:
                    ForEach(dati.riepilogo.livelli) { livello in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(livello.nome).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                Spacer()
                                Text(Formati.euroIntero(livello.totale)).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                            }
                            if let descrizione = livello.descrizione {
                                Text(descrizione).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                            }
                            Text("Margine soglia \(Formati.euroIntero(livello.margineSoglia)) · obiettivo \(Formati.euroIntero(livello.margineObiettivo))")
                                .font(Tipografia.nota)
                                .foregroundStyle(livello.margineSoglia < 0 ? Tema.critico : Tema.inkSecondario)
                        }
                        .rigaEdo()
                    }
                case 1:
                    if dati.movimenti.isEmpty {
                        StatoVuoto(titolo: "Nessun movimento", icona: "arrow.left.arrow.right").rigaEdo().listRowSeparator(.hidden)
                    }
                    ForEach(dati.movimenti) { movimento in
                        RigaMovimento(movimento: movimento, aggiorna: aggiorna).rigaEdo()
                    }
                default:
                    if dati.scadenze.isEmpty {
                        StatoVuoto(titolo: "Nessuna scadenza", icona: "calendar").rigaEdo().listRowSeparator(.hidden)
                    }
                    ForEach(dati.scadenze) { scadenza in
                        RigaScadenza(scadenza: scadenza, aggiorna: aggiorna).rigaEdo()
                    }
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $nuovoMovimento, onDismiss: { Task { await aggiorna() } }) { MovimentoFormView(movimento: nil) }
            .sheet(isPresented: $nuovaScadenza, onDismiss: { Task { await aggiorna() } }) { ScadenzaFormView(scadenza: nil) }
        }
        .telaEdo()
        .navigationTitle("Finance")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Nuovo movimento", systemImage: "arrow.left.arrow.right") { nuovoMovimento = true }
                    Button("Nuova scadenza", systemImage: "calendar") { nuovaScadenza = true }
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Aggiungi")
            }
        }
    }

    private func panoramica(_ r: RiepilogoFinance) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button { anno -= 1 } label: { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Anno precedente")
                Spacer()
                Overline(String(anno))
                Spacer()
                Button { anno += 1 } label: { Image(systemName: "chevron.right").frame(width: 44, height: 44) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Anno successivo")
            }
            .foregroundStyle(Tema.ember)
            NumeroEroe(valore: Formati.euroIntero(r.saldoCassa), didascalia: "Saldo di cassa", dimensione: 52)
            HStack(alignment: .top) {
                NumeroEroe(valore: Formati.euroIntero(r.totali.lordo), didascalia: "Lordo", dimensione: 24)
                NumeroEroe(valore: Formati.euroIntero(r.totali.netto), didascalia: "Netto", dimensione: 24)
                NumeroEroe(valore: Formati.euroIntero(r.totali.uscite), didascalia: "Uscite", dimensione: 24)
            }
            Chart(r.mesi) { mese in
                BarMark(x: .value("Mese", mese.mese), y: .value("Netto", NSDecimalNumber(decimal: mese.netto).doubleValue), width: 12)
                    .cornerRadius(6)
                    .foregroundStyle(mese.mese == Giorno.oggi.mese && anno == Giorno.oggi.anno ? Tema.ember : Tema.inkTerziario)
            }
            .chartXAxis(.hidden)
            .chartYAxis(.hidden)
            .frame(height: 110)
            .accessibilityLabel("Netto mensile")
            if r.candidatureAperte {
                Text("Candidature da chiudere: il margine del Livello 1 è sotto il valore di un cliente.")
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.attenzione)
            }
        }
    }
}

private struct RigaMovimento: View {
    let movimento: Movimento
    let aggiorna: () async -> Void
    @Environment(AppModel.self) private var app
    @State private var modifica = false

    var body: some View {
        Button { modifica = true } label: {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(movimento.descrizione).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                    Text("\(movimento.data.breve) · \(movimento.categoria.etichetta)")
                        .font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                }
                Spacer()
                Text("\(movimento.tipo == .uscita ? "−" : "+")\(Formati.euro(movimento.importo))")
                    .font(Tipografia.bodyMedio)
                    .monospacedDigit()
                    .foregroundStyle(movimento.tipo == .uscita ? Tema.ink : Tema.positivo)
            }
        }
        .buttonStyle(Premibile())
        .swipeActions {
            Button("Elimina", role: .destructive) {
                Task {
                    await app.esegui({ try await app.api.delete("/finance/movimenti/\(movimento.id)") }, successo: "Movimento eliminato")
                    await aggiorna()
                }
            }
        }
        .sheet(isPresented: $modifica, onDismiss: { Task { await aggiorna() } }) { MovimentoFormView(movimento: movimento) }
    }
}

private struct RigaScadenza: View {
    let scadenza: ScadenzaFiscale
    let aggiorna: () async -> Void
    @Environment(AppModel.self) private var app
    @State private var modifica = false
    @State private var paga = false

    var body: some View {
        Button { modifica = true } label: {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(scadenza.titolo).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                    Text("\(scadenza.dataScadenza.breve) · \(scadenza.categoria.etichetta)")
                        .font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    if let stimato = scadenza.importoStimato {
                        Text(Formati.euro(stimato)).font(Tipografia.bodyMedio).monospacedDigit().foregroundStyle(Tema.ink)
                    }
                    ChipStato(testo: scadenza.stato.etichetta, colore: scadenza.stato == .pagata ? Tema.positivo : Tema.attenzione)
                }
            }
        }
        .buttonStyle(Premibile())
        .swipeActions {
            // Una scadenza pagata ha già il suo movimento di cassa: il backend non la lascia eliminare
            if scadenza.stato != .pagata {
                Button("Elimina", role: .destructive) {
                    Task {
                        await app.esegui({ try await app.api.delete("/finance/scadenze/\(scadenza.id)") }, successo: "Scadenza eliminata")
                        await aggiorna()
                    }
                }
                Button("Paga") { paga = true }
                    .tint(Tema.positivo)
            }
        }
        .sheet(isPresented: $modifica, onDismiss: { Task { await aggiorna() } }) { ScadenzaFormView(scadenza: scadenza) }
        .sheet(isPresented: $paga, onDismiss: { Task { await aggiorna() } }) { PagaScadenzaView(scadenza: scadenza) }
    }
}

/// Stato proprio del foglio: letto dalla riga, `valido` restava fermo al valore prima della swipe action.
private struct PagaScadenzaView: View {
    let scadenza: ScadenzaFiscale
    @Environment(AppModel.self) private var app
    @State private var importo: String
    @State private var data = Giorno.oggi

    init(scadenza: ScadenzaFiscale) {
        self.scadenza = scadenza
        _importo = State(initialValue: scadenza.importoStimato?.testoModulo ?? "")
    }

    var body: some View {
        ModuloFoglio(titolo: "Paga scadenza", etichettaSalva: "Paga", valido: (Decimal(italiano: importo) ?? 0) > 0) {
            guard let valore = Decimal(italiano: importo) else { return }
            let _: ScadenzaFiscale = try await app.api.post("/finance/scadenze/\(scadenza.id)/paga", PagaScadenza(data: data, importo: valore))
            app.mostra("Scadenza pagata")
        } contenuto: {
            Section {
                TextField("Importo (€)", text: $importo).keyboardType(.decimalPad)
                CampoGiorno(etichetta: "Data", giorno: $data)
            }
        }
    }
}

struct MovimentoFormView: View {
    let movimento: Movimento?
    @Environment(AppModel.self) private var app
    @State private var data = Giorno.oggi
    @State private var tipo = TipoMovimento.uscita
    @State private var categoria = CategoriaMovimento.software
    @State private var descrizione = ""
    @State private var importo = ""
    @State private var note = ""

    var body: some View {
        ModuloFoglio(titolo: movimento == nil ? "Nuovo movimento" : "Modifica movimento",
                     valido: descrizione.nonVuoto != nil && (Decimal(italiano: importo) ?? 0) > 0) {
            guard let valore = Decimal(italiano: importo) else { return }
            let corpo = SalvaMovimento(data: data, tipo: tipo, categoria: categoria, descrizione: descrizione, importo: valore, note: note.nonVuoto)
            if let movimento {
                let _: Movimento = try await app.api.put("/finance/movimenti/\(movimento.id)", corpo)
            } else {
                let _: Movimento = try await app.api.post("/finance/movimenti", corpo)
            }
            app.mostra("Movimento salvato")
        } contenuto: {
            Section {
                Picker("Tipo", selection: $tipo) {
                    ForEach(TipoMovimento.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                .pickerStyle(.segmented)
                .onChange(of: tipo) { _, nuovo in
                    if !CategoriaMovimento.di(nuovo).contains(categoria) { categoria = CategoriaMovimento.di(nuovo)[0] }
                }
                Picker("Categoria", selection: $categoria) {
                    ForEach(CategoriaMovimento.di(tipo), id: \.self) { Text($0.etichetta).tag($0) }
                }
                CampoGiorno(etichetta: "Data", giorno: $data)
                TextField("Descrizione", text: $descrizione)
                TextField("Importo (€)", text: $importo).keyboardType(.decimalPad)
                TextField("Note", text: $note, axis: .vertical)
            }
        }
        .onAppear {
            guard let m = movimento else { return }
            data = m.data
            tipo = m.tipo
            categoria = m.categoria
            descrizione = m.descrizione
            importo = m.importo.testoModulo
            note = m.note.testo
        }
    }
}

struct ScadenzaFormView: View {
    let scadenza: ScadenzaFiscale?
    @Environment(AppModel.self) private var app
    @State private var titolo = ""
    @State private var categoria = CategoriaMovimento.imposte
    @State private var data = Giorno.oggi
    @State private var importo = ""
    @State private var note = ""

    var body: some View {
        ModuloFoglio(titolo: scadenza == nil ? "Nuova scadenza" : "Modifica scadenza", valido: titolo.nonVuoto != nil) {
            let corpo = SalvaScadenza(titolo: titolo, categoria: categoria, dataScadenza: data,
                                      importoStimato: Decimal(italiano: importo), note: note.nonVuoto)
            if let scadenza {
                let _: ScadenzaFiscale = try await app.api.put("/finance/scadenze/\(scadenza.id)", corpo)
            } else {
                let _: ScadenzaFiscale = try await app.api.post("/finance/scadenze", corpo)
            }
            app.mostra("Scadenza salvata")
        } contenuto: {
            Section {
                TextField("Titolo", text: $titolo)
                Picker("Categoria", selection: $categoria) {
                    ForEach(CategoriaMovimento.di(.uscita), id: \.self) { Text($0.etichetta).tag($0) }
                }
                CampoGiorno(etichetta: "Scadenza", giorno: $data)
                TextField("Importo stimato (€)", text: $importo).keyboardType(.decimalPad)
                TextField("Note", text: $note, axis: .vertical)
            }
        }
        .onAppear {
            guard let s = scadenza else { return }
            titolo = s.titolo
            categoria = s.categoria
            data = s.dataScadenza
            importo = s.importoStimato?.testoModulo ?? ""
            note = s.note.testo
        }
    }
}

// MARK: Impostazioni

struct ImpostazioniView: View {
    @Environment(AppModel.self) private var app
    @State private var voce: VoceImpostazione?
    @State private var cambio = false
    @State private var esci = false

    var body: some View {
        Caricamento {
            let dati: Panoramica = try await app.api.get("/impostazioni")
            return dati
        } contenuto: { dati, aggiorna in
            List {
                Section {
                    ForEach(dati.integrazioni) { integrazione in
                        VStack(alignment: .leading, spacing: 4) {
                            Toggle(integrazione.nome, isOn: Binding(get: { integrazione.attiva }, set: { valore in
                                Task {
                                    await app.esegui {
                                        let _: VoceImpostazione = try await app.api.put("/impostazioni", AggiornaImpostazione(
                                            chiave: integrazione.codice, valore: valore ? "true" : "false"))
                                    }
                                    await aggiorna()
                                }
                            }))
                            .font(Tipografia.bodyMedio)
                            .foregroundStyle(Tema.ink)
                            .disabled(!integrazione.configurata && !integrazione.attiva)
                            if let nota = integrazione.nota {
                                Text(nota).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                            }
                        }
                        .rigaEdo()
                    }
                } header: {
                    Overline("Integrazioni")
                }
                Section {
                    ForEach(dati.voci) { riga in
                        Button { voce = riga } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(riga.descrizione ?? riga.chiave).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                    .multilineTextAlignment(.leading)
                                Text(riga.valore.testo).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario).lineLimit(2)
                                    .multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(Premibile())
                        .rigaEdo()
                    }
                } header: {
                    Overline("Parametri e modelli")
                }
                Section {
                    Button("Cambia password") { cambio = true }.font(Tipografia.bodyMedio).rigaEdo()
                    Button("Esci", role: .destructive) { esci = true }.font(Tipografia.bodyMedio).rigaEdo()
                } header: {
                    Overline("Account")
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(item: $voce, onDismiss: { Task { await aggiorna() } }) { VoceFormView(voce: $0) }
        }
        .telaEdo()
        .navigationTitle("Impostazioni")
        .sheet(isPresented: $cambio) { CambioPasswordView() }
        .confirmationDialog("Vuoi uscire dall'app?", isPresented: $esci, titleVisibility: .visible) {
            Button("Esci", role: .destructive) { Task { await app.esci() } }
        }
    }
}

private struct VoceFormView: View {
    let voce: VoceImpostazione
    @Environment(AppModel.self) private var app
    @State private var valore = ""

    var body: some View {
        ModuloFoglio(titolo: "Modifica", valido: true) {
            let _: VoceImpostazione = try await app.api.put("/impostazioni", AggiornaImpostazione(chiave: voce.chiave, valore: valore))
            app.mostra("Impostazione salvata")
        } contenuto: {
            Section {
                TextField("Valore", text: $valore, axis: .vertical).lineLimit(3...16)
            } footer: {
                Text(voce.descrizione ?? voce.chiave)
            }
        }
        .onAppear { valore = voce.valore.testo }
    }
}
