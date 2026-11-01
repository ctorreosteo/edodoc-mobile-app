import SwiftUI
import UIKit

extension UIColor {
    convenience init(esadecimale: UInt32, alfa: CGFloat = 1) {
        self.init(red: CGFloat(esadecimale >> 16 & 0xFF) / 255, green: CGFloat(esadecimale >> 8 & 0xFF) / 255,
                  blue: CGFloat(esadecimale & 0xFF) / 255, alpha: alfa)
    }
}

extension Color {
    init(chiaro: UIColor, scuro: UIColor) {
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? scuro : chiaro })
    }

    init(_ chiaro: UInt32, _ scuro: UInt32) {
        self.init(chiaro: UIColor(esadecimale: chiaro), scuro: UIColor(esadecimale: scuro))
    }
}

/// Token di docs/DESIGN.md.
enum Tema {
    static let canvas = Color(0xF6F3EE, 0x080F1C)
    static let canvasRialzato = Color(0xFFFFFF, 0x101B2E)
    static let ink = Color(0x0F1C31, 0xF4F1EC)
    static let inkSecondario = Color(chiaro: UIColor(esadecimale: 0x0F1C31, alfa: 0.62), scuro: UIColor(esadecimale: 0xF4F1EC, alfa: 0.62))
    static let inkTerziario = Color(chiaro: UIColor(esadecimale: 0x0F1C31, alfa: 0.42), scuro: UIColor(esadecimale: 0xF4F1EC, alfa: 0.38))
    static let hairline = Color(chiaro: UIColor(esadecimale: 0x0F1C31, alfa: 0.09), scuro: UIColor(white: 1, alpha: 0.09))
    static let brand = Color(0x1F3A63, 0x1F3A63)
    static let ember = Color(0xE8701F, 0xFF8F3D)
    static let emberSoft = Color(0xF0944E, 0xFFC48F)
    static let suEmber = Color(0xFFFFFF, 0x080F1C)
    static let positivo = Color(0x1E8A62, 0x7FD8B0)
    static let attenzione = Color(0xB97A00, 0xFFC85C)
    static let critico = Color(0xC93A30, 0xFF7A70)

    static let raggioPannello: CGFloat = 28
    static let raggioControllo: CGFloat = 18
    static let margine: CGFloat = 22
}

enum Tipografia {
    private static func font(_ nome: String, _ dimensione: CGFloat, _ stile: Font.TextStyle) -> Font {
        .custom("Montserrat-\(nome)", size: dimensione, relativeTo: stile)
    }

    static func display(_ dimensione: CGFloat = 60) -> Font { font("Light", dimensione, .largeTitle) }
    static let title = font("SemiBold", 26, .title)
    static let headline = font("SemiBold", 18, .headline)
    static let body = font("Regular", 16, .body)
    static let bodyMedio = font("Medium", 16, .body)
    static let label = font("SemiBold", 13, .footnote)
    static let nota = font("Regular", 13, .footnote)
    static let overline = font("Bold", 11, .caption2)

    static func configuraBarre() {
        let barra = UINavigationBar.appearance()
        if let grande = UIFont(name: "Montserrat-SemiBold", size: 32) {
            barra.largeTitleTextAttributes = [.font: UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: grande)]
        }
        if let piccolo = UIFont(name: "Montserrat-SemiBold", size: 16) {
            barra.titleTextAttributes = [.font: UIFontMetrics(forTextStyle: .headline).scaledFont(for: piccolo)]
        }
        if let tab = UIFont(name: "Montserrat-SemiBold", size: 10) {
            let attributi: [NSAttributedString.Key: Any] = [.font: tab]
            UITabBarItem.appearance().setTitleTextAttributes(attributi, for: .normal)
        }
    }
}

enum Tocco {
    static func leggero() { UISelectionFeedbackGenerator().selectionChanged() }
    static func conferma() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func errore() { UINotificationFeedbackGenerator().notificationOccurred(.error) }
    static func impatto() { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
}

extension Animation {
    static let edo = Animation.spring(response: 0.42, dampingFraction: 0.82)
}

struct Overline: View {
    let testo: String
    var colore: Color = Tema.inkTerziario

    init(_ testo: String, colore: Color = Tema.inkTerziario) {
        self.testo = testo
        self.colore = colore
    }

    var body: some View {
        Text(testo.uppercased(with: Roma.locale))
            .font(Tipografia.overline)
            .tracking(1.8)
            .foregroundStyle(colore)
            .accessibilityAddTraits(.isHeader)
    }
}

// MARK: Vetro

struct Vetro: ViewModifier {
    var raggio: CGFloat
    var interattivo: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.glassEffect(interattivo ? .regular.interactive() : .regular, in: .rect(cornerRadius: raggio))
        } else {
            content
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: raggio, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: raggio, style: .continuous)
                        .strokeBorder(Tema.hairline, lineWidth: 0.5)
                }
        }
    }
}

extension View {
    func vetro(raggio: CGFloat = Tema.raggioPannello, interattivo: Bool = false) -> some View {
        modifier(Vetro(raggio: raggio, interattivo: interattivo))
    }
}

// MARK: Bottoni

struct BottoneEmber: ButtonStyle {
    @Environment(\.isEnabled) private var attivo
    var larghezzaPiena = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Tipografia.bodyMedio.weight(.semibold))
            .foregroundStyle(Tema.suEmber)
            .frame(maxWidth: larghezzaPiena ? .infinity : nil, minHeight: 54)
            .padding(.horizontal, 26)
            .background(Tema.ember.opacity(attivo ? 1 : 0.35), in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.965 : 1)
            .animation(.edo, value: configuration.isPressed)
    }
}

struct BottoneVetro: ButtonStyle {
    var larghezzaPiena = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Tipografia.bodyMedio)
            .foregroundStyle(Tema.ink)
            .frame(maxWidth: larghezzaPiena ? .infinity : nil, minHeight: 50)
            .padding(.horizontal, 22)
            .vetro(raggio: 25, interattivo: true)
            .scaleEffect(configuration.isPressed ? 0.965 : 1)
            .animation(.edo, value: configuration.isPressed)
    }
}

struct Premibile: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.6 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

// MARK: Forme della brand

/// Le ellissi della O del logo, usate come scala da 1 a 5.
struct ScalaEllissi: View {
    @Binding var valore: Int
    var etichette: [String]
    @Environment(\.accessibilityReduceMotion) private var riduci

    var body: some View {
        VStack(spacing: 22) {
            HStack(spacing: 12) {
                ForEach(1...5, id: \.self) { indice in
                    Button {
                        Tocco.leggero()
                        withAnimation(riduci ? .easeInOut(duration: 0.2) : .spring(response: 0.34, dampingFraction: 0.58)) {
                            valore = indice
                        }
                    } label: {
                        Ellipse()
                            .fill(indice <= valore ? AnyShapeStyle(Tema.ember) : AnyShapeStyle(Tema.hairline))
                            .overlay { Ellipse().strokeBorder(indice <= valore ? .clear : Tema.inkTerziario, lineWidth: 1) }
                            .frame(height: indice == valore ? 66 : 42)
                            .frame(height: 70)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(Premibile())
                    .accessibilityHidden(true)
                }
            }
            Text(valore > 0 ? etichette[valore - 1] : " ")
                .font(Tipografia.headline)
                .foregroundStyle(Tema.ink)
                .contentTransition(.opacity)
        }
        .accessibilityElement()
        .accessibilityLabel(valore > 0 ? "\(valore) su 5, \(etichette[valore - 1])" : "Nessuna scelta")
        .accessibilityAdjustableAction { direzione in
            switch direzione {
            case .increment: valore = min(5, valore + 1)
            case .decrement: valore = max(1, valore - 1)
            default: break
            }
        }
    }
}

struct ChipStato: View {
    let testo: String
    var colore: Color = Tema.inkSecondario

    var body: some View {
        Text(testo)
            .font(Tipografia.overline)
            .foregroundStyle(colore)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(colore.opacity(0.13), in: Capsule())
    }
}

extension StatoVisita {
    var colore: Color {
        switch self {
        case .effettuata: Tema.positivo
        case .programmata: Tema.ember
        case .nonPresentato: Tema.critico
        default: Tema.inkTerziario
        }
    }
}

extension StatoPagamento {
    var colore: Color {
        switch self {
        case .pagato: Tema.positivo
        case .daPagare: Tema.attenzione
        default: Tema.inkTerziario
        }
    }
}

extension StatoPaziente {
    var colore: Color {
        switch self {
        case .attivo: Tema.positivo
        case .mantenimento: Tema.ember
        case .lead, .listaAttesa: Tema.attenzione
        default: Tema.inkTerziario
        }
    }
}
