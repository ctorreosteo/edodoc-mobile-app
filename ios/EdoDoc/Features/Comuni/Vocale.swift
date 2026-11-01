import AVFoundation
import Observation
import SwiftUI

enum FormaOnda {
    /// Campioni reali del file, ridotti a poche barre normalizzate.
    static func barre(file: URL, quante: Int) throws -> [Float] {
        let audio = try AVAudioFile(forReading: file)
        let formato = audio.processingFormat
        let totale = AVAudioFrameCount(audio.length)
        guard totale > 0, let buffer = AVAudioPCMBuffer(pcmFormat: formato, frameCapacity: totale) else { return [] }
        try audio.read(into: buffer)
        guard let canale = buffer.floatChannelData?[0] else { return [] }
        let campioni = Int(buffer.frameLength)
        let perBarra = max(1, campioni / quante)
        var risultato: [Float] = []
        risultato.reserveCapacity(quante)
        var inizio = 0
        while inizio < campioni && risultato.count < quante {
            let fine = min(inizio + perBarra, campioni)
            var somma: Float = 0
            for i in inizio..<fine { somma += canale[i] * canale[i] }
            risultato.append(sqrt(somma / Float(fine - inizio)))
            inizio = fine
        }
        let massimo = risultato.max() ?? 1
        guard massimo > 0 else { return risultato }
        return risultato.map { pow($0 / massimo, 0.7) }
    }
}

@MainActor
@Observable
final class LettoreModel: NSObject, AVAudioPlayerDelegate {
    enum Fase: Equatable { case caricamento, pronto, errore(String) }

    private(set) var fase = Fase.caricamento
    private(set) var barre: [Float] = []
    private(set) var durata: Double = 0
    private(set) var posizione: Double = 0
    private(set) var suona = false
    private(set) var velocita: Float = 1
    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private var cursore: Task<Void, Never>?
    @ObservationIgnored private var file: URL?

    func prepara(api: APIClient, percorso: String) async {
        fase = .caricamento
        do {
            let scaricato = try await api.scarica(percorso, nomeFile: "feedback.m4a")
            file = scaricato
            try await apri(scaricato)
        } catch {
            if !AppModel.annullato(error) { fase = .errore(error.localizedDescription) }
        }
    }

    /// Riascolto di una registrazione non ancora inviata.
    func prepara(locale: URL) async {
        fase = .caricamento
        do {
            try await apri(locale)
        } catch {
            fase = .errore("Non riesco ad aprire la registrazione.")
        }
    }

    private func apri(_ origine: URL) async throws {
        let barre = try await Task.detached(priority: .userInitiated) {
            try FormaOnda.barre(file: origine, quante: 56)
        }.value
        let player = try AVAudioPlayer(contentsOf: origine)
        player.enableRate = true
        player.delegate = self
        player.prepareToPlay()
        self.player = player
        self.barre = barre
        durata = player.duration
        fase = .pronto
    }

    func alterna() {
        guard let player else { return }
        if suona {
            player.pause()
            suona = false
            cursore?.cancel()
        } else {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try? AVAudioSession.sharedInstance().setActive(true)
            player.rate = velocita
            player.play()
            suona = true
            seguiCursore()
        }
        Tocco.leggero()
    }

    func vai(a frazione: Double) {
        guard let player else { return }
        player.currentTime = min(max(frazione, 0), 1) * durata
        posizione = player.currentTime
    }

    func cambiaVelocita() {
        velocita = velocita == 1 ? 1.5 : velocita == 1.5 ? 2 : 1
        player?.rate = velocita
    }

    func ferma() {
        player?.stop()
        suona = false
        cursore?.cancel()
        // Sono dati sanitari: il file temporaneo non resta sul telefono
        if let file { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) }
        file = nil
    }

    private func seguiCursore() {
        cursore?.cancel()
        cursore = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, let player = self.player else { return }
                self.posizione = player.currentTime
                try? await Task.sleep(for: .milliseconds(33))
            }
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.suona = false
            self.cursore?.cancel()
            self.posizione = 0
            player.currentTime = 0
        }
    }
}

/// La forma d'onda è l'interfaccia: si trascina per scorrere, la parte ascoltata si accende di brace.
struct LettoreVocale: View {
    var percorso: String?
    var locale: URL?
    @Environment(AppModel.self) private var app
    @State private var modello = LettoreModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            switch modello.fase {
            case .caricamento:
                onda(barre: Array(repeating: 0.12, count: 56), frazione: 0)
                    .redacted(reason: .placeholder)
            case .errore(let messaggio):
                HStack {
                    Text(messaggio).font(Tipografia.nota).foregroundStyle(Tema.critico)
                    Spacer()
                    Button("Riprova") { Task { await carica() } }
                        .font(Tipografia.label)
                }
            case .pronto:
                let frazione = modello.durata > 0 ? modello.posizione / modello.durata : 0
                onda(barre: modello.barre, frazione: frazione)
                HStack(spacing: 16) {
                    Button(action: modello.alterna) {
                        Image(systemName: modello.suona ? "pause.fill" : "play.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(Tema.suEmber)
                            .frame(width: 56, height: 56)
                            .background(Tema.ember, in: Circle())
                            .contentTransition(.symbolEffect(.replace))
                    }
                    .accessibilityLabel(modello.suona ? "Pausa" : "Ascolta")
                    .accessibilityIdentifier("vocale.play")
                    Text("\(Formati.cronometro(modello.posizione)) / \(Formati.cronometro(modello.durata))")
                        .font(Tipografia.label)
                        .monospacedDigit()
                        .foregroundStyle(Tema.inkSecondario)
                    Spacer()
                    Button(action: modello.cambiaVelocita) {
                        Text(modello.velocita == 1 ? "1×" : modello.velocita == 1.5 ? "1,5×" : "2×")
                            .font(Tipografia.label)
                            .monospacedDigit()
                            .foregroundStyle(Tema.ink)
                            .frame(minWidth: 48, minHeight: 44)
                    }
                    .accessibilityLabel("Velocità di riproduzione")
                }
            }
        }
        .task(id: percorso ?? locale?.path) { await carica() }
        .onDisappear { modello.ferma() }
    }

    private func carica() async {
        if let locale {
            await modello.prepara(locale: locale)
        } else if let percorso {
            await modello.prepara(api: app.api, percorso: percorso)
        }
    }

    private func onda(barre: [Float], frazione: Double) -> some View {
        GeometryReader { spazio in
            Canvas { contesto, dimensioni in
                guard !barre.isEmpty else { return }
                let passo = dimensioni.width / CGFloat(barre.count)
                let larghezza = max(2, passo * 0.56)
                for (indice, valore) in barre.enumerated() {
                    let altezza = max(4, CGFloat(valore) * dimensioni.height)
                    let rettangolo = CGRect(x: CGFloat(indice) * passo, y: (dimensioni.height - altezza) / 2,
                                            width: larghezza, height: altezza)
                    let ascoltata = Double(indice) / Double(barre.count) < frazione
                    contesto.fill(Path(roundedRect: rettangolo, cornerRadius: larghezza / 2),
                                  with: .color(ascoltata ? Tema.ember : Tema.inkTerziario))
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { tocco in
                modello.vai(a: tocco.location.x / spazio.size.width)
            })
        }
        .frame(height: 84)
        .accessibilityElement()
        .accessibilityLabel("Forma d'onda del messaggio vocale")
        .accessibilityValue("\(Int(frazione * 100)) per cento")
        .accessibilityAdjustableAction { direzione in
            let passo = direzione == .increment ? 0.1 : -0.1
            modello.vai(a: frazione + passo)
        }
    }
}
