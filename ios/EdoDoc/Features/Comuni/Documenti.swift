import QuickLook
import SwiftUI

/// Scarica un PDF con Bearer e lo apre in anteprima: da lì si stampa o si condivide.
struct BottonePDF<Etichetta: View>: View {
    let percorso: String
    let nomeFile: String
    @ViewBuilder let etichetta: () -> Etichetta
    @Environment(AppModel.self) private var app
    @State private var inCorso = false
    @State private var anteprima: URL?

    var body: some View {
        Button {
            Task { await apri() }
        } label: {
            if inCorso {
                ProgressView().frame(minWidth: 44, minHeight: 44)
            } else {
                etichetta()
            }
        }
        .buttonStyle(Premibile())
        .disabled(inCorso)
        .quickLookPreview($anteprima)
    }

    private func apri() async {
        inCorso = true
        defer { inCorso = false }
        await app.esegui {
            anteprima = try await app.api.scarica(percorso, nomeFile: nomeFile)
        }
    }
}

/// Testo Markdown delle lezioni: titoli, elenchi, citazioni e paragrafi con enfasi e link.
struct TestoMarkdown: View {
    let sorgente: String

    private enum Blocco: Identifiable {
        case titolo(Int, String), elenco(String), citazione(String), paragrafo(String)
        var id: String { "\(self)" }
    }

    private var blocchi: [Blocco] {
        var risultato: [Blocco] = []
        var paragrafo: [String] = []
        func chiudi() {
            if !paragrafo.isEmpty { risultato.append(.paragrafo(paragrafo.joined(separator: " "))) }
            paragrafo = []
        }
        for riga in sorgente.components(separatedBy: .newlines) {
            let t = riga.trimmingCharacters(in: .whitespaces)
            if t.isEmpty {
                chiudi()
            } else if t.hasPrefix("#") {
                chiudi()
                let livello = t.prefix { $0 == "#" }.count
                risultato.append(.titolo(livello, t.dropFirst(livello).trimmingCharacters(in: .whitespaces)))
            } else if t.hasPrefix("- ") || t.hasPrefix("* ") {
                chiudi()
                risultato.append(.elenco(String(t.dropFirst(2))))
            } else if t.hasPrefix("> ") {
                chiudi()
                risultato.append(.citazione(String(t.dropFirst(2))))
            } else {
                paragrafo.append(t)
            }
        }
        chiudi()
        return risultato
    }

    private func inline(_ testo: String) -> Text {
        let opzioni = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        return Text((try? AttributedString(markdown: testo, options: opzioni)) ?? AttributedString(testo))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(blocchi.enumerated()), id: \.offset) { _, blocco in
                switch blocco {
                case .titolo(let livello, let testo):
                    inline(testo)
                        .font(livello == 1 ? Tipografia.title : Tipografia.headline)
                        .foregroundStyle(Tema.ink)
                        .padding(.top, 10)
                        .accessibilityAddTraits(.isHeader)
                case .elenco(let testo):
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Ellipse().fill(Tema.ember).frame(width: 7, height: 5).alignmentGuide(.firstTextBaseline) { $0[.bottom] + 2 }
                        inline(testo).font(Tipografia.body).foregroundStyle(Tema.ink)
                    }
                case .citazione(let testo):
                    inline(testo)
                        .font(Tipografia.body.italic())
                        .foregroundStyle(Tema.inkSecondario)
                        .padding(.leading, 14)
                        .overlay(alignment: .leading) { Rectangle().fill(Tema.ember).frame(width: 2) }
                case .paragrafo(let testo):
                    inline(testo)
                        .font(Tipografia.body)
                        .lineSpacing(5)
                        .foregroundStyle(Tema.ink)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Copertina del corso, con la geometria del logo quando non c'è un'immagine.
struct Copertina: View {
    let id: UUID?
    let seme: Int
    @Environment(AppModel.self) private var app

    var body: some View {
        ZStack {
            segnaposto
            if let id {
                AsyncImage(url: app.api.urlCopertina(id)) { immagine in
                    immagine.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
            }
        }
        .clipped()
    }

    private var segnaposto: some View {
        ZStack {
            LinearGradient(colors: [Tema.brand, Color(0x101B2E, 0x101B2E)], startPoint: .topLeading, endPoint: .bottomTrailing)
            GeometryReader { spazio in
                let d = spazio.size.width * 0.95
                Ellipse()
                    .fill(RadialGradient(colors: [Color(0xFF8F3D, 0xFF8F3D).opacity(0.85), Color(0xFF8F3D, 0xFF8F3D).opacity(0)],
                                         center: .center, startRadius: 0, endRadius: d * 0.6))
                    .frame(width: d, height: d * 0.72)
                    .offset(x: seme % 2 == 0 ? spazio.size.width * 0.32 : -spazio.size.width * 0.28, y: spazio.size.height * 0.08)
            }
        }
    }
}

extension View {
    @ViewBuilder
    func sorgenteZoom(_ id: some Hashable, in spazio: Namespace.ID) -> some View {
        if #available(iOS 18, *) {
            matchedTransitionSource(id: id, in: spazio)
        } else {
            self
        }
    }

    @ViewBuilder
    func destinazioneZoom(_ id: some Hashable, in spazio: Namespace.ID) -> some View {
        if #available(iOS 18, *) {
            navigationTransition(.zoom(sourceID: id, in: spazio))
        } else {
            self
        }
    }
}
