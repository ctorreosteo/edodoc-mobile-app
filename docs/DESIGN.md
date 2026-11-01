# Linguaggio visivo EdoDoc

Un unico linguaggio, due app davvero native. iOS e Android condividono palette, ritmo e momenti chiave, ma ciascuna si comporta come il proprio sistema: gesti, navigazione, haptics e controlli di piattaforma restano quelli attesi.

## L'idea: la luce della brace

La brand è un blu notte e un arancio. L'app è una stanza scura illuminata da una sola fonte calda: una brace arancione che vive dietro al contenuto, respira lentamente e si sposta verso ciò che conta adesso. Il resto è silenzio.

L'arancio non è decorazione: indica sempre l'unica azione o l'unico dato importante della schermata. Mai due accenti in competizione.

## Colore

| Token | Scuro (predefinito) | Chiaro |
|---|---|---|
| `canvas` | `#080F1C` | `#F6F3EE` |
| `canvasElevated` | `#101B2E` | `#FFFFFF` |
| `ink` | `#F4F1EC` | `#0F1C31` |
| `inkSecondary` | `#F4F1EC` al 62% | `#0F1C31` al 62% |
| `inkTertiary` | `#F4F1EC` al 38% | `#0F1C31` al 42% |
| `hairline` | bianco al 9% | `#0F1C31` al 9% |
| `brand` (blu) | `#1F3A63` | `#1F3A63` |
| `ember` (accento) | `#FF8F3D` | `#E8701F` |
| `emberSoft` | `#FFC48F` | `#F0944E` |
| `positive` | `#7FD8B0` | `#1E8A62` |
| `warning` | `#FFC85C` | `#B97A00` |
| `critical` | `#FF7A70` | `#C93A30` |

Il tema segue il sistema. Il contrasto del testo resta almeno 4.5:1.

## Tipografia

Montserrat, inclusa nell'app, con Dynamic Type su iOS e unità `sp` su Android.

| Ruolo | Peso | Dimensione |
|---|---|---|
| Display (numeri eroici, titoli di apertura) | 300 | 56–72, tracking stretto |
| Title | 600 | 26 |
| Headline | 600 | 18 |
| Body | 400 | 16, interlinea 1.45 |
| Label | 600 | 13 |
| Overline | 700 | 11, maiuscolo, tracking largo |

Il contrasto tra Display leggerissimo e Overline piccolissimo è la firma tipografica. I numeri usano cifre tabellari.

## Superficie

- Tre piani: canvas con brace, vetro, contenuto. Nessuna ombra grigia.
- Vetro: Liquid Glass (`glassEffect`) su iOS 26 con ripiego su material; su Android `RenderEffect` blur da API 31, con ripiego su superficie traslucida.
- Niente card dentro card. Le liste sono righe su canvas separate da hairline; il vetro è riservato agli elementi flottanti e al pezzo più importante della schermata.
- Angoli continui: 28 per i pannelli, 18 per i controlli, capsula per le azioni.
- Navigazione a capsula flottante: `TabView` di sistema su iOS, barra in vetro su Android.

## Movimento

- Tutto a molla, niente easing lineari. Transizioni condivise tra elenco e dettaglio.
- La brace respira con un ciclo di circa 9 secondi: è shader su iOS (`MeshGradient`) e AGSL su Android da API 33, con gradiente statico altrove.
- Haptics solo a conferma di un gesto significativo.
- Riduci movimento: la brace si ferma e le molle diventano dissolvenze.

## Momenti firma

La geometria del logo (barre della E, triangolo della D, ellisse della O) è il repertorio delle forme.

1. **Oggi**: non una dashboard. Un'unica frase in Display dice cosa fare adesso, sotto c'è l'unico gesto che serve.
2. **Check**: una domanda alla volta a schermo intero. Pasti con quadrante circolare da trascinare, energia e impegno con cinque ellissi che si riempiono, invio con la brace che si espande a tutto schermo.
3. **Feedback vocale**: la forma d'onda reale è l'interfaccia. Si trascina per scorrere, la parte ascoltata si accende di brace.
4. **Percorso**: una linea verticale con un nodo per visita, i futuri vuoti, il prossimo acceso con il conto alla rovescia.
5. **Corsi**: copertine a tutta altezza con transizione verso il player.
6. **Chat**: bolle essenziali, invio ottimistico, nessun doppione.
7. **Dottore**: stessa eleganza con più densità. La mattina si apre sul numero di ciò che chiede attenzione.

## Stati e testi

Ogni schermata ha caricamento (scheletri, non spinner), vuoto, errore con riprova e stato offline. Italiano, tono caldo e sobrio, seconda persona. Nessun testo promette risultati clinici. Target tattili di almeno 44 pt / 48 dp; VoiceOver e TalkBack su tutti i controlli personalizzati.
