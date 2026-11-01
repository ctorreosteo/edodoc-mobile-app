import SwiftUI
import UIKit
import UserNotifications

final class Delegato: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var modello: AppModel?
    private var tokenInAttesa: Data?
    private var destinazioneInAttesa: Destinazione?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func collega(_ modello: AppModel) {
        self.modello = modello
        if let tokenInAttesa { modello.registra(pushToken: tokenInAttesa) }
        if let destinazioneInAttesa { modello.destinazione = destinazioneInAttesa }
        tokenInAttesa = nil
        destinazioneInAttesa = nil
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in
            if let modello { modello.registra(pushToken: deviceToken) } else { tokenInAttesa = deviceToken }
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .badge]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let destinazione = Destinazione(userInfo: response.notification.request.content.userInfo) else { return }
        await MainActor.run {
            if let modello { modello.destinazione = destinazione } else { destinazioneInAttesa = destinazione }
        }
    }
}

@main
struct EdoDocApp: App {
    @UIApplicationDelegateAdaptor(Delegato.self) private var delegato
    @State private var modello = AppModel()

    init() {
        Tipografia.configuraBarre()
    }

    var body: some Scene {
        WindowGroup {
            RadiceView()
                .environment(modello)
                .tint(Tema.ember)
                .onAppear { delegato.collega(modello) }
                .onOpenURL { modello.apri(url: $0) }
        }
    }
}

struct RadiceView: View {
    @Environment(AppModel.self) private var modello

    var body: some View {
        ZStack(alignment: .top) {
            switch modello.stato {
            case .anonimo:
                AccessoView().transition(.opacity)
            case .paziente:
                PazienteShell().transition(.opacity)
            case .dottore:
                DottoreShell().transition(.opacity)
            }
            if let avviso = modello.avviso {
                AvvisoView(testo: avviso)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(10)
            }
        }
        .animation(.smooth(duration: 0.45), value: modello.stato)
    }
}
