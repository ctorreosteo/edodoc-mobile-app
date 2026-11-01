import SwiftUI

struct PazienteShell: View {
    @Environment(AppModel.self) private var app
    @Environment(\.scenePhase) private var fase
    @State private var scheda = 0
    @State private var nonLetti = 0
    @State private var feedbackAperto: CheckDettaglio?

    var body: some View {
        TabView(selection: $scheda) {
            NavigationStack { OggiView(vaiAllaChat: { scheda = 2 }, vaiAlPercorso: { scheda = 3 }) }
                .tabItem { Label("Oggi", systemImage: "sun.horizon") }
                .tag(0)
            NavigationStack { CorsiView() }
                .tabItem { Label("Corsi", systemImage: "play.rectangle") }
                .tag(1)
            NavigationStack { ChatView(canale: .paziente, titolo: "Dr. Bo") }
                .tabItem { Label("Chat", systemImage: "bubble.left") }
                .badge(nonLetti)
                .tag(2)
            NavigationStack { PercorsoView() }
                .tabItem { Label("Percorso", systemImage: "point.topleft.down.to.point.bottomright.curvepath") }
                .tag(3)
        }
        .task(id: fase) {
            guard fase == .active else { return }
            await aggiornaContatori()
        }
        .onChange(of: scheda) { _, nuova in
            if nuova == 2 { nonLetti = 0 }
        }
        .onChange(of: app.destinazione) { _, _ in segui() }
        .onAppear { segui() }
        .sheet(item: $feedbackAperto) { check in
            NavigationStack { FeedbackView(check: check) }
        }
    }

    private func aggiornaContatori() async {
        if let riepilogo: RiepilogoApp = try? await app.api.get("/app/riepilogo") {
            nonLetti = scheda == 2 ? 0 : riepilogo.messaggiDaLeggere
        }
    }

    private func segui() {
        guard let destinazione = app.destinazione else { return }
        app.destinazione = nil
        switch destinazione {
        case .chat:
            scheda = 2
        case .feedback(let checkId):
            scheda = 0
            Task {
                let storico: [CheckDettaglio]? = try? await app.api.get("/app/check")
                feedbackAperto = storico?.first { $0.id == checkId } ?? storico?.first { $0.feedbackDaLeggere }
            }
        }
    }
}
