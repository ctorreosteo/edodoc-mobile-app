import AVKit
import SwiftUI

// MARK: Feedback

struct FeedbackView: View {
    let check: CheckDettaglio
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Overline("Settimana del \(check.settimana.breve)")
                    .padding(.bottom, 16)
                if check.haFeedback {
                    Text(check.feedbackAudioId != nil ? "Un vocale per te." : "La risposta del Dr. Bo.")
                        .font(Tipografia.display(40))
                        .foregroundStyle(Tema.ink)
                        .padding(.bottom, 28)
                    if check.feedbackAudioId != nil {
                        LettoreVocale(percorso: "/app/check/\(check.id)/feedback/audio")
                            .padding(.bottom, 28)
                    }
                    if let testo = check.feedback, !testo.isEmpty {
                        Text(testo)
                            .font(Tipografia.body)
                            .lineSpacing(6)
                            .foregroundStyle(Tema.ink)
                            .textSelection(.enabled)
                            .padding(.bottom, 12)
                    }
                    if let il = check.feedbackIl {
                        Text(il.giornoOra)
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.inkTerziario)
                            .padding(.bottom, 36)
                    }
                } else {
                    Text("Il Dr. Bo non ha ancora risposto.")
                        .font(Tipografia.display(34))
                        .foregroundStyle(Tema.ink)
                        .padding(.bottom, 36)
                }
                Overline("Le tue risposte").padding(.bottom, 6)
                RigaDato(etichetta: "Pasti ottimali", valore: check.pastiOttimali.map { "\($0) su 21" })
                RigaDato(etichetta: "Pasti liberi", valore: check.pastiLiberi)
                RigaDato(etichetta: "Energia", valore: check.energia.map { "\($0) su 5" })
                RigaDato(etichetta: "Allenamento", valore: check.allenamento)
                RigaDato(etichetta: "Peso", valore: check.pesoKg.map { Formati.peso($0) })
                RigaDato(etichetta: "Andamento del peso", valore: check.andamentoPeso)
                RigaDato(etichetta: "Miglioramenti", valore: check.percezioneMigliorata.map { $0 ? "Sì" : "Non ancora" })
                RigaDato(etichetta: "Note", valore: check.percezione)
                RigaDato(etichetta: "Impegno", valore: check.impegno.map { "\($0) su 5" })
            }
            .padding(Tema.margine)
        }
        .telaEdo(fuoco: .init(x: 0.5, y: 0.1), intensita: 0.8)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard check.feedbackDaLeggere else { return }
            let _: Vuoto? = try? await app.api.post("/app/check/\(check.id)/feedback-letto")
        }
    }
}

// MARK: Percorso

struct PercorsoView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Caricamento {
            let percorso: PercorsoPaziente = try await app.api.get("/app/percorso")
            return percorso
        } contenuto: { dati, aggiorna in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    testata(dati)
                    if dati.visite.isEmpty && dati.percorso == nil {
                        StatoVuoto(titolo: "Il percorso non è ancora iniziato",
                                   testo: "Qui vedrai le tue visite appena il Dr. Bo le fisserà.", icona: "calendar")
                    } else {
                        Linea(nodi: nodi(dati))
                            .padding(.top, 34)
                    }
                    NavigationLink {
                        FattureView()
                    } label: {
                        HStack {
                            Text("Fatture e pagamenti")
                                .font(Tipografia.bodyMedio)
                                .foregroundStyle(Tema.ink)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Tema.inkTerziario)
                        }
                        .padding(20)
                        .vetro(raggio: Tema.raggioControllo + 4, interattivo: true)
                    }
                    .buttonStyle(Premibile())
                    .padding(.top, 36)
                    .accessibilityIdentifier("percorso.fatture")
                }
                .padding(.horizontal, Tema.margine)
                .padding(.bottom, 30)
            }
            .refreshable { await aggiorna() }
        }
        .telaEdo(fuoco: .init(x: 0.1, y: 0.05), intensita: 0.7)
        .navigationTitle("Percorso")
    }

    @ViewBuilder
    private func testata(_ dati: PercorsoPaziente) -> some View {
        if let p = dati.percorso {
            VStack(alignment: .leading, spacing: 6) {
                Overline("\(p.tipo.etichetta) · \(p.stato.etichetta)")
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(p.visiteEffettuate)")
                        .font(Tipografia.display(84))
                        .foregroundStyle(Tema.ink)
                    Text("su \(p.visiteTotali) visite")
                        .font(Tipografia.headline)
                        .foregroundStyle(Tema.inkSecondario)
                }
                if let fine = p.dataFine {
                    Text("Dal \(p.dataInizio.breve) al \(fine.breve)")
                        .font(Tipografia.nota)
                        .foregroundStyle(Tema.inkSecondario)
                }
            }
            .padding(.top, 8)
            .accessibilityElement(children: .combine)
        }
    }

    private func nodi(_ dati: PercorsoPaziente) -> [Nodo] {
        let visite = dati.visite.filter { $0.stato != .annullata }
        let prossima = visite.filter { $0.stato == .programmata && $0.fine.data > Date() }.min { $0.inizio < $1.inizio }
        var nodi = visite.map { Nodo(id: "v\($0.id)", visita: $0, numero: $0.numeroVisita, prossima: $0.id == prossima?.id) }
        if let p = dati.percorso, p.stato == .attivo {
            let inPercorso = visite.filter { $0.numeroVisita != nil && $0.visiteTotali == p.visiteTotali }.count
            if inPercorso < p.visiteTotali {
                let numeri = (inPercorso + 1)...p.visiteTotali
                nodi += numeri.map { Nodo(id: "f\($0)", visita: nil, numero: $0, prossima: false) }
            }
        }
        return nodi
    }
}

private struct Nodo: Identifiable {
    let id: String
    let visita: AppVisita?
    let numero: Int?
    let prossima: Bool
}

/// Una linea verticale con un nodo per visita: fatte piene, future vuote, la prossima accesa.
private struct Linea: View {
    let nodi: [Nodo]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(nodi.enumerated()), id: \.element.id) { indice, nodo in
                HStack(alignment: .top, spacing: 18) {
                    VStack(spacing: 0) {
                        marcatore(nodo)
                        if indice < nodi.count - 1 {
                            Rectangle()
                                .fill(nodo.visita?.stato == .effettuata ? Tema.ember : Tema.hairline)
                                .frame(width: 2)
                                .frame(maxHeight: .infinity)
                        }
                    }
                    .frame(width: 28)
                    testo(nodo)
                        .padding(.bottom, 26)
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func marcatore(_ nodo: Nodo) -> some View {
        if nodo.prossima {
            Ellipse()
                .fill(Tema.ember)
                .frame(width: 26, height: 20)
                .background { Ellipse().fill(Tema.ember.opacity(0.28)).frame(width: 46, height: 36) }
        } else if nodo.visita?.stato == .effettuata {
            Ellipse().fill(Tema.ember).frame(width: 18, height: 14).frame(height: 20)
        } else if nodo.visita?.stato == .nonPresentato {
            Ellipse().strokeBorder(Tema.critico, lineWidth: 1.5).frame(width: 18, height: 14).frame(height: 20)
        } else {
            Ellipse().strokeBorder(Tema.inkTerziario, lineWidth: 1.5).frame(width: 18, height: 14).frame(height: 20)
        }
    }

    @ViewBuilder
    private func testo(_ nodo: Nodo) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let visita = nodo.visita {
                Text(nodo.numero.map { "Visita \($0)" } ?? visita.tipo.etichetta)
                    .font(nodo.prossima ? Tipografia.headline : Tipografia.bodyMedio)
                    .foregroundStyle(Tema.ink)
                Text("\(visita.inizio.giornoOra) · \(visita.modalita.etichetta)")
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.inkSecondario)
                if nodo.prossima {
                    Text(contoAllaRovescia(visita))
                        .font(Tipografia.label)
                        .foregroundStyle(Tema.ember)
                        .padding(.top, 2)
                    if let meet = visita.meetUrl.flatMap(URL.init(string:)) {
                        Link("Entra con Meet", destination: meet)
                            .buttonStyle(BottoneVetro(larghezzaPiena: false))
                            .padding(.top, 8)
                    }
                } else if visita.stato == .nonPresentato {
                    ChipStato(testo: visita.stato.etichetta, colore: Tema.critico)
                }
            } else {
                Text("Visita \(nodo.numero ?? 0)")
                    .font(Tipografia.bodyMedio)
                    .foregroundStyle(Tema.inkTerziario)
                Text("Da fissare")
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.inkTerziario)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func contoAllaRovescia(_ visita: AppVisita) -> String {
        let giorni = Roma.calendario.dateComponents([.day], from: Giorno.oggi.data, to: visita.inizio.giorno.data).day ?? 0
        switch giorni {
        case ...0: return "Oggi"
        case 1: return "Domani"
        default: return "Tra \(giorni) giorni"
        }
    }
}

// MARK: Fatture e pagamenti

struct FattureView: View {
    @Environment(AppModel.self) private var app

    private struct Dati {
        let fatture: [AppFattura]
        let pagamenti: AppListaPagamenti
    }

    var body: some View {
        Caricamento {
            async let fatture: [AppFattura] = app.api.get("/app/fatture")
            async let pagamenti: AppListaPagamenti = app.api.get("/app/pagamenti")
            return try await Dati(fatture: fatture, pagamenti: pagamenti)
        } contenuto: { dati, aggiorna in
            List {
                Section {
                    HStack(spacing: 20) {
                        NumeroEroe(valore: Formati.euroIntero(dati.pagamenti.riepilogo.pagato), didascalia: "Pagato")
                        NumeroEroe(valore: Formati.euroIntero(dati.pagamenti.riepilogo.daPagare), didascalia: "Da pagare")
                    }
                    .rigaEdo()
                    .listRowSeparator(.hidden)
                    if dati.pagamenti.riepilogo.scaduto > 0 {
                        Text("Hai \(Formati.euro(dati.pagamenti.riepilogo.scaduto)) scaduti.")
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.critico)
                            .rigaEdo()
                            .listRowSeparator(.hidden)
                    }
                }
                Section {
                    if dati.fatture.isEmpty {
                        Text("Le fatture compariranno qui dopo ogni pagamento.")
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.inkSecondario)
                            .rigaEdo()
                    }
                    ForEach(dati.fatture) { fattura in
                        BottonePDF(percorso: "/app/fatture/\(fattura.id)/pdf",
                                   nomeFile: fattura.nomeFile ?? "Fattura-\(fattura.numero).pdf") {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Fattura n. \(String(fattura.numero))")
                                        .font(Tipografia.bodyMedio)
                                        .foregroundStyle(Tema.ink)
                                    Text(fattura.dataEmissione.breve)
                                        .font(Tipografia.nota)
                                        .foregroundStyle(Tema.inkSecondario)
                                }
                                Spacer()
                                Text(Formati.euro(fattura.importoTotale))
                                    .font(Tipografia.bodyMedio)
                                    .monospacedDigit()
                                    .foregroundStyle(Tema.ink)
                                Image(systemName: "arrow.down.circle")
                                    .font(.system(size: 20, weight: .light))
                                    .foregroundStyle(Tema.ember)
                            }
                        }
                        .accessibilityHint("Apre il PDF")
                        .accessibilityIdentifier("fattura.riga")
                        .rigaEdo()
                    }
                } header: {
                    Overline("Fatture")
                }
                Section {
                    ForEach(dati.pagamenti.righe) { pagamento in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(pagamento.descrizione)
                                    .font(Tipografia.bodyMedio)
                                    .foregroundStyle(Tema.ink)
                                Text((pagamento.dataPagamento ?? pagamento.scadenza)?.breve ?? "")
                                    .font(Tipografia.nota)
                                    .foregroundStyle(Tema.inkSecondario)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 4) {
                                Text(Formati.euro(pagamento.importo))
                                    .font(Tipografia.bodyMedio)
                                    .monospacedDigit()
                                    .foregroundStyle(Tema.ink)
                                ChipStato(testo: pagamento.scaduto ? "Scaduto" : pagamento.stato.etichetta,
                                          colore: pagamento.scaduto ? Tema.critico : pagamento.stato.colore)
                            }
                        }
                        .rigaEdo()
                    }
                } header: {
                    Overline("Pagamenti")
                }
            }
            .listaEdo()
            .refreshable { await aggiorna() }
        }
        .telaEdo()
        .navigationTitle("Fatture e pagamenti")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: Corsi

struct CorsiView: View {
    @Environment(AppModel.self) private var app
    @Namespace private var zoom

    var body: some View {
        Caricamento {
            let corsi: [AppCorso] = try await app.api.get("/app/corsi")
            return corsi
        } contenuto: { corsi, aggiorna in
            ScrollView {
                if corsi.isEmpty {
                    StatoVuoto(titolo: "Ancora nessun corso", testo: "Quando il Dr. Bo ne pubblicherà uno, lo troverai qui.", icona: "play.rectangle")
                }
                LazyVStack(spacing: 18) {
                    ForEach(corsi) { corso in
                        NavigationLink {
                            CorsoView(corso: corso).destinazioneZoom(corso.id, in: zoom)
                        } label: {
                            schedaCorso(corso)
                        }
                        .buttonStyle(Premibile())
                        .sorgenteZoom(corso.id, in: zoom)
                        .accessibilityIdentifier("corso.scheda")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .refreshable { await aggiorna() }
        }
        .telaEdo(fuoco: .init(x: 0.9, y: 0.0), intensita: 0.5)
        .navigationTitle("Corsi")
    }

    private func schedaCorso(_ corso: AppCorso) -> some View {
        Copertina(id: corso.copertinaId, seme: corso.id)
            .frame(height: 430)
            .overlay {
                LinearGradient(colors: [.clear, .black.opacity(0.72)], startPoint: .center, endPoint: .bottom)
            }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 8) {
                    Overline(metadati(corso), colore: .white.opacity(0.72))
                    Text(corso.titolo)
                        .font(Tipografia.display(32))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                }
                .padding(22)
            }
            .clipShape(RoundedRectangle(cornerRadius: Tema.raggioPannello, style: .continuous))
    }

    private func metadati(_ corso: AppCorso) -> String {
        let lezioni = corso.lezioni == 1 ? "1 lezione" : "\(corso.lezioni) lezioni"
        return corso.secondiVideo > 0 ? "\(lezioni) · \(Formati.durata(corso.secondiVideo))" : lezioni
    }
}

struct CorsoView: View {
    let corso: AppCorso
    @Environment(AppModel.self) private var app

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Copertina(id: corso.copertinaId, seme: corso.id)
                    .frame(height: 320)
                VStack(alignment: .leading, spacing: 10) {
                    Text(corso.titolo)
                        .font(Tipografia.display(38))
                        .foregroundStyle(Tema.ink)
                    if let descrizione = corso.descrizione {
                        Text(descrizione)
                            .font(Tipografia.body)
                            .foregroundStyle(Tema.inkSecondario)
                    }
                }
                .padding(Tema.margine)
                Caricamento(chiave: corso.id) {
                    let dettaglio: AppCorsoDettaglio = try await app.api.get("/app/corsi/\(corso.id)")
                    return dettaglio
                } contenuto: { dettaglio, _ in
                    LazyVStack(alignment: .leading, spacing: 30) {
                        ForEach(dettaglio.moduli) { modulo in
                            VStack(alignment: .leading, spacing: 2) {
                                Overline(modulo.titolo).padding(.bottom, 8)
                                ForEach(modulo.lezioni) { lezione in
                                    NavigationLink {
                                        LezioneView(riga: lezione)
                                    } label: {
                                        rigaLezione(lezione)
                                    }
                                    .buttonStyle(Premibile())
                                    Filetto()
                                }
                            }
                        }
                    }
                    .padding(.horizontal, Tema.margine)
                }
                .frame(minHeight: 160)
            }
            .padding(.bottom, 30)
        }
        .ignoresSafeArea(edges: .top)
        .background(Tema.canvas)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func rigaLezione(_ lezione: AppLezioneRiga) -> some View {
        HStack(spacing: 14) {
            Image(systemName: lezione.tipo == .video ? "play.fill" : "text.alignleft")
                .font(.system(size: 13))
                .foregroundStyle(Tema.ember)
                .frame(width: 34, height: 26)
                .background(Tema.ember.opacity(0.13), in: Ellipse())
            Text(lezione.titolo)
                .font(Tipografia.bodyMedio)
                .foregroundStyle(Tema.ink)
                .multilineTextAlignment(.leading)
            Spacer()
            if let durata = lezione.durataSecondi {
                Text(Formati.durata(durata))
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.inkSecondario)
            }
        }
        .frame(minHeight: 60)
    }
}

struct LezioneView: View {
    let riga: AppLezioneRiga
    @Environment(AppModel.self) private var app

    var body: some View {
        Caricamento(chiave: riga.id) {
            let lezione: AppLezione = try await app.api.get("/app/lezioni/\(riga.id)")
            return lezione
        } contenuto: { lezione, aggiorna in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let hls = lezione.riproduzione?.hlsUrl.flatMap(URL.init(string:)) {
                        LettoreVideo(url: hls)
                            .aspectRatio(16 / 9, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: Tema.raggioControllo, style: .continuous))
                    } else if lezione.tipo == .video {
                        StatoVuoto(titolo: "Video non disponibile", testo: "Riprova tra qualche istante.", icona: "play.slash",
                                   azione: ("Riprova", { Task { await aggiorna() } }))
                    }
                    Text(lezione.titolo)
                        .font(Tipografia.display(32))
                        .foregroundStyle(Tema.ink)
                    if let descrizione = lezione.descrizione {
                        Text(descrizione)
                            .font(Tipografia.body)
                            .foregroundStyle(Tema.inkSecondario)
                    }
                    if let contenuto = lezione.contenuto, !contenuto.isEmpty {
                        TestoMarkdown(sorgente: contenuto)
                    }
                }
                .padding(.horizontal, Tema.margine)
                .padding(.bottom, 30)
            }
        }
        .telaEdo()
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// L'URL è firmato e dura un'ora: si chiede a ogni apertura e non si conserva.
struct LettoreVideo: View {
    let url: URL
    @State private var player: AVPlayer?

    var body: some View {
        VideoPlayer(player: player)
            .onAppear {
                try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
                player = AVPlayer(url: url)
            }
            .onDisappear {
                player?.pause()
                player = nil
            }
    }
}
