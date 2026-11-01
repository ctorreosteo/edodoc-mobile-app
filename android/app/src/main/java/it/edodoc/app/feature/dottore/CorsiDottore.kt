package it.edodoc.app.feature.dottore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import it.edodoc.app.Grafo
import it.edodoc.app.core.net.ApiException
import it.edodoc.app.model.*
import it.edodoc.app.ui.kit.*
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.coroutineContext

private const val BLOCCO_TUS = 5 * 1024 * 1024 // Multiplo di 256 KiB, come chiede Cloudflare

private fun dimensione(context: Context, uri: Uri): Long =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0 } ?: 0

/** Upload tus diretto a Cloudflare, ripartendo dall'offset che il server dichiara. */
private suspend fun caricaTus(context: Context, uri: Uri, url: String, totale: Long, avanzamento: (Float) -> Unit) = withContext(Dispatchers.IO) {
    val http = Grafo.api.http
    val tipo = "application/offset+octet-stream".toMediaType()
    var offset = http.newCall(Request.Builder().url(url).head().header("Tus-Resumable", "1.0.0").build()).execute().use {
        it.header("Upload-Offset")?.toLongOrNull() ?: 0L
    }
    context.contentResolver.openInputStream(uri)!!.use { ingresso ->
        var saltati = 0L
        while (saltati < offset) saltati += ingresso.skip(offset - saltati)
        val buffer = ByteArray(BLOCCO_TUS)
        while (offset < totale) {
            coroutineContext.ensureActive()
            var letti = 0
            while (letti < BLOCCO_TUS) {
                val n = ingresso.read(buffer, letti, BLOCCO_TUS - letti)
                if (n < 0) break
                letti += n
            }
            if (letti == 0) break
            // Stesso comportamento del gestionale: dopo un errore di rete riprova con attesa crescente
            var tentativo = 0
            while (true) {
                val richiesta = Request.Builder().url(url).patch(buffer.toRequestBody(tipo, 0, letti))
                    .header("Tus-Resumable", "1.0.0").header("Upload-Offset", offset.toString()).build()
                try {
                    offset = http.newCall(richiesta).execute().use {
                        if (!it.isSuccessful) throw ApiException("Cloudflare ha rifiutato il blocco (${it.code})", it.code)
                        it.header("Upload-Offset")?.toLongOrNull() ?: throw ApiException("Risposta di Cloudflare non valida", it.code)
                    }
                    break
                } catch (e: java.io.IOException) {
                    if (++tentativo > 4) throw e
                    delay(1000L shl tentativo)
                }
            }
            withContext(Dispatchers.Main) { avanzamento(offset.toFloat() / totale) }
        }
    }
}

@Composable
private fun scegliImmagine(onScelta: (ByteArray, String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val avvisi = LocalAvvisi.current
    val selettore = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val tipo = context.contentResolver.getType(uri) ?: "image/jpeg"
            val byte = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            when {
                byte == null -> avvisi.mostra("Immagine non leggibile")
                byte.size > 5_000_000 -> avvisi.mostra("L'immagine supera i 5 MB")
                else -> onScelta(byte, tipo)
            }
        }
    }
    return { selettore.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
fun CorsiDottore(nav: NavHostController) {
    var nuovo by rememberSaveable { mutableStateOf(false) }
    Schermo({ nav.popBackStack() }, sopra = { Aggiungi("Nuovo corso") { nuovo = true } }) {
        Async("catalogo", { Grafo.edo.catalogo() }) { corsi, _ ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Costruzione", "Corsi")
                if (corsi.isEmpty()) item { Vuoto("Nessun corso", "Tutto nasce in bozza: creane uno.") }
                items(corsi, key = { it.id }) { c ->
                    Riga(
                        c.titolo, "${c.moduli} moduli · ${c.lezioniPubblicate} di ${c.lezioni} lezioni pubblicate",
                        inizio = { Copertina(c.copertinaId, Modifier.size(52.dp).clip(Forme.Controllo)) },
                        fine = { Pastiglia(if (c.pubblicato) "Pubblicato" else "Bozza", if (c.pubblicato) Tono.Positivo else Tono.Neutro) },
                        onClick = { nav.navigate("corsoEdit/${c.id}") },
                    )
                }
                piede()
            }
        }
    }
    if (nuovo) {
        Titolato("Nuovo corso", null, null, { nuovo = false }) { t, d ->
            val s = Grafo.edo.creaCorso(SalvaCorso(t, d))
            nuovo = false
            nav.navigate("corsoEdit/${s.corso.id}")
        }
    }
}

@Composable
private fun Titolato(titolo: String, t0: String?, d0: String?, onChiudi: () -> Unit, onSalva: suspend (String, String?) -> Unit) {
    var t by rememberSaveable { mutableStateOf(t0 ?: "") }
    var d by rememberSaveable { mutableStateOf(d0 ?: "") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    Foglio(onChiudi) {
        Testo(titolo, Tipo.Title)
        Campo(t, { t = it }, "Titolo")
        Campo(d, { d = it }, "Descrizione", righeMin = 3)
        AzioneBrace("Salva", abilitata = t.isNotBlank(), inCorso = inCorso) {
            inCorso = true
            esegui {
                try { onSalva(t.trim(), d.ifBlank { null }) } finally { inCorso = false }
            }
        }
    }
}

@Composable
fun CorsoEdit(id: Long, onFine: () -> Unit) {
    val esegui = azione()
    var modifica by rememberSaveable { mutableStateOf(false) }
    var nuovoModulo by rememberSaveable { mutableStateOf(false) }
    var elimina by rememberSaveable { mutableStateOf(false) }
    var modulo by remember { mutableStateOf<RigaModulo?>(null) }
    var lezione by remember { mutableStateOf<Pair<Long, RigaLezione?>?>(null) }
    var versione by remember { mutableIntStateOf(0) }
    var ricarica by remember { mutableStateOf<() -> Unit>({}) }
    val copertinaCorso = scegliImmagine { byte, tipo ->
        esegui { Grafo.edo.copertinaCorso(id, byte, tipo); versione++; ricarica() }
    }
    var moduloCopertina by remember { mutableStateOf<Long?>(null) }
    val copertinaModulo = scegliImmagine { byte, tipo ->
        val m = moduloCopertina ?: return@scegliImmagine
        esegui { Grafo.edo.copertinaModulo(m, byte, tipo); ricarica() }
    }
    Schermo(onFine, azioni = { BottoneIcona(Icons.Rounded.Edit, "Modifica il corso") { modifica = true } }) {
        Async("corso-edit-$id", { Grafo.edo.struttura(id) }) { s, r ->
            ricarica = r.esegui
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                        Copertina(s.corso.copertinaId, Modifier.fillMaxSize(), versione)
                    }
                }
                item {
                    Column(Modifier.padding(MargineSchermo), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Testo(s.corso.titolo, Tipo.Display)
                        s.corso.descrizione?.let { Testo(it, colore = colori.inkSecondary) }
                        Interruttore("Pubblicato", s.corso.pubblicato, "I pazienti vedono solo le lezioni pubblicate e complete") {
                            esegui { Grafo.edo.pubblicaCorso(id, it); r.esegui() }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AzioneQuieta("Copertina", icona = Icons.Rounded.Image, onClick = copertinaCorso)
                            if (s.corso.copertinaId != null) AzioneQuieta("Togli copertina") { esegui { Grafo.edo.togliCopertinaCorso(id); r.esegui() } }
                            AzioneQuieta("Nuovo modulo", icona = Icons.Rounded.Add) { nuovoModulo = true }
                            AzioneQuieta("Elimina corso", tono = Tono.Critico) { elimina = true }
                        }
                    }
                }
                s.moduli.forEachIndexed { i, m ->
                    item(key = "m${m.modulo.id}") {
                        Column(Modifier.padding(top = 18.dp)) {
                            Row(Modifier.padding(start = MargineSchermo, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Sopratitolo(m.modulo.titolo, Modifier.weight(1f))
                                if (i > 0) BottoneIcona(Icons.Rounded.KeyboardArrowUp, "Sposta su", vetro = false) {
                                    val ids = s.moduli.map { it.modulo.id }.toMutableList().apply { add(i - 1, removeAt(i)) }
                                    esegui { Grafo.edo.ordineModuli(id, ids); r.esegui() }
                                }
                                BottoneIcona(Icons.Rounded.MoreHoriz, "Modifica il modulo", vetro = false) { modulo = m.modulo }
                            }
                        }
                    }
                    items(m.lezioni, key = { "l${it.id}" }) { l ->
                        Riga(
                            l.titolo, listOfNotNull(if (l.tipo == "VIDEO") Etichette.StatoVideo[l.videoStato] ?: "Video da caricare" else "Testo", l.durataSecondi?.let { durata(it) }).joinToString(" · "),
                            fine = {
                                when {
                                    !l.completa -> Pastiglia("Incompleta", Tono.Attenzione)
                                    l.pubblicata -> Pastiglia("Pubblicata", Tono.Positivo)
                                    else -> Pastiglia("Bozza")
                                }
                            },
                            onClick = { lezione = m.modulo.id to l },
                        )
                    }
                    item(key = "a${m.modulo.id}") {
                        AzioneQuieta("Nuova lezione", Modifier.padding(start = MargineSchermo, top = 8.dp), icona = Icons.Rounded.Add) { lezione = m.modulo.id to null }
                    }
                }
                piede()
            }
            if (modifica) Titolato("Modifica corso", s.corso.titolo, s.corso.descrizione, { modifica = false }) { t, d ->
                Grafo.edo.aggiornaCorso(id, SalvaCorso(t, d)); modifica = false; r.esegui()
            }
            if (nuovoModulo) Titolato("Nuovo modulo", null, null, { nuovoModulo = false }) { t, d ->
                Grafo.edo.creaModulo(id, SalvaCorso(t, d)); nuovoModulo = false; r.esegui()
            }
            modulo?.let { m ->
                Foglio({ modulo = null }) {
                    var t by rememberSaveable { mutableStateOf(m.titolo) }
                    var d by rememberSaveable { mutableStateOf(m.descrizione ?: "") }
                    Testo("Modulo", Tipo.Title)
                    Campo(t, { t = it }, "Titolo")
                    Campo(d, { d = it }, "Descrizione", righeMin = 2)
                    AzioneBrace("Salva", abilitata = t.isNotBlank()) {
                        esegui { Grafo.edo.aggiornaModulo(m.id, SalvaCorso(t.trim(), d.ifBlank { null })); modulo = null; r.esegui() }
                    }
                    AzioneQuieta("Copertina del modulo", icona = Icons.Rounded.Image) { moduloCopertina = m.id; modulo = null; copertinaModulo() }
                    if (m.copertinaId != null) AzioneQuieta("Togli copertina") { esegui { Grafo.edo.togliCopertinaModulo(m.id); modulo = null; r.esegui() } }
                    AzioneQuieta("Elimina modulo", tono = Tono.Critico) { esegui { Grafo.edo.eliminaModulo(m.id); modulo = null; r.esegui() } }
                }
            }
            lezione?.let { (mod, l) -> LezioneFoglio(mod, l, { lezione = null }) { r.esegui() } }
        }
    }
    if (elimina) {
        Conferma("Eliminare il corso?", "Spariscono moduli, lezioni e video. Non si torna indietro.", "Elimina", pericolosa = true, onConferma = {
            elimina = false
            esegui { Grafo.edo.eliminaCorso(id); onFine() }
        }, onAnnulla = { elimina = false })
    }
}

@Composable
private fun LezioneFoglio(modulo: Long, l: RigaLezione?, onChiudi: () -> Unit, ricarica: () -> Unit) {
    val context = LocalContext.current
    val esegui = azione()
    val avvisi = LocalAvvisi.current
    var tipo by rememberSaveable { mutableStateOf(l?.tipo ?: "VIDEO") }
    var titolo by rememberSaveable { mutableStateOf(l?.titolo ?: "") }
    var descrizione by rememberSaveable { mutableStateOf(l?.descrizione ?: "") }
    var contenuto by rememberSaveable { mutableStateOf(l?.contenuto ?: "") }
    var avanzamento by remember { mutableStateOf<Float?>(null) }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val lezione = l ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            esegui {
                try {
                    val totale = dimensione(context, uri)
                    val up = Grafo.edo.avviaUploadVideo(lezione.id, totale)
                    avanzamento = 0f
                    caricaTus(context, uri, up.uploadUrl, totale) { avanzamento = it }
                    Grafo.edo.sincronizzaVideo(lezione.id)
                    avvisi.mostra("Video caricato: Cloudflare lo sta elaborando")
                    ricarica()
                } catch (e: java.io.IOException) {
                    avvisi.mostra("Upload interrotto. Riprova: riparte da dove si è fermato.")
                } finally {
                    avanzamento = null
                }
            }
        }
    }
    Foglio(onChiudi) {
        Testo(if (l == null) "Nuova lezione" else "Lezione", Tipo.Title)
        if (l == null) Scelta("Tipo", listOf("VIDEO" to "Video", "TESTO" to "Testo"), tipo) { tipo = it }
        Campo(titolo, { titolo = it }, "Titolo")
        Campo(descrizione, { descrizione = it }, "Descrizione", righeMin = 2)
        if (tipo == "TESTO") Campo(contenuto, { contenuto = it }, "Contenuto in Markdown", righeMin = 8)
        AzioneBrace("Salva", abilitata = titolo.isNotBlank()) {
            val corpo = SalvaLezione(tipo, titolo.trim(), descrizione.ifBlank { null }, contenuto.ifBlank { null })
            esegui {
                if (l == null) Grafo.edo.creaLezione(modulo, corpo) else Grafo.edo.aggiornaLezione(l.id, corpo)
                onChiudi()
                ricarica()
            }
        }
        if (l != null) {
            if (l.tipo == "VIDEO") {
                val a = avanzamento
                if (a != null) {
                    Testo("Caricamento ${(a * 100).toInt()}%", Tipo.Numero)
                } else {
                    l.videoErrore?.let { Testo(it, Tipo.Label, colori.critical) }
                    AzioneQuieta(if (l.videoStato == null) "Carica il video" else "Sostituisci il video", icona = Icons.Rounded.UploadFile) {
                        video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                    if (l.videoStato == "ELABORAZIONE" || l.videoStato == "CARICAMENTO") {
                        AzioneQuieta("Aggiorna lo stato", icona = Icons.Rounded.Refresh) { esegui { Grafo.edo.sincronizzaVideo(l.id); onChiudi(); ricarica() } }
                    }
                }
            }
            Interruttore("Pubblicata", l.pubblicata, if (l.completa) null else "Serve il contenuto prima di pubblicare") { si ->
                esegui { Grafo.edo.pubblicaLezione(l.id, si); onChiudi(); ricarica() }
            }
            AzioneQuieta("Elimina lezione", tono = Tono.Critico) { esegui { Grafo.edo.eliminaLezione(l.id); onChiudi(); ricarica() } }
        }
    }
}
