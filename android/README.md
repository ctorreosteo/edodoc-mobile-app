# EdoDoc per Android

App nativa in Kotlin e Jetpack Compose, una sola per paziente e dottore: la schermata dipende da `utente.ruolo`.

| | |
|---|---|
| Application id | `it.edodoc.app` |
| SDK | min 26, target 36 |
| Struttura | `core` (rete, sessione, push, media), `model` (DTO), `ui` (tema, kit, navigazione), `feature` (schermate) |

## Avvio

Servono Android Studio o l'SDK con la piattaforma 36, e il backend di `edodoc-hq` su `:8081`.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

In debug l'app parla con `http://10.0.2.2:8081/api`, cioè il `localhost` del Mac visto dall'emulatore.

## Rilascio

L'URL di produzione non ha un valore predefinito valido: si passa alla build.

```bash
./gradlew :app:bundleRelease -PEDODOC_API_URL=https://dominio/api
```

La firma del rilascio non è configurata: va aggiunta quando esiste il keystore di Google Play.

## Notifiche

Firebase è facoltativo. Senza `app/google-services.json` l'app compila e gira senza push, e rilegge i contatori all'apertura. Con il file, il plugin si applica da solo e il token FCM viene registrato a ogni apertura. Il tocco su una notifica apre la schermata giusta da `tipo`, `checkId` e `pazienteId`, che arrivano come stringhe.

## Scelte

- Date e ore sono locali di Europe/Rome: non si convertono mai.
- Gli importi sono `BigDecimal`, mai `Double`.
- Gli enum del backend restano stringhe, così un valore nuovo non fa cadere le app già installate.
- La sessione è cifrata con una chiave AES-GCM dell'Android Keystore. Il backup è disattivato.
- Un solo `/rinnova` alla volta: vedi `core/net/RinnovoSingolo.kt`.
- Il linguaggio visivo è in [../docs/DESIGN.md](../docs/DESIGN.md), le estensioni API in [../docs/CONTRATTO-API.md](../docs/CONTRATTO-API.md).
