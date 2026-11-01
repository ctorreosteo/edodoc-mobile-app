import SwiftUI

/// Sostituisce tutta l'app finché non si installa la versione nuova: non ha modo di essere chiusa.
struct AggiornamentoView: View {
    let aggiornamento: Aggiornamento
    @Environment(\.openURL) private var apri

    var body: some View {
        ZStack {
            Brace(fuoco: .init(x: 0.5, y: 0.3), intensita: 1.1)
            VStack(alignment: .leading, spacing: 0) {
                Image("Logo")
                    .resizable()
                    .renderingMode(.template)
                    .scaledToFit()
                    .frame(height: 30)
                    .foregroundStyle(Tema.ember)
                    .accessibilityLabel("EdoDoc")
                Spacer(minLength: 40)
                Text("Aggiorna l'app.")
                    .font(Tipografia.display(44))
                    .foregroundStyle(Tema.ink)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                    .padding(.bottom, 8)
                Text("È disponibile la versione \(aggiornamento.versione). Per continuare a usare EdoDoc devi installarla.")
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.inkSecondario)
                    .padding(.bottom, 36)
                Button("Aggiorna") { apri(aggiornamento.url) }
                    .buttonStyle(BottoneEmber())
                    .accessibilityIdentifier("aggiornamento.apri")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Tema.margine)
        }
    }
}
