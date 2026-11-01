import Observation
import SwiftUI

struct CanaleChat: Hashable {
    let messaggi: String
    let letti: String
    let mio: Mittente

    static let paziente = CanaleChat(messaggi: "/app/messaggi", letti: "/app/messaggi/letti", mio: .paziente)
    static func dottore(pazienteId: Int) -> CanaleChat {
        CanaleChat(messaggi: "/chat/\(pazienteId)/messaggi", letti: "/chat/\(pazienteId)/letti", mio: .dottore)
    }
}

struct Bolla: Identifiable, Equatable {
    enum Stato { case inviato, inCorso, fallito }

    let id: String
    var serverId: Int?
    let clientId: UUID?
    let testo: String
    let mia: Bool
    let ora: OraLocale
    var stato: Stato
    var letta: Bool

    init(riga: RigaMessaggio, mio: Mittente) {
        id = riga.clientId?.uuidString ?? "s\(riga.id)"
        serverId = riga.id
        clientId = riga.clientId
        testo = riga.testo
        mia = riga.mittente == mio
        ora = riga.inviatoIl
        stato = .inviato
        letta = riga.lettoIl != nil
    }

    init(locale testo: String, clientId: UUID) {
        id = clientId.uuidString
        self.clientId = clientId
        self.testo = testo
        mia = true
        ora = OraLocale(Date())
        stato = .inCorso
        letta = false
    }
}

/// La chat non ha socket: a schermo aperto chiede i nuovi con dopoId ogni pochi secondi.
/// Il clientId resta lo stesso nei reinvii, così una rete caduta non crea doppioni.
@MainActor
@Observable
final class ChatModel {
    private(set) var bolle: [Bolla] = []
    private(set) var caricata = false
    private(set) var errore: String?
    private(set) var ciSonoPrecedenti = false
    private(set) var carichiPrecedenti = false

    private let api: APIClient
    private let canale: CanaleChat
    private static let pagina = 50

    init(api: APIClient, canale: CanaleChat) {
        self.api = api
        self.canale = canale
    }

    private var ultimoId: Int? { bolle.compactMap(\.serverId).max() }
    private var primoId: Int? { bolle.compactMap(\.serverId).min() }

    func apri() async {
        do {
            let righe: [RigaMessaggio] = try await api.get(canale.messaggi)
            unisci(righe)
            ciSonoPrecedenti = righe.count >= Self.pagina
            caricata = true
            errore = nil
            await segnaLetti()
        } catch {
            if !AppModel.annullato(error) { errore = error.localizedDescription }
        }
    }

    func ascolta() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            guard caricata else {
                await apri()
                continue
            }
            await nuovi()
        }
    }

    private func nuovi() async {
        guard let dopo = ultimoId else {
            await apri()
            return
        }
        guard let righe: [RigaMessaggio] = try? await api.get(canale.messaggi, [URLQueryItem(name: "dopoId", value: "\(dopo)")]),
              !righe.isEmpty else { return }
        unisci(righe)
        if righe.contains(where: { $0.mittente != canale.mio }) {
            Tocco.leggero()
            await segnaLetti()
        }
    }

    func precedenti() async -> String? {
        guard let primoId, !carichiPrecedenti else { return nil }
        let ancora = bolle.first?.id
        carichiPrecedenti = true
        defer { carichiPrecedenti = false }
        guard let righe: [RigaMessaggio] = try? await api.get(canale.messaggi, [URLQueryItem(name: "primaDiId", value: "\(primoId)")]) else {
            return nil
        }
        ciSonoPrecedenti = righe.count >= Self.pagina
        bolle.insert(contentsOf: righe.map { Bolla(riga: $0, mio: canale.mio) }, at: 0)
        return ancora
    }

    func invia(_ testo: String) async {
        let pulito = testo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !pulito.isEmpty else { return }
        let id = UUID()
        bolle.append(Bolla(locale: pulito, clientId: id))
        Tocco.leggero()
        await consegna(testo: pulito, clientId: id)
    }

    func riprova(_ bolla: Bolla) async {
        guard let clientId = bolla.clientId, let indice = bolle.firstIndex(where: { $0.id == bolla.id }) else { return }
        bolle[indice].stato = .inCorso
        await consegna(testo: bolla.testo, clientId: clientId)
    }

    private func consegna(testo: String, clientId: UUID) async {
        do {
            let riga: RigaMessaggio = try await api.post(canale.messaggi, Invio(testo: testo, clientId: clientId))
            unisci([riga])
        } catch {
            if let indice = bolle.firstIndex(where: { $0.clientId == clientId }) {
                bolle[indice].stato = .fallito
                Tocco.errore()
            }
        }
    }

    private func unisci(_ righe: [RigaMessaggio]) {
        for riga in righe {
            let bolla = Bolla(riga: riga, mio: canale.mio)
            if let indice = bolle.firstIndex(where: { $0.id == bolla.id || $0.serverId == riga.id }) {
                bolle[indice].serverId = riga.id
                bolle[indice].stato = .inviato
                bolle[indice].letta = bolla.letta
            } else {
                // Ordine del server: i messaggi in attesa restano in fondo
                let primoInAttesa = bolle.firstIndex { $0.serverId == nil } ?? bolle.endIndex
                bolle.insert(bolla, at: primoInAttesa)
            }
        }
    }

    private func segnaLetti() async {
        let _: Vuoto? = try? await api.post(canale.letti)
    }
}

struct ChatView: View {
    let canale: CanaleChat
    var titolo = "Chat"
    @Environment(AppModel.self) private var app
    @State private var modello: ChatModel?
    @State private var testo = ""
    @FocusState private var scrivendo: Bool

    var body: some View {
        Group {
            if let modello {
                contenuto(modello)
            } else {
                Scheletro()
            }
        }
        .telaEdo(fuoco: .init(x: 0.5, y: 1.0), intensita: 0.38)
        .navigationTitle(titolo)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: canale) {
            let nuovo = ChatModel(api: app.api, canale: canale)
            modello = nuovo
            await nuovo.apri()
            await nuovo.ascolta()
        }
    }

    @ViewBuilder
    private func contenuto(_ modello: ChatModel) -> some View {
        ScrollViewReader { lettore in
            ScrollView {
                LazyVStack(spacing: 6) {
                    if modello.ciSonoPrecedenti {
                        Button {
                            Task {
                                if let ancora = await modello.precedenti() {
                                    lettore.scrollTo(ancora, anchor: .top)
                                }
                            }
                        } label: {
                            Text(modello.carichiPrecedenti ? "Caricamento…" : "Messaggi precedenti")
                                .font(Tipografia.label)
                                .foregroundStyle(Tema.inkSecondario)
                                .frame(minHeight: 44)
                        }
                    }
                    if !modello.caricata, let errore = modello.errore {
                        StatoVuoto(titolo: "Chat non disponibile", testo: errore, icona: "wifi.slash",
                                   azione: ("Riprova", { Task { await modello.apri() } }))
                    } else if modello.caricata && modello.bolle.isEmpty {
                        StatoVuoto(titolo: "Nessun messaggio", testo: canale.mio == .paziente
                                   ? "Scrivi al Dr. Bo quando vuoi: ti risponderà qui."
                                   : "Inizia tu la conversazione.", icona: "bubble.left")
                    }
                    ForEach(Array(modello.bolle.enumerated()), id: \.element.id) { indice, bolla in
                        if indice == 0 || modello.bolle[indice - 1].ora.giorno != bolla.ora.giorno {
                            Overline(bolla.ora.giorno.esteso)
                                .padding(.vertical, 16)
                        }
                        BollaView(bolla: bolla, ultima: indice == modello.bolle.count - 1) {
                            Task { await modello.riprova(bolla) }
                        }
                        .id(bolla.id)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)
            }
            .defaultScrollAnchor(.bottom)
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: modello.bolle.last?.id) { _, id in
                guard let id else { return }
                withAnimation(.edo) { lettore.scrollTo(id, anchor: .bottom) }
            }
            .safeAreaInset(edge: .bottom) {
                compositore(modello)
            }
        }
    }

    private func compositore(_ modello: ChatModel) -> some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField("Scrivi un messaggio", text: $testo, axis: .vertical)
                .font(Tipografia.body)
                .lineLimit(1...5)
                .focused($scrivendo)
                .padding(.horizontal, 18)
                .padding(.vertical, 13)
                .vetro(raggio: 24, interattivo: true)
                .accessibilityIdentifier("chat.testo")
            let vuoto = testo.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            Button {
                let daInviare = testo
                testo = ""
                Task { await modello.invia(daInviare) }
            } label: {
                Image(systemName: "arrow.up")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Tema.suEmber)
                    .frame(width: 48, height: 48)
                    .background(Tema.ember.opacity(vuoto ? 0.3 : 1), in: Circle())
            }
            .disabled(vuoto || String(testo.prefix(4001)).count > 4000)
            .accessibilityLabel("Invia")
            .accessibilityIdentifier("chat.invia")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }
}

private struct BollaView: View {
    let bolla: Bolla
    let ultima: Bool
    let riprova: () -> Void

    var body: some View {
        VStack(alignment: bolla.mia ? .trailing : .leading, spacing: 4) {
            Text(bolla.testo)
                .font(Tipografia.body)
                .foregroundStyle(bolla.mia ? Tema.suEmber : Tema.ink)
                .textSelection(.enabled)
                .padding(.horizontal, 15)
                .padding(.vertical, 10)
                .background {
                    if bolla.mia {
                        RoundedRectangle(cornerRadius: 21, style: .continuous).fill(Tema.ember)
                    } else {
                        RoundedRectangle(cornerRadius: 21, style: .continuous).fill(Tema.canvasRialzato.opacity(0.9))
                    }
                }
                .opacity(bolla.stato == .inCorso ? 0.6 : 1)
            piede
        }
        .frame(maxWidth: .infinity, alignment: bolla.mia ? .trailing : .leading)
        .padding(bolla.mia ? .leading : .trailing, 48)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(bolla.mia ? "Tu" : "Messaggio ricevuto"), \(bolla.ora.ora): \(bolla.testo)")
        .accessibilityHint(bolla.stato == .fallito ? "Non inviato, tocca per riprovare" : "")
    }

    @ViewBuilder
    private var piede: some View {
        switch bolla.stato {
        case .fallito:
            Button(action: riprova) {
                Label("Non inviato. Riprova", systemImage: "arrow.clockwise")
                    .font(Tipografia.overline)
                    .foregroundStyle(Tema.critico)
                    .frame(minHeight: 30)
            }
        case .inCorso:
            EmptyView()
        case .inviato:
            if ultima {
                Text(bolla.mia && bolla.letta ? "Letto · \(bolla.ora.ora)" : bolla.ora.ora)
                    .font(Tipografia.overline)
                    .foregroundStyle(Tema.inkTerziario)
            }
        }
    }
}
