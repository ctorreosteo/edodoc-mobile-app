# EdoDoc per iOS

App nativa per pazienti e dottore: Swift e SwiftUI, nessuna libreria di terze parti. Bundle `it.edodoc.app`, da iOS 17, compilata con l'SDK di iOS 26 (Liquid Glass su iOS 26, material sulle versioni precedenti). La schermata dipende da `utente.ruolo`.

Fonti condivise con Android: [docs/DESIGN.md](../docs/DESIGN.md) e [docs/CONTRATTO-API.md](../docs/CONTRATTO-API.md).

## Avvio

Serve il backend di `edodoc-hq` su `:8081`. Apri `EdoDoc.xcodeproj` con Xcode 26 e avvia su un simulatore.

Il file di progetto usa cartelle sincronizzate: un file aggiunto sotto `EdoDoc/` entra da solo nella build.

```bash
xcodebuild -project EdoDoc.xcodeproj -scheme EdoDoc \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO test
```

I test `EdoDocUITests` guidano l'app vera contro il backend locale e usano i dati demo. Le schermate finiscono nella cartella indicata da `TEST_RUNNER_EDODOC_SHOTS`.

`ScrittureTests` scrive sul backend ed emette una fattura che consuma un numero: si salta da sola se `TEST_RUNNER_EDODOC_GIRO` non è impostata.

Il Keychain del simulatore funziona solo con l'app firmata. `testSessioneRipristinata` va quindi eseguito senza `CODE_SIGNING_ALLOWED=NO`, con la firma predefinita o con `CODE_SIGN_IDENTITY=-`.

## Struttura

| Cartella | Contenuto |
|---|---|
| `EdoDoc/Core` | Client API con rinnovo single-flight, Keychain, modelli, date senza fuso, stato dell'app |
| `EdoDoc/Design` | Token, tipografia, brace, vetro, stati di caricamento, vuoto ed errore |
| `EdoDoc/Features/Accesso` | Accesso e primo accesso con il link di invito |
| `EdoDoc/Features/Comuni` | Chat, vocale, PDF, Markdown, copertine |
| `EdoDoc/Features/Paziente` | Oggi, check, corsi, percorso, fatture, profilo |
| `EdoDoc/Features/Dottore` | Cruscotto, pazienti, agenda, check, chat, acquisti, pagamenti, fatture, corsi, finance, task, impostazioni |

## Prima di pubblicare

1. Controlla `API_BASE_URL` della configurazione Release nel progetto: ora vale l'URL tecnico di Cloud Run `https://edodoc-backend-4cmkg7zsaq-oc.a.run.app/api`, da sostituire se nasce un dominio dedicato.
2. Scegli il Team in Signing & Capabilities: serve un account Apple Developer a pagamento.
3. Crea su developer.apple.com l'identificatore `it.edodoc.app` con Push Notifications.
4. Crea una chiave APNs (`.p8`) e passala al backend con le variabili `APNS_*` di `env.example`.

`aps-environment` vale `development` in Debug e `production` in Release. Il backend deve usare l'ambiente corrispondente: scambiarli fa rifiutare i token.

Ogni versione pubblicata è obbligatoria: all'avvio e al ritorno in primo piano l'app confronta `MARKETING_VERSION` con quella dell'App Store italiano e, se è indietro, si chiude dietro la schermata di aggiornamento. Vale solo in Release: in Debug si simula con l'argomento di avvio `-edodoc.aggiornamento`. Una build TestFlight con versione più bassa di quella in store resta bloccata. Se l'App Store non risponde, o il telefono non supporta la versione nuova, l'app resta usabile.

Il link di invito `edodoc://` funziona dove il client di posta lo rende cliccabile. In tutti gli altri casi il paziente lo incolla nella schermata di primo accesso. Per un link sempre cliccabile serve un dominio con Universal Links.
