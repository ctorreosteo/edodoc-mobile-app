package it.edodoc.app.feature.comune

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.edodoc.app.Grafo
import it.edodoc.app.core.net.ApiException
import it.edodoc.app.model.RigaMessaggio
import it.edodoc.app.ui.kit.BottoneIcona
import it.edodoc.app.ui.kit.Errore
import it.edodoc.app.ui.kit.LocalSpazioBarra
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.ScheletroBlocco
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.Vuoto
import it.edodoc.app.ui.kit.capitalizzato
import it.edodoc.app.ui.kit.ora
import it.edodoc.app.ui.kit.parlato
import it.edodoc.app.ui.kit.vetro
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class Stato { Inviato, Invio, Fallito }

data class Bolla(val chiave: String, val id: Long?, val mia: Boolean, val testo: String, val quando: LocalDateTime, val letto: Boolean, val clientId: String?, val stato: Stato = Stato.Inviato)

/** Il client ha inviato clientId: un messaggio tornato dal server sostituisce la sua bolla ottimistica, mai la duplica. */
fun unisci(attuali: List<Bolla>, arrivati: List<Bolla>): List<Bolla> {
    val risultato = attuali.toMutableList()
    arrivati.forEach { nuova ->
        val i = risultato.indexOfFirst { (nuova.id != null && it.id == nuova.id) || (nuova.clientId != null && it.clientId == nuova.clientId) }
        if (i >= 0) risultato[i] = nuova.copy(chiave = risultato[i].chiave) else risultato += nuova
    }
    return risultato.sortedWith(compareBy({ it.id == null }, { it.id ?: 0 }))
}

class ChatModello : ViewModel() {
    var pazienteId: Long? = null
    private val dottore get() = pazienteId != null
    val bolle = mutableStateListOf<Bolla>()
    var caricata by mutableStateOf(false)
    var errore by mutableStateOf<ApiException?>(null)
    var finite by mutableStateOf(false)
    private var caricandoPrecedenti = false
    var onLetti: () -> Unit = {}

    private fun bolla(m: RigaMessaggio) = Bolla(
        "s${m.id}", m.id, (m.mittente == "DOTTORE") == dottore, m.testo, m.inviatoIl, m.lettoIl != null, m.clientId,
    )

    private fun applica(arrivati: List<RigaMessaggio>) {
        val nuove = unisci(bolle.toList(), arrivati.map(::bolla))
        bolle.clear()
        bolle.addAll(nuove)
    }

    suspend fun aggiorna() {
        try {
            val ultimo = bolle.mapNotNull { it.id }.maxOrNull()
            val arrivati = Grafo.edo.messaggi(pazienteId, dopoId = ultimo)
            applica(arrivati)
            if (!caricata) {
                caricata = true
                finite = arrivati.size < 50
            }
            errore = null
            if (arrivati.any { (it.mittente == "DOTTORE") != dottore } || ultimo == null) {
                Grafo.edo.letti(pazienteId)
                onLetti()
            }
        } catch (e: ApiException) {
            if (!caricata) errore = e
        }
    }

    fun precedenti() {
        val primo = bolle.mapNotNull { it.id }.minOrNull() ?: return
        if (finite || caricandoPrecedenti) return
        caricandoPrecedenti = true
        viewModelScope.launch {
            runCatching {
                val vecchi = Grafo.edo.messaggi(pazienteId, primaDiId = primo)
                if (vecchi.isEmpty()) finite = true
                applica(vecchi)
            }
            caricandoPrecedenti = false
        }
    }

    fun invia(testo: String) {
        val id = UUID.randomUUID().toString()
        bolle += Bolla("c$id", null, true, testo, LocalDateTime.now(), false, id, Stato.Invio)
        spedisci(id, testo)
    }

    fun riprova(b: Bolla) {
        val id = b.clientId ?: return
        cambia(id, Stato.Invio)
        spedisci(id, b.testo)
    }

    private fun spedisci(clientId: String, testo: String) {
        viewModelScope.launch {
            try {
                applica(listOf(Grafo.edo.scrivi(pazienteId, testo, clientId)))
            } catch (_: ApiException) {
                cambia(clientId, Stato.Fallito)
            }
        }
    }

    private fun cambia(clientId: String, stato: Stato) {
        val i = bolle.indexOfFirst { it.clientId == clientId }
        if (i >= 0) bolle[i] = bolle[i].copy(stato = stato)
    }
}

/** Stessa chat per i due lati: il paziente (pazienteId null) parla col dottore, il dottore con un paziente. */
@Composable
fun ChatSchermo(pazienteId: Long?, titolo: String, onIndietro: (() -> Unit)?, onLetti: () -> Unit = {}) {
    val c = colori
    val modello = viewModel<ChatModello>(key = "chat-$pazienteId")
    modello.pazienteId = pazienteId
    modello.onLetti = onLetti
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                modello.aggiorna()
                delay(3_000)
            }
        }
    }
    val lista = rememberLazyListState()
    val bolle = modello.bolle.asReversed()
    LaunchedEffect(lista) {
        snapshotFlow { lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect {
            if (modello.bolle.isNotEmpty() && it >= modello.bolle.size - 5) modello.precedenti()
        }
    }
    LaunchedEffect(modello.bolle.lastOrNull()?.chiave) { if (modello.bolle.isNotEmpty()) lista.animateScrollToItem(0) }

    val densita = LocalDensity.current
    val ime = WindowInsets.ime.asPaddingValues(densita).calculateBottomPadding()
    val sistema = WindowInsets.navigationBars.asPaddingValues(densita).calculateBottomPadding()
    val barra = if (onIndietro == null) LocalSpazioBarra.current else 0.dp
    val fondo = max(ime, sistema + barra) + 10.dp
    val errore = modello.errore

    Schermo(onIndietro, forza = .55f, sopra = {
        Composer(Modifier.align(Alignment.BottomCenter).padding(bottom = fondo)) { modello.invia(it) }
    }) {
        Column(Modifier.fillMaxSize()) {
            when {
                errore != null -> Errore(errore.message ?: "", errore.offline) { modello.errore = null }
                !modello.caricata -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(MargineSchermo).padding(top = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(5) { ScheletroBlocco(46.dp, larghezza = if (it % 2 == 0) .6f else .45f) }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = lista,
                    reverseLayout = true,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 128.dp, bottom = fondo + 72.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (bolle.isEmpty()) item { Vuoto("Scrivi quando vuoi", "Questo è il tuo filo diretto.") }
                    items(bolle.size, key = { bolle[it].chiave }) { i ->
                        val b = bolle[i]
                        val precedente = bolle.getOrNull(i + 1)
                        Column {
                            if (precedente?.quando?.toLocalDate() != b.quando.toLocalDate()) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                                    Sopratitolo(b.quando.toLocalDate().parlato())
                                }
                            }
                            BollaVista(b, i == 0, Modifier.animateItem()) { modello.riprova(b) }
                        }
                    }
                }
            }
        }
        // Il titolo resta leggibile sopra ai messaggi che scorrono
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onIndietro != null) 76.dp else MargineSchermo, end = MargineSchermo, top = 16.dp)) {
            Testo(titolo, Tipo.Title, righe = 1)
        }
    }
}

@Composable
private fun BollaVista(b: Bolla, ultima: Boolean, modifier: Modifier = Modifier, onRiprova: () -> Unit) {
    val c = colori
    val forma = if (b.mia) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (b.mia) Alignment.End else Alignment.Start) {
        Box(
            Modifier.widthIn(max = 300.dp).clip(forma)
                .background(if (b.mia) c.ember.copy(alpha = if (b.stato == Stato.Inviato) 1f else .6f) else c.ink.copy(alpha = if (c.scuro) .08f else .06f))
                .then(if (b.stato == Stato.Fallito) Modifier.clickable(role = Role.Button, onClick = onRiprova) else Modifier)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(b.testo, style = Tipo.Body, color = if (b.mia) c.onEmber else c.ink)
        }
        when {
            b.stato == Stato.Fallito -> Testo("Non inviato. Tocca per riprovare", Tipo.Label, c.critical, Modifier.padding(top = 4.dp, end = 4.dp))
            ultima -> Testo(
                b.quando.ora() + if (b.mia && b.letto) " · Letto" else "",
                Tipo.Label.copy(fontWeight = Tipo.Body.fontWeight), c.inkTertiary, Modifier.padding(top = 4.dp, end = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun Composer(modifier: Modifier = Modifier, onInvia: (String) -> Unit) {
    val c = colori
    var testo by rememberSaveable { mutableStateOf("") }
    Row(
        modifier.padding(horizontal = 14.dp).fillMaxWidth().heightIn(min = 56.dp).vetro(RoundedCornerShape(28.dp)).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            testo, { if (it.length <= 4000) testo = it },
            Modifier.weight(1f).padding(vertical = 10.dp).semantics { contentDescription = "Messaggio" },
            textStyle = Tipo.Body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ember),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { interno ->
                Box {
                    if (testo.isEmpty()) Testo("Scrivi un messaggio", colore = c.inkSecondary)
                    interno()
                }
            },
        )
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (testo.isBlank()) c.ink.copy(alpha = .1f) else c.ember)
                .clickable(enabled = testo.isNotBlank(), role = Role.Button) {
                    onInvia(testo.trim())
                    testo = ""
                }.semantics { contentDescription = "Invia" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = if (testo.isBlank()) c.inkTertiary else c.onEmber, modifier = Modifier.size(20.dp)) }
    }
}
