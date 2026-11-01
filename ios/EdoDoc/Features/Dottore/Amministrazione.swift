import SwiftUI
import UIKit

// MARK: Acquisti

struct AcquistiView: View {
    @Environment(AppModel.self) private var app
    @State private var nuovo = false

    var body: some View {
        Caricamento {
            let dati: Acquisti = try await app.api.get("/percorsi")
            return dati
        } contenuto: { dati, aggiorna in
            List {
                if dati.percorsi.isEmpty && dati.fuoriPercorso.isEmpty {
                    StatoVuoto(titolo: "Nessun acquisto", icona: "bag").rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(dati.percorsi) { acquisto in
                    NavigationLink { AcquistoDettaglioView(acquisto: acquisto) } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(acquisto.pazienteNome).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                Text("\(acquisto.percorso.tipo.etichetta) · \(acquisto.percorso.stato.etichetta)")
                                    .font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 3) {
                                Text(Formati.euroIntero(acquisto.percorso.importoTotale))
                                    .font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                                if acquisto.scaduto > 0 {
                                    Text("\(Formati.euroIntero(acquisto.scaduto)) scaduti").font(Tipografia.nota).foregroundStyle(Tema.critico)
                                } else if acquisto.daPagare > 0 {
                                    Text("\(Formati.euroIntero(acquisto.daPagare)) da pagare").font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                                }
                            }
                        }
                    }
                    .rigaEdo()
                }
                if !dati.fuoriPercorso.isEmpty {
                    Section {
                        ForEach(dati.fuoriPercorso) { RigaPagamentoView(riga: $0, mostraPaziente: true, aggiorna: aggiorna).rigaEdo() }
                    } header: {
                        Overline("Fuori percorso")
                    }
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $nuovo, onDismiss: { Task { await aggiorna() } }) {
                PercorsoFormView(pazienteId: nil)
            }
        }
        .telaEdo()
        .navigationTitle("Acquisti")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuovo = true } label: { Image(systemName: "plus") }.accessibilityLabel("Nuovo percorso")
            }
        }
    }
}

struct AcquistoDettaglioView: View {
    let acquisto: Acquisto
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var annulla = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Overline(acquisto.percorso.tipo.etichetta).padding(.bottom, 8)
                Text(acquisto.pazienteNome).font(Tipografia.display(38)).foregroundStyle(Tema.ink).padding(.bottom, 18)
                HStack {
                    NumeroEroe(valore: Formati.euroIntero(acquisto.pagato), didascalia: "Pagato", dimensione: 30)
                    NumeroEroe(valore: Formati.euroIntero(acquisto.daPagare), didascalia: "Da pagare", dimensione: 30)
                }
                .padding(.bottom, 12)
                RigaDato(etichetta: "Piano", valore: acquisto.percorso.pianoPagamento?.etichetta)
                RigaDato(etichetta: "Inizio", valore: acquisto.percorso.dataInizio.breve)
                RigaDato(etichetta: "Fine", valore: acquisto.percorso.dataFine?.breve)
                RigaDato(etichetta: "Visite", valore: "\(acquisto.percorso.visiteEffettuate) di \(acquisto.percorso.visiteTotali)")
                RigaDato(etichetta: "Prossima scadenza", valore: acquisto.prossimaScadenza?.breve)
                RigaDato(etichetta: "Note", valore: acquisto.percorso.note)
                Overline("Pagamenti").padding(.top, 20).padding(.bottom, 6)
                ForEach(acquisto.pagamenti) { riga in
                    RigaPagamentoView(riga: riga, mostraPaziente: false, aggiorna: {})
                    Filetto()
                }
                if acquisto.percorso.stato == .attivo {
                    Button("Annulla il percorso", role: .destructive) { annulla = true }
                        .buttonStyle(BottoneVetro())
                        .padding(.top, 26)
                }
            }
            .padding(Tema.margine)
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Annullare il percorso?", isPresented: $annulla, titleVisibility: .visible) {
            Button("Annulla il percorso", role: .destructive) {
                Task {
                    let riuscito = await app.esegui({ try await app.api.delete("/percorsi/\(acquisto.percorso.id)") }, successo: "Percorso annullato")
                    if riuscito { chiudi() }
                }
            }
        }
    }
}

struct PercorsoFormView: View {
    @State var pazienteId: Int?
    @Environment(AppModel.self) private var app
    @State private var tipo = TipoPercorso.bussola
    @State private var piano = PianoPagamento.unico
    @State private var inizio = Giorno.oggi
    @State private var note = ""
    @State private var listino: Listino?

    var body: some View {
        ModuloFoglio(titolo: "Nuovo percorso", valido: pazienteId != nil) {
            guard let pazienteId else { return }
            let _: SintesiPercorso = try await app.api.post("/percorsi", CreaPercorso(
                pazienteId: pazienteId, tipo: tipo, pianoPagamento: piano, dataInizio: inizio, note: note.nonVuoto))
            app.mostra("Percorso creato")
        } contenuto: {
            Section { SceltaPaziente(selezione: $pazienteId) }
            Section {
                Picker("Tipo", selection: $tipo) {
                    ForEach(TipoPercorso.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                Picker("Pagamento", selection: $piano) {
                    ForEach(PianoPagamento.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                CampoGiorno(etichetta: "Inizio", giorno: $inizio)
                TextField("Note", text: $note, axis: .vertical)
            } footer: {
                if let prezzo { Text(prezzo) }
            }
        }
        .task { listino = try? await app.api.get("/percorsi/listino") }
    }

    private var prezzo: String? {
        guard let listino else { return nil }
        switch (tipo, piano) {
        case (.mantenimento, .unico): return "\(Formati.euroIntero(listino.mantenimento)) in anticipo"
        case (.mantenimento, _): return "2 rate da \(Formati.euroIntero(listino.rataMantenimento))"
        case (_, .unico): return "\(Formati.euroIntero(listino.unico)) in anticipo"
        default: return "3 rate da \(Formati.euroIntero(listino.rataPercorso))"
        }
    }
}

// MARK: Pagamenti

struct RigaPagamentoView: View {
    let riga: RigaPagamento
    let mostraPaziente: Bool
    let aggiorna: () async -> Void
    @Environment(AppModel.self) private var app
    @State private var registra = false
    @State private var modifica = false
    @State private var emetti = false
    @State private var email: RichiestaEmail?
    @State private var annulla = false

    var body: some View {
        Menu {
            if riga.stato == .daPagare {
                Button("Registra pagamento", systemImage: "checkmark.circle") { registra = true }
                Button("Modifica", systemImage: "pencil") { modifica = true }
                Button("Sollecita via email", systemImage: "envelope") {
                    email = RichiestaEmail(tipo: .sollecito, pazienteId: riga.pazienteId, pagamentoId: riga.id)
                }
                Button("Annulla il pagamento", systemImage: "xmark", role: .destructive) { annulla = true }
            }
            if riga.stato == .pagato {
                if !riga.fatturato {
                    Button("Emetti fattura", systemImage: "doc.badge.plus") { emetti = true }
                }
                Button("Annulla registrazione", systemImage: "arrow.uturn.backward") {
                    Task {
                        await app.esegui({
                            let _: RigaPagamento = try await app.api.post("/pagamenti/\(riga.id)/annulla-registrazione")
                        }, successo: "Registrazione annullata")
                        await aggiorna()
                    }
                }
            }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(mostraPaziente ? riga.pazienteNome : riga.descrizione)
                        .font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                        .multilineTextAlignment(.leading)
                    Text(sottotitolo).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text(Formati.euro(riga.importo)).font(Tipografia.bodyMedio).monospacedDigit().foregroundStyle(Tema.ink)
                    ChipStato(testo: riga.scaduto ? "Scaduto" : riga.stato.etichetta,
                              colore: riga.scaduto ? Tema.critico : riga.stato.colore)
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(Premibile())
        .sheet(isPresented: $registra, onDismiss: { Task { await aggiorna() } }) { RegistraPagamentoView(riga: riga) }
        .sheet(isPresented: $modifica, onDismiss: { Task { await aggiorna() } }) { PagamentoFormView(pagamento: riga, pazienteId: riga.pazienteId) }
        .sheet(isPresented: $emetti, onDismiss: { Task { await aggiorna() } }) { EmettiFatturaView(pagamento: riga, pazienteId: riga.pazienteId) }
        .sheet(item: $email) { EmailView(richiesta: $0) }
        .confirmationDialog("Annullare il pagamento?", isPresented: $annulla, titleVisibility: .visible) {
            Button("Annulla il pagamento", role: .destructive) {
                Task {
                    await app.esegui({ try await app.api.delete("/pagamenti/\(riga.id)") }, successo: "Pagamento annullato")
                    await aggiorna()
                }
            }
        }
    }

    private var sottotitolo: String {
        var parti: [String] = []
        if mostraPaziente { parti.append(riga.descrizione) }
        if let data = riga.dataPagamento ?? riga.scadenza { parti.append(data.breve) }
        if riga.fatturato { parti.append("fatturato") }
        return parti.joined(separator: " · ")
    }
}

struct PagamentiView: View {
    @Environment(AppModel.self) private var app
    @State private var mese = Giorno.oggi.primoDelMese
    @State private var nuovo = false

    var body: some View {
        Caricamento(chiave: mese) {
            let lista: ListaPagamenti = try await app.api.get("/pagamenti", URLQueryItem.periodo(mese))
            return lista
        } contenuto: { lista, aggiorna in
            List {
                VStack(spacing: 12) {
                    SelettoreMese(mese: $mese)
                    HStack(alignment: .top) {
                        NumeroEroe(valore: Formati.euroIntero(lista.riepilogo.pagato), didascalia: "Pagato", dimensione: 26)
                        NumeroEroe(valore: Formati.euroIntero(lista.riepilogo.daPagare), didascalia: "Da pagare", dimensione: 26)
                        NumeroEroe(valore: Formati.euroIntero(lista.riepilogo.scaduto), didascalia: "Scaduto", dimensione: 26,
                                   colore: lista.riepilogo.scaduto > 0 ? Tema.critico : Tema.ink)
                    }
                }
                .buttonStyle(.plain)
                .rigaEdo()
                .listRowSeparator(.hidden)
                if lista.righe.isEmpty {
                    StatoVuoto(titolo: "Nessun pagamento nel mese", icona: "creditcard").rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(lista.righe) { RigaPagamentoView(riga: $0, mostraPaziente: true, aggiorna: aggiorna).rigaEdo() }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $nuovo, onDismiss: { Task { await aggiorna() } }) {
                PagamentoFormView(pagamento: nil, pazienteId: nil)
            }
        }
        .telaEdo()
        .navigationTitle("Pagamenti")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuovo = true } label: { Image(systemName: "plus") }.accessibilityLabel("Nuovo pagamento")
            }
        }
    }
}

struct PagamentoFormView: View {
    let pagamento: RigaPagamento?
    @State var pazienteId: Int?
    @Environment(AppModel.self) private var app
    @State private var tipo = TipoPagamento.altro
    @State private var descrizione = ""
    @State private var importo = ""
    @State private var scadenza = Giorno.oggi
    @State private var note = ""

    var body: some View {
        ModuloFoglio(titolo: pagamento == nil ? "Nuovo pagamento" : "Modifica pagamento",
                     valido: pazienteId != nil && descrizione.nonVuoto != nil && (Decimal(italiano: importo) ?? 0) > 0) {
            guard let pazienteId, let valore = Decimal(italiano: importo) else { return }
            let corpo = SalvaPagamento(pazienteId: pazienteId, tipo: tipo, descrizione: descrizione, importo: valore, scadenza: scadenza, note: note.nonVuoto)
            if let pagamento {
                let _: RigaPagamento = try await app.api.put("/pagamenti/\(pagamento.id)", corpo)
            } else {
                let _: RigaPagamento = try await app.api.post("/pagamenti", corpo)
            }
            app.mostra("Pagamento salvato")
        } contenuto: {
            if pagamento == nil { Section { SceltaPaziente(selezione: $pazienteId) } }
            Section {
                Picker("Tipo", selection: $tipo) {
                    ForEach(TipoPagamento.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                TextField("Descrizione", text: $descrizione)
                TextField("Importo (€)", text: $importo).keyboardType(.decimalPad)
                CampoGiorno(etichetta: "Scadenza", giorno: $scadenza)
                TextField("Note", text: $note, axis: .vertical)
            }
        }
        .onAppear {
            guard let p = pagamento else { return }
            tipo = p.tipo ?? .altro
            descrizione = p.descrizione
            importo = p.importo.testoModulo
            scadenza = p.scadenza ?? .oggi
            note = p.note.testo
        }
    }
}

struct RegistraPagamentoView: View {
    let riga: RigaPagamento
    @Environment(AppModel.self) private var app
    @State private var data = Giorno.oggi
    @State private var metodo = MetodoPagamento.bonifico
    @State private var contabile = false
    @State private var note = ""

    var body: some View {
        ModuloFoglio(titolo: "Registra pagamento", etichettaSalva: "Registra", valido: true) {
            let _: RigaPagamento = try await app.api.post("/pagamenti/\(riga.id)/registra", RegistraPagamento(
                dataPagamento: data, metodo: metodo, contabile: contabile, note: note.nonVuoto))
            app.mostra("Pagamento registrato")
        } contenuto: {
            Section {
                LabeledContent(riga.descrizione, value: Formati.euro(riga.importo))
                CampoGiorno(etichetta: "Data", giorno: $data)
                Picker("Metodo", selection: $metodo) {
                    ForEach(MetodoPagamento.scelte, id: \.self) { Text($0.etichetta).tag($0) }
                }
                Toggle("Contabile ricevuta", isOn: $contabile)
                TextField("Note", text: $note, axis: .vertical)
            }
        }
    }
}

// MARK: Fatture

struct RigaFatturaView: View {
    let fattura: RigaFattura

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("N. \(String(fattura.numero)) · \(fattura.intestatario.testo)")
                    .font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                    .multilineTextAlignment(.leading)
                Text(fattura.dataEmissione.breve).font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(Formati.euro(fattura.importoTotale)).font(Tipografia.bodyMedio).monospacedDigit().foregroundStyle(Tema.ink)
                if fattura.stato == .annullata { ChipStato(testo: "Annullata", colore: Tema.critico) }
            }
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}

struct FattureDottoreView: View {
    @Environment(AppModel.self) private var app
    @State private var mese = Giorno.oggi.primoDelMese
    @State private var nuova = false

    var body: some View {
        Caricamento(chiave: mese) {
            let elenco: [RigaFattura] = try await app.api.get("/fatture", URLQueryItem.periodo(mese))
            return elenco
        } contenuto: { elenco, aggiorna in
            List {
                SelettoreMese(mese: $mese).buttonStyle(.plain).rigaEdo().listRowSeparator(.hidden)
                NavigationLink { TesseraView() } label: {
                    Label("Tessera Sanitaria", systemImage: "cross.case")
                        .font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                }
                .rigaEdo()
                if elenco.isEmpty {
                    StatoVuoto(titolo: "Nessuna fattura nel mese", icona: "doc.text").rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(elenco) { fattura in
                    NavigationLink { FatturaDettaglioView(fattura: fattura) } label: {
                        RigaFatturaView(fattura: fattura)
                    }
                    .rigaEdo()
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
            .sheet(isPresented: $nuova, onDismiss: { Task { await aggiorna() } }) {
                EmettiFatturaView(pagamento: nil, pazienteId: nil)
            }
        }
        .telaEdo()
        .navigationTitle("Fatture")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nuova = true } label: { Image(systemName: "plus") }.accessibilityLabel("Nuova fattura")
            }
        }
    }
}

struct FatturaDettaglioView: View {
    @State var fattura: RigaFattura
    @Environment(AppModel.self) private var app
    @State private var annulla = false
    @State private var motivo = ""
    @State private var email: RichiestaEmail?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Overline("Fattura n. \(String(fattura.numero))").padding(.bottom, 8)
                Text(Formati.euro(fattura.importoTotale))
                    .font(Tipografia.display(56))
                    .foregroundStyle(Tema.ink)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                    .padding(.bottom, 14)
                if fattura.stato == .annullata {
                    ChipStato(testo: "Annullata", colore: Tema.critico).padding(.bottom, 14)
                }
                BottonePDF(percorso: "/fatture/\(fattura.id)/pdf", nomeFile: fattura.nomeFile ?? "Fattura-\(fattura.numero).pdf") {
                    Text("Apri il PDF")
                        .font(Tipografia.bodyMedio.weight(.semibold))
                        .foregroundStyle(Tema.suEmber)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(Tema.ember, in: Capsule())
                }
                .padding(.bottom, 12)
                if fattura.stato == .emessa {
                    Button("Invia via email") {
                        email = RichiestaEmail(tipo: .fattura, pazienteId: fattura.pazienteId, fatturaId: fattura.id)
                    }
                    .buttonStyle(BottoneVetro())
                    .padding(.bottom, 16)
                }
                RigaDato(etichetta: "Data", valore: fattura.dataEmissione.breve)
                RigaDato(etichetta: "Intestatario", valore: fattura.intestatario)
                RigaDato(etichetta: "Indirizzo", valore: fattura.indirizzo)
                RigaDato(etichetta: "Codice fiscale", valore: fattura.codiceFiscale)
                RigaDato(etichetta: "Descrizione", valore: fattura.descrizione)
                RigaDato(etichetta: "Imponibile", valore: fattura.imponibile.map { Formati.euro($0) })
                RigaDato(etichetta: "Contributo", valore: fattura.contributo.map { Formati.euro($0) })
                RigaDato(etichetta: "Bollo", valore: fattura.bollo.map { Formati.euro($0) })
                RigaDato(etichetta: "Email inviata", valore: fattura.emailInviataIl?.giornoOra)
                RigaDato(etichetta: "Tessera Sanitaria", valore: fattura.tsOpposizione ? "Opposizione" : fattura.tsStato?.etichetta)
                RigaDato(etichetta: "Motivo annullamento", valore: fattura.motivoAnnullamento)
                if fattura.stato == .emessa {
                    Button("Annulla la fattura", role: .destructive) { annulla = true }
                        .buttonStyle(BottoneVetro())
                        .padding(.top, 22)
                }
            }
            .padding(Tema.margine)
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $email) { EmailView(richiesta: $0) }
        .sheet(isPresented: $annulla) {
            ModuloFoglio(titolo: "Annulla fattura", etichettaSalva: "Annulla la fattura", valido: motivo.nonVuoto != nil) {
                fattura = try await app.api.post("/fatture/\(fattura.id)/annulla", AnnullaFattura(motivo: motivo))
                app.mostra("Fattura annullata")
            } contenuto: {
                Section {
                    TextField("Motivo", text: $motivo, axis: .vertical)
                } footer: {
                    Text("Una fattura emessa non si modifica: si annulla e il numero resta occupato.")
                }
            }
        }
    }
}

/// Prima l'anteprima, poi l'emissione: il numero è progressivo e non torna indietro.
struct EmettiFatturaView: View {
    let pagamento: RigaPagamento?
    @State var pazienteId: Int?
    @Environment(AppModel.self) private var app
    @State private var importo = ""
    @State private var data = Giorno.oggi
    @State private var descrizione = ""
    @State private var opposizione = false
    @State private var anteprima: AnteprimaFattura?
    @State private var errore: String?

    private var richiesta: EmettiFattura? {
        if let pagamento {
            return EmettiFattura(pagamentoId: pagamento.id, data: data, descrizione: descrizione.nonVuoto, tsOpposizione: opposizione)
        }
        guard let pazienteId, let valore = Decimal(italiano: importo), valore > 0 else { return nil }
        return EmettiFattura(pazienteId: pazienteId, importo: valore, data: data, descrizione: descrizione.nonVuoto, tsOpposizione: opposizione)
    }

    var body: some View {
        ModuloFoglio(titolo: "Emetti fattura", etichettaSalva: "Emetti", valido: anteprima != nil && richiesta != nil) {
            guard let richiesta else { return }
            let _: RigaFattura = try await app.api.post("/fatture", richiesta)
            app.mostra("Fattura emessa")
        } contenuto: {
            Section {
                if let pagamento {
                    LabeledContent(pagamento.pazienteNome, value: Formati.euro(pagamento.importo))
                } else {
                    SceltaPaziente(selezione: $pazienteId)
                    TextField("Importo (€)", text: $importo).keyboardType(.decimalPad)
                }
                CampoGiorno(etichetta: "Data", giorno: $data)
                TextField("Descrizione", text: $descrizione, axis: .vertical)
                Toggle("Opposizione Tessera Sanitaria", isOn: $opposizione)
            }
            Section {
                Button("Calcola anteprima") { Task { await calcola() } }
                    .disabled(richiesta == nil)
                if let errore { Text(errore).foregroundStyle(Tema.critico) }
            }
            if let a = anteprima {
                Section("Anteprima fattura n. \(String(a.numero))") {
                    LabeledContent("Intestatario", value: a.intestatario.testo)
                    LabeledContent("Codice fiscale", value: a.codiceFiscale.testo)
                    LabeledContent("Imponibile", value: Formati.euro(a.imponibile))
                    LabeledContent("Contributo", value: Formati.euro(a.contributo))
                    LabeledContent("Bollo", value: Formati.euro(a.bollo))
                    LabeledContent("Totale", value: Formati.euro(a.importoTotale))
                }
            }
        }
        .onChange(of: importo) { anteprima = nil }
        .onChange(of: pazienteId) { anteprima = nil }
        .onChange(of: data) { anteprima = nil }
    }

    private func calcola() async {
        guard let richiesta else { return }
        do {
            anteprima = try await app.api.post("/fatture/anteprima", richiesta)
            errore = nil
        } catch {
            errore = error.localizedDescription
        }
    }
}

struct TesseraView: View {
    @Environment(AppModel.self) private var app
    @State private var mese = Giorno.oggi.primoDelMese

    var body: some View {
        Caricamento(chiave: mese) {
            let elenco: [RigaTessera] = try await app.api.get("/fatture/tessera-sanitaria", [
                URLQueryItem(name: "mese", value: String(mese.testo.prefix(7)))])
            return elenco
        } contenuto: { elenco, aggiorna in
            List {
                SelettoreMese(mese: $mese).buttonStyle(.plain).rigaEdo().listRowSeparator(.hidden)
                if elenco.isEmpty {
                    StatoVuoto(titolo: "Niente da inserire", testo: "Tutte le fatture del mese sono sul Sistema TS.", icona: "checkmark.circle")
                        .rigaEdo().listRowSeparator(.hidden)
                }
                ForEach(elenco) { riga in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("N. \(String(riga.numero)) · \(riga.data.breve)")
                                .font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                            Spacer()
                            Text(Formati.euro(riga.importo)).font(Tipografia.bodyMedio).foregroundStyle(Tema.ink)
                        }
                        Text(riga.codiceFiscale.testo)
                            .font(Tipografia.nota).foregroundStyle(Tema.inkSecondario)
                            .textSelection(.enabled)
                    }
                    .rigaEdo()
                    .swipeActions {
                        Button("Inserita") {
                            Task {
                                await app.esegui({
                                    let _: Vuoto = try await app.api.post("/fatture/\(riga.fatturaId)/tessera-sanitaria/inserita",
                                                                          [URLQueryItem(name: "inserita", value: "true")])
                                }, successo: "Segnata come inserita")
                                await aggiorna()
                            }
                        }
                        .tint(Tema.positivo)
                    }
                    .contextMenu {
                        Button("Copia il codice fiscale", systemImage: "doc.on.doc") { UIPasteboard.general.string = riga.codiceFiscale }
                    }
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
        }
        .telaEdo()
        .navigationTitle("Tessera Sanitaria")
        .navigationBarTitleDisplayMode(.inline)
    }
}
