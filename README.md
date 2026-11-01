# edodoc-mobile-app

App native di EdoDoc per i pazienti e per il Dr. Edoardo Bo. Una sola app per piattaforma: la schermata dipende dal ruolo dell'utente. Il backend è in `edodoc-hq`.

| Cartella | Cosa contiene | Stack |
|----------|---------------|-------|
| [ios/](ios/) | App iOS | Swift, SwiftUI, iOS 17+ |
| [android/](android/) | App Android | Kotlin, Jetpack Compose, Android 8+ |
| [docs/](docs/) | [Linguaggio visivo](docs/DESIGN.md) e [contratto API](docs/CONTRATTO-API.md) | |

Avvio e collaudo di iOS sono in [ios/README.md](ios/README.md). Gli screenshot dei collaudi restano in locale in `docs/screenshots/`, fuori da git.

## Avvio locale

Avvia il backend con `./dev.sh` in `edodoc-hq`.

- iOS: apri `ios/EdoDoc.xcodeproj` ed esegui su simulatore. Punta a `http://localhost:8081/api`.
- Android: `cd android && ./gradlew installDebug` con un emulatore acceso. Punta a `http://10.0.2.2:8081/api`.

## Prima della pubblicazione

- iOS: segui la sezione Rilascio di [ios/README.md](ios/README.md).
- Android: configura il keystore e aggiungi `app/google-services.json`.
- Backend: imposta le variabili `APNS_*` e `FCM_CREDENTIALS_PATH`.

## Account per la revisione di Apple e Google

`prova.dottore@edodoc.it` e `prova.paziente@edodoc.it` sono gli account per la revisione. Non esiste un server demo: senza `API_DEMO_URL` le app usano l'URL di produzione anche per questi due indirizzi.
