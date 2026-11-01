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
import android.content.Intent
import androidx.core.net.toUri
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
fun AgendaSchermo(nav: NavHostController) {
    var lunedi by rememberSaveable { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    val da = LocalDate.parse(lunedi)
    var aperta by remember { mutableStateOf<VisitaRiga?>(null) }
    val context = LocalContext.current
    val esegui = azione()
    Schermo({ nav.popBackStack() }, sopra = { Aggiungi("Nuova visita") { nav.navigate("visitaForm") } }) {
        Async("agenda-$lunedi", { Grafo.edo.visite(da, da.plusDays(6)) }) { visite, r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Agenda", "${da.corto()} – ${da.plusDays(6).corto()}")
                item { Sfoglia("Settimana", { lunedi = da.minusWeeks(1).toString() }, { lunedi = da.plusWeeks(1).toString() }) }
                if (visite.isEmpty()) item { Vuoto("Settimana libera", "Nessuna visita fissata.") }
                visite.groupBy { it.inizio.toLocalDate() }.toSortedMap().forEach { (giorno, lista) ->
                    sezione(giorno.lungo())
                    items(lista.sortedBy { it.inizio }, key = { it.id }) { v ->
                        Riga(
                            v.pazienteNome, "${v.inizio.ora()} · ${Etichette.TipoVisita[v.tipo]}" + (v.numeroVisita?.let { " $it di ${v.visiteTotali}" } ?: ""),
                            fine = { Pastiglia(Etichette.StatoVisita[v.stato] ?: "", tonoVisita(v.stato)) }, onClick = { aperta = v },
                        )
                    }
                }
                piede()
            }
            aperta?.let { v ->
                Foglio({ aperta = null }) {
                    Testo(v.pazienteNome, Tipo.Title)
                    Testo("${v.inizio.toLocalDate().lungo().capitalizzato()}, ${v.inizio.ora()} · ${Etichette.Modalita[v.modalita]}", colore = colori.inkSecondary)
                    v.googleSyncStato?.let { Pastiglia(Etichette.SyncGoogle[it] ?: it, if (it == "ERRORE") Tono.Critico else Tono.Neutro) }
                    v.note?.takeIf { it.isNotBlank() }?.let { Testo(it) }
                    v.meetUrl?.let { url -> AzioneBrace("Apri Meet", icona = Icons.Rounded.Videocam) { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
                    Scelta("Stato", Etichette.StatoVisita.toList(), v.stato) { s ->
                        aperta = null
                        esegui { Grafo.edo.statoVisita(v.id, s); r.esegui() }
                    }
                    AzioneQuieta("Sposta o modifica", icona = Icons.Rounded.Edit) { aperta = null; nav.navigate("visitaForm?paziente=${v.pazienteId}&id=${v.id}") }
                    AzioneQuieta("Scheda paziente", icona = Icons.Rounded.Person) { aperta = null; nav.navigate("paziente/${v.pazienteId}") }
                }
            }
        }
    }
}

@Composable
fun VisitaForm(paziente: Long?, id: Long?, onFine: () -> Unit) {
    Schermo(onFine) {
        if (id == null) ModuloVisita(paziente, null, onFine)
        else Async("visita-$id", { Grafo.edo.visitePaziente(paziente!!).first { it.id == id } }, scheletro = {}) { v, _ -> ModuloVisita(paziente, v, onFine) }
    }
}

@Composable
private fun ModuloVisita(paziente: Long?, v: VisitaRiga?, onFine: () -> Unit) {
    var pazienteId by rememberSaveable { mutableStateOf(paziente) }
    var tipo by rememberSaveable { mutableStateOf(v?.tipo ?: "CONTROLLO") }
    var modalita by rememberSaveable { mutableStateOf(v?.modalita ?: "ONLINE") }
    var giorno by remember { mutableStateOf<LocalDate?>(v?.inizio?.toLocalDate() ?: LocalDate.now().plusDays(1)) }
    var ora by remember { mutableStateOf(v?.inizio?.toLocalTime() ?: LocalTime.of(10, 0)) }
    var durata by rememberSaveable { mutableStateOf((v?.let { java.time.Duration.between(it.inizio, it.fine ?: it.inizio).toMinutes().toInt() }?.takeIf { it > 0 } ?: 30).toString()) }
    var meet by rememberSaveable { mutableStateOf(v?.conMeet ?: true) }
    var invita by rememberSaveable { mutableStateOf(v?.invitaPaziente ?: true) }
    var note by rememberSaveable { mutableStateOf(v?.note ?: "") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    ColonnaForm {
        Testo(if (v == null) "Nuova visita" else "Modifica visita", Tipo.Display)
        if (v == null) ScegliPaziente(pazienteId) { pazienteId = it.id } else Testo(v.pazienteNome, Tipo.Headline)
        Scelta("Tipo", Etichette.TipoVisita.toList(), tipo) { tipo = it; durata = (Etichette.DurataVisita[it] ?: 30).toString() }
        Scelta("Modalità", Etichette.Modalita.toList(), modalita) { modalita = it }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CampoData("Giorno", giorno, Modifier.weight(2f)) { giorno = it }
            CampoOra("Ora", ora, Modifier.weight(1f)) { ora = it }
        }
        Campo(durata, { durata = it }, "Durata in minuti", tastiera = KeyboardType.Number)
        Interruttore("Link Meet", meet) { meet = it }
        Interruttore("Invita il paziente", invita, "Riceve l'invito su Google Calendar") { invita = it }
        Campo(note, { note = it }, "Note", righeMin = 2)
        AzioneBrace("Salva", abilitata = pazienteId != null && giorno != null, inCorso = inCorso) {
            val corpo = VisitaSalva(pazienteId!!, tipo, modalita, LocalDateTime.of(giorno, ora), durata.toIntOrNull(), meet, invita, note.ifBlank { null })
            inCorso = true
            esegui {
                try {
                    if (v == null) Grafo.edo.creaVisita(corpo) else Grafo.edo.aggiornaVisita(v.id, corpo)
                    onFine()
                } finally {
                    inCorso = false
                }
            }
        }
    }
}
