# Estensioni API per le app native

Si aggiungono alle API descritte nel README di `edodoc-hq`. Questo file è la fonte condivisa tra backend, iOS e Android.

Bundle id / application id: `it.edodoc.app`. Schema deep link: `edodoc://`.

## Percorso del paziente

`GET /api/app/percorso`

```json
{
  "percorso": {
    "id": 1, "tipo": "PERCORSO_6_MESI", "dataInizio": "2026-06-01", "dataFine": "2026-12-01",
    "visiteTotali": 6, "visiteFissate": 4, "visiteEffettuate": 3, "stato": "ATTIVO"
  },
  "visite": [
    {
      "id": 10, "numeroVisita": 1, "visiteTotali": 6, "tipo": "PRIMA_VISITA", "modalita": "ONLINE",
      "stato": "EFFETTUATA", "inizio": "2026-06-01T10:00:00", "fine": "2026-06-01T11:00:00", "meetUrl": null
    }
  ]
}
```

`percorso` è quello in corso, oppure il più recente, oppure `null`. `visite` contiene tutte le visite del paziente, anche quelle fuori percorso, in ordine di data. I valori di `tipo` e `stato` sono quelli degli enum Java. Mai note del dottore né dati di sincronizzazione Google.

## Eliminazione dell'account

`POST /api/app/account/chiudi`, senza corpo: solo paziente. Archivia il paziente e chiude subito accesso, sessioni e dispositivi. Dopo la risposta l'app torna al login. L'anonimizzazione la conferma il dottore dal gestionale entro un mese.

## Feedback al check

Canali: testo, vocale, email. L'ultimo feedback salvato sostituisce il precedente, compreso l'eventuale vocale.

`CheckDto.Dettaglio` guadagna tre campi:

| Campo | Tipo | Significato |
|---|---|---|
| `feedbackAudioId` | UUID o null | C'è un vocale da ascoltare |
| `feedbackAudioSecondi` | intero o null | Durata del vocale |
| `feedbackEmailIl` | data e ora o null | Quando è partita l'email |

| Metodo | Percorso | Uso |
|---|---|---|
| PUT | `/api/check/{id}/feedback` | `{testo, inviaEmail?}`. Senza `inviaEmail` il comportamento resta quello attuale, così il gestionale web non cambia |
| POST | `/api/check/{id}/feedback/audio` | Multipart: `file` (AAC in contenitore m4a, massimo 5 MB), `secondi`, `testo?`, `inviaEmail?` |
| GET | `/api/check/{id}/feedback/audio` | Il dottore riascolta il vocale |
| GET | `/api/app/check/{id}/feedback/audio` | Il paziente lo ascolta. Solo sui propri check, altrimenti 404 |

Le risposte audio hanno `Content-Type: audio/mp4`. Il vocale conta come feedback a tutti gli effetti: blocca le modifiche al check, risulta da leggere e invia la push.

Con `inviaEmail: true` il paziente riceve il testo per email con Gmail. Per un vocale, l'email avvisa soltanto che c'è un messaggio nell'app: l'audio non viene allegato. Se Gmail è spento o il paziente non ha email, il feedback si salva comunque, `feedbackEmailIl` resta null e `message` lo dice. Il dottore non deve mai perdere il feedback.

## Notifiche native

`POST /api/auth/dispositivi` accetta i token di APNs e FCM: niente più formato Expo, solo non vuoto e al massimo 255 caratteri. Il backend sceglie il canale da `piattaforma`.

Il payload resta generico: niente nomi né dati sanitari. In `data`: `tipo` (`MESSAGGIO` o `FEEDBACK`) con `checkId` o `pazienteId`.

## Invito all'app

Il valore predefinito di `APP_IMPOSTA_PASSWORD_URL` diventa `edodoc://imposta-password`. Il link risultante è `edodoc://imposta-password?token=...`.

I client di posta spesso non rendono cliccabili gli schemi personalizzati, quindi le app permettono anche di incollare il link nella schermata di primo accesso.
