import SwiftUI

struct AvvisoView: View {
    let testo: String

    var body: some View {
        Text(testo)
            .font(Tipografia.label)
            .foregroundStyle(Tema.ink)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 20)
            .padding(.vertical, 13)
            .vetro(raggio: 22)
            .padding(.horizontal, Tema.margine)
            .accessibilityAddTraits(.isStaticText)
            .onAppear { UIAccessibility.post(notification: .announcement, argument: testo) }
    }
}

/// Scheletri, non spinner: la forma della schermata arriva prima dei dati.
struct Scheletro: View {
    var righe = 6
    @State private var acceso = false
    @Environment(\.accessibilityReduceMotion) private var riduci

    var body: some View {
        VStack(alignment: .leading, spacing: 28) {
            forma(larghezza: 130, altezza: 10)
            forma(larghezza: 250, altezza: 40)
            VStack(spacing: 22) {
                ForEach(0..<righe, id: \.self) { indice in
                    VStack(alignment: .leading, spacing: 9) {
                        forma(larghezza: indice % 2 == 0 ? 220 : 170, altezza: 14)
                        forma(larghezza: 120, altezza: 10)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(Tema.margine)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .opacity(acceso ? 1 : 0.45)
        .onAppear {
            guard !riduci else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { acceso = true }
        }
        .accessibilityElement()
        .accessibilityLabel("Caricamento")
    }

    private func forma(larghezza: CGFloat, altezza: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: altezza / 2, style: .continuous)
            .fill(Tema.hairline)
            .frame(width: larghezza, height: altezza)
    }
}

struct StatoVuoto: View {
    let titolo: String
    var testo: String?
    var icona: String = "circle.dotted"
    var azione: (titolo: String, esegui: () -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: icona)
                .font(.system(size: 30, weight: .light))
                .foregroundStyle(Tema.ember)
                .accessibilityHidden(true)
            Text(titolo)
                .font(Tipografia.headline)
                .foregroundStyle(Tema.ink)
            if let testo {
                Text(testo)
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.inkSecondario)
            }
            if let azione {
                Button(azione.titolo, action: azione.esegui)
                    .buttonStyle(BottoneVetro(larghezzaPiena: false))
                    .padding(.top, 6)
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 36)
        .padding(.vertical, 60)
    }
}

/// Carica un valore e gestisce da solo scheletro, errore, offline e riprova.
/// Riapparendo ricarica in silenzio, così tornando da un dettaglio i dati sono già aggiornati.
struct Caricamento<Valore, Contenuto: View>: View {
    let chiave: AnyHashable
    let carica: () async throws -> Valore
    @ViewBuilder let contenuto: (Valore, @escaping () async -> Void) -> Contenuto

    @State private var valore: Valore?
    @State private var errore: Error?

    init(chiave: AnyHashable = 0, carica: @escaping () async throws -> Valore,
         @ViewBuilder contenuto: @escaping (Valore, @escaping () async -> Void) -> Contenuto) {
        self.chiave = chiave
        self.carica = carica
        self.contenuto = contenuto
    }

    var body: some View {
        Group {
            if let valore {
                contenuto(valore, aggiorna)
            } else if let errore {
                StatoVuoto(titolo: (errore as? ErroreAPI) == .offline ? "Sei offline" : "Non è stato possibile caricare",
                           testo: errore.localizedDescription,
                           icona: (errore as? ErroreAPI) == .offline ? "wifi.slash" : "exclamationmark.circle",
                           azione: ("Riprova", { Task { await aggiorna() } }))
                    .frame(maxHeight: .infinity)
            } else {
                Scheletro()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task(id: chiave) { await aggiorna() }
    }

    private func aggiorna() async {
        do {
            let nuovo = try await carica()
            withAnimation(.easeOut(duration: 0.25)) {
                valore = nuovo
                errore = nil
            }
        } catch {
            guard !AppModel.annullato(error) else { return }
            errore = error
            // Con dati già a schermo restano quelli: meglio vecchi che un muro di errore
            if valore != nil, (error as? ErroreAPI) == .offline { errore = nil }
        }
    }
}

/// Riga di dati etichetta e valore.
struct RigaDato: View {
    let etichetta: String
    let valore: String?

    var body: some View {
        if let valore, !valore.isEmpty {
            HStack(alignment: .firstTextBaseline, spacing: 16) {
                Text(etichetta)
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.inkSecondario)
                Spacer(minLength: 8)
                Text(valore)
                    .font(Tipografia.bodyMedio)
                    .foregroundStyle(Tema.ink)
                    .multilineTextAlignment(.trailing)
            }
            .padding(.vertical, 12)
            .accessibilityElement(children: .combine)
        }
    }
}

struct Filetto: View {
    var body: some View {
        Rectangle().fill(Tema.hairline).frame(height: 0.5)
    }
}

/// Numero eroico con didascalia.
struct NumeroEroe: View {
    let valore: String
    let didascalia: String
    var dimensione: CGFloat = 34
    var colore: Color = Tema.ink

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(valore)
                .font(Tipografia.display(dimensione))
                .foregroundStyle(colore)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            Text(didascalia)
                .font(Tipografia.nota)
                .foregroundStyle(Tema.inkSecondario)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

extension View {
    /// Liste native su canvas: righe separate da filetti, senza card.
    func listaEdo() -> some View {
        listStyle(.plain)
            .scrollContentBackground(.hidden)
    }

    func rigaEdo() -> some View {
        listRowBackground(Color.clear)
            .listRowSeparatorTint(Tema.hairline)
            .listRowInsets(EdgeInsets(top: 12, leading: Tema.margine, bottom: 12, trailing: Tema.margine))
    }

    func moduloEdo() -> some View {
        scrollContentBackground(.hidden)
            .font(Tipografia.body)
    }
}

struct Avatar: View {
    let nome: String
    var dimensione: CGFloat = 42

    private var iniziali: String {
        nome.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }

    var body: some View {
        Text(iniziali)
            .font(.custom("Montserrat-SemiBold", size: dimensione * 0.34))
            .foregroundStyle(Tema.ink)
            .frame(width: dimensione, height: dimensione)
            .background(Tema.hairline, in: Ellipse())
            .accessibilityHidden(true)
    }
}
