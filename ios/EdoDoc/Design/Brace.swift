import SwiftUI

/// La luce della brace: unica fonte calda dietro al contenuto. Respira in circa 9 secondi e si sposta verso `fuoco`.
struct Brace: View {
    var fuoco: UnitPoint = .init(x: 0.5, y: 0.08)
    var intensita: Double = 1
    var animata = true
    @Environment(\.accessibilityReduceMotion) private var riduci
    @Environment(\.colorScheme) private var schema

    var body: some View {
        Group {
            if animata && !riduci {
                TimelineView(.animation(minimumInterval: 1 / 24)) { contesto in
                    tela(tempo: contesto.date.timeIntervalSinceReferenceDate)
                }
            } else {
                tela(tempo: 0)
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func tela(tempo: Double) -> some View {
        let respiro = (sin(tempo * 2 * .pi / 9) + 1) / 2
        let deriva = sin(tempo * 2 * .pi / 23)
        if #available(iOS 18, *) {
            maglia(respiro: respiro, deriva: deriva)
        } else {
            radiale(respiro: respiro, deriva: deriva)
        }
    }

    private var scuro: Bool { schema == .dark }

    @available(iOS 18, *)
    private func maglia(respiro: Double, deriva: Double) -> some View {
        let x = Float(fuoco.x + deriva * 0.06)
        let y = Float(fuoco.y + respiro * 0.03)
        let centro = SIMD2<Float>(min(max(x, 0.1), 0.9), min(max(y, 0.04), 0.9))
        let forza = (scuro ? 0.72 : 0.5) * intensita * (0.82 + 0.18 * respiro)
        let fondo = Tema.canvas
        let blu = Tema.brand.opacity(scuro ? 0.5 : 0.08)
        return ZStack {
            Tema.canvas
            MeshGradient(
                width: 3, height: 3,
                points: [
                    [0, 0], [centro.x, 0], [1, 0],
                    [0, centro.y + 0.14], centro, [1, centro.y + 0.1],
                    [0, 1], [0.5 + Float(deriva) * 0.1, 1], [1, 1],
                ],
                colors: [
                    blu, Tema.ember.opacity(forza * 0.5), blu,
                    fondo, Tema.ember.opacity(forza), fondo,
                    fondo, blu, fondo,
                ],
                smoothsColors: true
            )
            .blur(radius: 30)
        }
    }

    private func radiale(respiro: Double, deriva: Double) -> some View {
        GeometryReader { spazio in
            ZStack {
                Tema.canvas
                RadialGradient(colors: [Tema.brand.opacity(scuro ? 0.55 : 0.1), .clear], center: .bottomTrailing,
                               startRadius: 0, endRadius: spazio.size.height * 0.8)
                RadialGradient(colors: [Tema.ember.opacity((scuro ? 0.5 : 0.34) * intensita * (0.82 + 0.18 * respiro)), .clear],
                               center: UnitPoint(x: fuoco.x + deriva * 0.06, y: fuoco.y),
                               startRadius: 0, endRadius: spazio.size.width * 0.95)
            }
        }
    }
}

extension View {
    /// Fondo statico per le schermate interne: la brace animata è riservata ai momenti firma.
    func telaEdo(fuoco: UnitPoint = .init(x: 0.82, y: 0.02), intensita: Double = 0.55, animata: Bool = false) -> some View {
        background { Brace(fuoco: fuoco, intensita: intensita, animata: animata) }
    }
}
