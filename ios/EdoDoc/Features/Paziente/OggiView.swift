import SwiftUI

private struct DatiOggi {
    let riepilogo: RiepilogoApp
    let check: StatoCheck
    let percorso: PercorsoPaziente

    var prossimaVisita: AppVisita? {
        percorso.visite.filter { $0.stato == .programmata && $0.fine.data > Date() }.min { $0.inizio < $1.inizio }
    }
}

/// Non una dashboard: una frase dice cosa fare adesso, sotto c'è l'unico gesto che serve.
struct OggiView: View {
    let vaiAllaChat: () -> Void
    let vaiAlPercorso: () -> Void
    @Environment(AppModel.self) private var app
    @State private var compilazione: StatoCheck?
    @State private var feedback: CheckDettaglio?
    @State private var versione = 0

    private enum Momento {
        case feedback(CheckDettaglio), check, messaggi(Int), visita(AppVisita), quiete
    }

    var body: some View {
        Caricamento(chiave: versione, carica: carica) { dati, aggiorna in
            contenuto(dati, aggiorna: aggiorna)
        }
        .background { Brace(fuoco: .init(x: 0.72, y: 0.3), intensita: 1) }
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(item: Binding(get: { compilazione.map(Riferimento.init) }, set: { compilazione = $0?.stato }),
                         onDismiss: { versione += 1 }) { riferimento in
            CheckFlowView(stato: riferimento.stato)
        }
        .sheet(item: $feedback, onDismiss: { versione += 1 }) { check in
            NavigationStack { FeedbackView(check: check) }
        }
        .navigationDestination(for: RottaPaziente.self) { rotta in
            switch rotta {
            case .profilo: ProfiloView()
            case .storico: StoricoCheckView()
            }
        }
    }

    private struct Riferimento: Identifiable {
        let stato: StatoCheck
        var id: String { stato.settimana.testo }
    }

    private func carica() async throws -> DatiOggi {
        async let riepilogo: RiepilogoApp = app.api.get("/app/riepilogo")
        async let check: StatoCheck = app.api.get("/app/check/corrente")
        async let percorso: PercorsoPaziente = app.api.get("/app/percorso")
        return try await DatiOggi(riepilogo: riepilogo, check: check, percorso: percorso)
    }

    private func momento(_ dati: DatiOggi) -> Momento {
        if let check = dati.check.check, check.feedbackDaLeggere { return .feedback(check) }
        if !dati.check.compilato { return .check }
        if dati.riepilogo.messaggiDaLeggere > 0 { return .messaggi(dati.riepilogo.messaggiDaLeggere) }
        if let visita = dati.prossimaVisita { return .visita(visita) }
        return .quiete
    }

    private func contenuto(_ dati: DatiOggi, aggiorna: @escaping () async -> Void) -> some View {
        let attuale = momento(dati)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Overline(Giorno.oggi.esteso)
                    Spacer()
                    NavigationLink(value: RottaPaziente.profilo) {
                        Avatar(nome: "\(app.utente?.nome ?? "") \(app.utente?.cognome ?? "")", dimensione: 40)
                    }
                    .accessibilityLabel("Profilo")
                    .accessibilityIdentifier("oggi.profilo")
                }
                .padding(.bottom, 84)

                Text(frase(attuale))
                    .font(Tipografia.display(50))
                    .foregroundStyle(Tema.ink)
                    .tracking(-0.6)
                    .lineSpacing(2)
                    .minimumScaleFactor(0.7)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("oggi.frase")
                    .padding(.bottom, 14)
                Text(sottotitolo(attuale, dati))
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.inkSecondario)
                    .padding(.bottom, 32)
                azione(attuale, dati)
                    .padding(.bottom, 64)

                dettagli(dati, attuale)
            }
            .padding(.horizontal, Tema.margine + 2)
            .padding(.top, 14)
            .padding(.bottom, 40)
        }
        .refreshable { await aggiorna() }
    }

    private func frase(_ momento: Momento) -> String {
        let nome = app.utente?.nome ?? ""
        switch momento {
        case .feedback(let check):
            return check.feedbackAudioId != nil ? "Il Dr. Bo ti ha lasciato un vocale." : "Il Dr. Bo ti ha risposto."
        case .check: return "\(nome), com'è andata questa settimana?"
        case .messaggi(let quanti): return quanti == 1 ? "Hai un nuovo messaggio." : "Hai \(quanti) nuovi messaggi."
        case .visita(let visita):
            let giorni = Roma.calendario.dateComponents([.day], from: Giorno.oggi.data, to: visita.inizio.giorno.data).day ?? 0
            switch giorni {
            case 0: return "Oggi hai la visita, alle \(visita.inizio.ora)."
            case 1: return "Domani hai la visita, alle \(visita.inizio.ora)."
            default: return "Prossima visita tra \(giorni) giorni."
            }
        case .quiete: return "Tutto a posto, \(nome)."
        }
    }

    private func sottotitolo(_ momento: Momento, _ dati: DatiOggi) -> String {
        switch momento {
        case .feedback: "Risposta al tuo check della settimana."
        case .check: "Bastano due minuti. Una domanda alla volta."
        case .messaggi: "Dal Dr. Bo, nella tua chat."
        case .visita(let visita): "\(visita.tipo.etichetta), \(visita.inizio.giorno.esteso)."
        case .quiete: "Il check di questa settimana è fatto. Il Dr. Bo lo leggerà presto."
        }
    }

    @ViewBuilder
    private func azione(_ momento: Momento, _ dati: DatiOggi) -> some View {
        switch momento {
        case .feedback(let check):
            Button(check.feedbackAudioId != nil ? "Ascolta" : "Leggi") { feedback = check }
                .buttonStyle(BottoneEmber())
                .accessibilityIdentifier("oggi.azione")
        case .check:
            Button("Inizia il check") { compilazione = dati.check }
                .buttonStyle(BottoneEmber())
                .accessibilityIdentifier("oggi.azione")
        case .messaggi:
            Button("Apri la chat", action: vaiAllaChat)
                .buttonStyle(BottoneEmber())
        case .visita(let visita):
            if let meet = visita.meetUrl.flatMap(URL.init(string:)), visita.inizio.giorno == .oggi {
                Link("Entra nella visita", destination: meet)
                    .buttonStyle(BottoneEmber())
            } else {
                Button("Vedi il percorso", action: vaiAlPercorso)
                    .buttonStyle(BottoneVetro())
            }
        case .quiete:
            EmptyView()
        }
    }

    private func dettagli(_ dati: DatiOggi, _ attuale: Momento) -> some View {
        VStack(spacing: 0) {
            if let percorso = dati.percorso.percorso, percorso.stato == .attivo {
                Button(action: vaiAlPercorso) {
                    RigaOggi(etichetta: percorso.tipo.etichetta, valore: "\(percorso.visiteEffettuate) di \(percorso.visiteTotali) visite")
                }
                .buttonStyle(Premibile())
                Filetto()
            }
            if case .visita = attuale {} else if let visita = dati.prossimaVisita {
                Button(action: vaiAlPercorso) {
                    RigaOggi(etichetta: "Prossima visita", valore: visita.inizio.giornoOra)
                }
                .buttonStyle(Premibile())
                Filetto()
            }
            if dati.check.compilato, let check = dati.check.check, !check.haFeedback {
                Button {
                    compilazione = dati.check
                } label: {
                    RigaOggi(etichetta: "Check della settimana", valore: "Modifica")
                }
                .buttonStyle(Premibile())
                .accessibilityIdentifier("oggi.modifica")
                Filetto()
            } else if let check = dati.check.check, check.haFeedback, !check.feedbackDaLeggere {
                Button {
                    feedback = check
                } label: {
                    RigaOggi(etichetta: "Risposta del Dr. Bo", valore: "Rileggi")
                }
                .buttonStyle(Premibile())
                .accessibilityIdentifier("oggi.rileggi")
                Filetto()
            }
            NavigationLink(value: RottaPaziente.storico) {
                RigaOggi(etichetta: "I tuoi check", valore: "Storico")
            }
            .buttonStyle(Premibile())
        }
    }
}

enum RottaPaziente: Hashable {
    case profilo, storico
}

private struct RigaOggi: View {
    let etichetta: String
    let valore: String

    var body: some View {
        HStack {
            Text(etichetta)
                .font(Tipografia.body)
                .foregroundStyle(Tema.ink)
            Spacer()
            Text(valore)
                .font(Tipografia.nota)
                .foregroundStyle(Tema.inkSecondario)
            Image(systemName: "chevron.right")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Tema.inkTerziario)
        }
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }
}

struct ProfiloView: View {
    @Environment(AppModel.self) private var app
    @State private var cambio = false
    @State private var conferma = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(app.utente?.nome ?? "")
                    .font(Tipografia.display(46))
                    .foregroundStyle(Tema.ink)
                    .padding(.bottom, 4)
                Text(app.utente?.username ?? "")
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.inkSecondario)
                    .padding(.bottom, 44)
                Button("Cambia password") { cambio = true }
                    .buttonStyle(BottoneVetro())
                    .padding(.bottom, 12)
                Button("Esci") { conferma = true }
                    .buttonStyle(BottoneVetro())
                    .accessibilityIdentifier("profilo.esci")
                Text("Montserrat © The Montserrat Project Authors, licenza SIL Open Font 1.1.")
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.inkTerziario)
                    .padding(.top, 44)
            }
            .padding(Tema.margine)
        }
        .telaEdo()
        .navigationTitle("Profilo")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $cambio) { CambioPasswordView() }
        .confirmationDialog("Vuoi uscire dall'app?", isPresented: $conferma, titleVisibility: .visible) {
            Button("Esci", role: .destructive) { Task { await app.esci() } }
        }
    }
}

struct CambioPasswordView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @State private var attuale = ""
    @State private var nuova = ""
    @State private var inCorso = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("Password attuale", text: $attuale)
                        .textContentType(.password)
                    SecureField("Nuova password", text: $nuova)
                        .textContentType(.newPassword)
                } footer: {
                    Text("Almeno 10 caratteri. Dopo il cambio dovrai accedere di nuovo su tutti i telefoni.")
                }
            }
            .moduloEdo()
            .telaEdo()
            .navigationTitle("Cambia password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annulla") { chiudi() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salva") {
                        inCorso = true
                        Task {
                            await app.esegui { try await app.cambiaPassword(attuale: attuale, nuova: nuova) }
                            inCorso = false
                        }
                    }
                    .disabled(attuale.isEmpty || nuova.count < 10 || inCorso)
                }
            }
        }
    }
}
