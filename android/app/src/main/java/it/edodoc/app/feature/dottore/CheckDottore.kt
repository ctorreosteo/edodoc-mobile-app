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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import it.edodoc.app.core.media.Registratore
import it.edodoc.app.feature.comune.LettoreVocale
import it.edodoc.app.feature.comune.Onda
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun SettimanaSchermo(nav: NavHostController) {
    var giorno by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val data = LocalDate.parse(giorno)
    var filtro by rememberSaveable { mutableStateOf("Tutti") }
    val esegui = azione()
    Schermo {
        Async("settimana-$giorno", { Grafo.edo.settimana(data) }) { s, r ->
            val righe = s.righe.filter {
                when (filtro) {
                    "Segnali" -> it.check?.haSegnali == true
                    "Da rispondere" -> it.check != null && !it.check.haFeedback
                    "Mancanti" -> it.check == null
                    else -> true
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Settimana del ${s.settimana.corto()}", "${s.compilati} di ${s.attesi}", if (s.conSegnali > 0) "${s.conSegnali} con segnali" else "Nessun segnale", conBarra = false)
                item {
                    Sfoglia(s.settimana.breve(), { giorno = s.settimana.minusWeeks(1).toString() }, { giorno = s.settimana.plusWeeks(1).toString() })
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MargineSchermo, vertical = 10.dp)) {
                        Scelta(null, listOf("Tutti", "Segnali", "Da rispondere", "Mancanti").map { it to it }, filtro) { filtro = it }
                    }
                }
                if (righe.isEmpty()) item { Vuoto("Niente qui", "In questa vista non c'è nessuno.") }
                items(righe, key = { it.pazienteId }) { p ->
                    val c = p.check
                    Riga(
                        p.pazienteNome,
                        if (c != null) segnali(c).ifBlank { "Compilato" } else Etichette.AzioneCheck[p.azione] ?: "Da compilare",
                        fine = {
                            when {
                                c == null -> Pastiglia("Manca", Tono.Neutro)
                                c.haFeedback -> Pastiglia("Risposto", Tono.Positivo)
                                c.haSegnali -> Pastiglia("Segnali", Tono.Attenzione)
                                else -> Pastiglia("Da leggere", Tono.Brace)
                            }
                        },
                        onClick = { if (c != null) nav.navigate("checkDettaglio/${p.pazienteId}/${c.id}") else nav.navigate("checkNuovo/${p.pazienteId}?data=${s.settimana}") },
                        filetto = false,
                    )
                    Row(Modifier.padding(horizontal = MargineSchermo), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spunta("Lunedì", p.lunediFattoIl != null) { esegui { Grafo.edo.contatto(Contatto(p.pazienteId, s.settimana, "LUNEDI", it)); r.esegui() } }
                        Spunta("Mercoledì", p.mercolediFattoIl != null) { esegui { Grafo.edo.contatto(Contatto(p.pazienteId, s.settimana, "MERCOLEDI", it)); r.esegui() } }
                    }
                    Filetto(Modifier.padding(horizontal = MargineSchermo))
                }
                piede()
            }
        }
    }
}

private fun segnali(c: CheckDettaglio) = listOfNotNull(
    "peso fermo".takeIf { c.pesoFermo }, "nessun miglioramento".takeIf { c.percezioneNegativa }, "energia bassa".takeIf { c.energiaBassa },
).joinToString(" · ").capitalizzato()

@Composable
private fun Spunta(testo: String, fatto: Boolean, onCambia: (Boolean) -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).clickable(role = Role.Checkbox) { onCambia(!fatto) }.semantics { contentDescription = "Contatto del $testo, " + if (fatto) "fatto" else "da fare" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (fatto) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (fatto) colori.positive else colori.inkTertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Testo(testo, Tipo.Label, colori.inkSecondary)
    }
}

/** Risposte del paziente e feedback: scritto, a voce, anche per email. */
@Composable
fun CheckDottore(pazienteId: Long, id: Long, onFine: () -> Unit) {
    val context = LocalContext.current
    Schermo(onFine) {
        Async("check-doc-$id", {
            val c = Grafo.edo.checkPaziente(pazienteId).first { it.id == id }
            val audio = c.feedbackAudioId?.let { a ->
                val f = File(context.cacheDir, "vocali/$a.m4a")
                if (f.exists()) f else Grafo.edo.scaricaVocale(id, true, f)
            }
            c to audio
        }) { (c, audio), r ->
            LaunchedEffect(c.id) { if (c.lettoIl == null) runCatching { Grafo.edo.checkLetto(c.id) } }
            ColonnaForm {
                Column {
                    Sopratitolo("Settimana del ${c.settimana.corto()}")
                    Spacer(Modifier.height(10.dp))
                    Testo(c.pazienteNome, Tipo.Display)
                    segnali(c).takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Pastiglia(it, Tono.Attenzione)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    c.pastiOttimali?.let { Numero("$it/21", "Pasti") }
                    c.energia?.let { Numero("$it/5", "Energia") }
                    c.impegno?.let { Numero("$it/5", "Impegno") }
                }
                c.pesoKg?.let { Dato("Peso", it.kg()) }
                c.andamentoPeso?.let { Dato("Dati del peso", it) }
                c.pastiLiberi?.let { Dato("Pasti liberi", it) }
                c.allenamento?.let { Dato("Allenamento", it) }
                Dato("Lo specchio", listOfNotNull(c.percezioneMigliorata?.let { if (it) "Nota miglioramenti" else "Nessun miglioramento" }, c.percezione).joinToString(" · ").ifBlank { "—" })
                Filetto()
                if (c.haFeedback) {
                    Sopratitolo("Il tuo feedback")
                    audio?.let { LettoreVocale(it, c.feedbackAudioSecondi ?: 0) }
                    c.feedback?.takeIf { it.isNotBlank() }?.let { Testo(it) }
                    Testo(
                        listOfNotNull(c.feedbackIl?.conOra(), if (c.feedbackLettoIl != null) "letto" else "non ancora letto", c.feedbackEmailIl?.let { "email inviata" }).joinToString(" · "),
                        Tipo.Label, colori.inkTertiary,
                    )
                }
                Risposta(c, if (c.haFeedback) "Sostituisci il feedback" else "Rispondi") { r.esegui() }
            }
        }
    }
}

@Composable
private fun Dato(etichetta: String, valore: String) {
    Column {
        Sopratitolo(etichetta)
        Spacer(Modifier.height(4.dp))
        Testo(valore)
    }
}

@Composable
private fun Risposta(c: CheckDettaglio, titolo: String, onInviato: () -> Unit) {
    val context = LocalContext.current
    val avvisi = LocalAvvisi.current
    val esegui = azione()
    var testo by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(false) }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    var inviaVocale by remember { mutableStateOf<Pair<File, Int>?>(null) }
    val registratore = remember { Registratore(context) }
    var registrando by remember { mutableStateOf(false) }
    val livelli = remember { mutableStateListOf<Float>() }
    var secondi by remember { mutableIntStateOf(0) }

    fun ferma() {
        val durata = registratore.ferma()
        registrando = false
        if (durata > 0) inviaVocale = registratore.file to durata else avvisi.mostra("Vocale troppo breve")
    }
    fun avvia() {
        livelli.clear()
        inviaVocale = null
        try {
            registratore.avvia { ferma() }
            registrando = true
        } catch (_: Exception) {
            avvisi.mostra("Microfono non disponibile")
        }
    }
    val permesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) avvia() else avvisi.mostra("Per i vocali serve il permesso del microfono")
    }
    LaunchedEffect(registrando) {
        while (registrando) {
            livelli += registratore.livello().coerceIn(.04f, 1f)
            if (livelli.size > 56) livelli.removeAt(0)
            secondi = ((System.currentTimeMillis() - registratore.inizio) / 1000).toInt()
            delay(90)
        }
    }
    DisposableEffect(Unit) { onDispose { if (registrando) registratore.annulla() } }

    fun fatto(risposta: Pair<CheckDettaglio?, String?>) {
        testo = ""
        email = false
        inviaVocale = null
        risposta.second?.let { avvisi.mostra(it) }
        onInviato()
    }

    Sopratitolo(titolo)
    when {
        registrando -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Onda(livelli.toList(), 1f, Modifier.weight(1f).height(56.dp))
                Testo(durata(secondi), Tipo.Numero)
            }
            AzioneBrace("Ferma", icona = Icons.Rounded.Stop, onClick = ::ferma)
        }
        inviaVocale != null -> {
            val (file, dur) = inviaVocale!!
            LettoreVocale(file, dur)
            Campo(testo, { testo = it }, "Testo di accompagnamento", righeMin = 2)
            Interruttore("Avvisa anche per email", email, "L'email dice solo che c'è un vocale nell'app") { email = it }
            AzioneBrace("Invia il vocale", inCorso = inCorso, icona = Icons.AutoMirrored.Rounded.Send) {
                inCorso = true
                esegui {
                    try { fatto(Grafo.edo.feedbackVocale(c.id, file, dur, testo.ifBlank { null }, email)) } finally { inCorso = false }
                }
            }
            AzioneQuieta("Registra di nuovo", icona = Icons.Rounded.Refresh) { avvia() }
        }
        else -> {
            Campo(testo, { testo = it.take(4000) }, "Scrivi al paziente", righeMin = 3)
            Interruttore("Invia anche per email", email) { email = it }
            AzioneBrace("Invia il feedback", abilitata = testo.isNotBlank(), inCorso = inCorso, icona = Icons.AutoMirrored.Rounded.Send) {
                inCorso = true
                esegui {
                    try { fatto(Grafo.edo.feedbackTesto(c.id, testo.trim(), email)) } finally { inCorso = false }
                }
            }
            AzioneQuieta("Registra un vocale", icona = Icons.Rounded.Mic) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) avvia()
                else permesso.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/** Check dettato a voce o ricevuto per messaggio: lo inserisce il dottore. */
@Composable
fun CheckManuale(pazienteId: Long, data: LocalDate?, onFine: () -> Unit) {
    var pasti by rememberSaveable { mutableStateOf("") }
    var liberi by rememberSaveable { mutableStateOf("") }
    var energia by rememberSaveable { mutableIntStateOf(3) }
    var impegno by rememberSaveable { mutableIntStateOf(3) }
    var allenamento by rememberSaveable { mutableStateOf("") }
    var peso by rememberSaveable { mutableStateOf("") }
    var datiPeso by rememberSaveable { mutableStateOf("") }
    var migliorata by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var percezione by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    val voti = (1..5).map { it to "$it" }
    val nPasti = pasti.toIntOrNull()
    Schermo(onFine) {
        ColonnaForm {
            Testo("Inserisci il check", Tipo.Display)
            Campo(pasti, { pasti = it }, "Pasti ottimali (0-21)", tastiera = KeyboardType.Number)
            Campo(liberi, { liberi = it }, "Pasti liberi")
            Scelta("Energia", voti, energia) { energia = it }
            Campo(allenamento, { allenamento = it }, "Allenamenti o passeggiate")
            Campo(peso, { peso = it }, "Peso (kg)", tastiera = KeyboardType.Decimal)
            Campo(datiPeso, { datiPeso = it }, "Dati del peso della settimana")
            Scelta("Lo specchio", listOf(true to "Nota miglioramenti", false to "Nessun miglioramento"), migliorata) { migliorata = it }
            Campo(percezione, { percezione = it }, "Percezione, a parole sue", righeMin = 2)
            Scelta("Impegno", voti, impegno) { impegno = it }
            AzioneBrace("Salva", abilitata = nPasti != null && nPasti in 0..21, inCorso = inCorso) {
                inCorso = true
                esegui {
                    try {
                        Grafo.edo.inserisciCheck(pazienteId, data, Risposte(nPasti ?: 0, liberi.ifBlank { null }, energia, allenamento.ifBlank { null }, importo(peso), datiPeso.ifBlank { null }, percezione.ifBlank { null }, migliorata, impegno))
                        onFine()
                    } finally {
                        inCorso = false
                    }
                }
            }
        }
    }
}
