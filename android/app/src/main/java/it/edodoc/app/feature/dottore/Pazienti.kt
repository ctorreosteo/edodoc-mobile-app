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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.core.net.toUri

private fun tonoPaziente(s: String?) = when (s) {
    "ATTIVO" -> Tono.Positivo
    "MANTENIMENTO" -> Tono.Brace
    "CONCLUSO" -> Tono.Neutro
    else -> Tono.Attenzione
}

private fun tonoPagamenti(s: String?) = when (s) {
    "SCADUTO" -> Tono.Critico
    "DA_PAGARE" -> Tono.Attenzione
    else -> Tono.Positivo
}

@Composable
fun PazientiSchermo(nav: NavHostController) {
    var cerca by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    Schermo(sopra = { Aggiungi("Nuovo paziente", conBarra = true) { nav.navigate("pazienteForm") } }) {
        Async("pazienti", { Grafo.edo.pazienti() }) { tutti, _ ->
            val visibili = tutti.filter { (filtro == null || it.stato == filtro) && it.nomeCompleto.contains(cerca, ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                testata("${tutti.size} in archivio", "Pazienti", conBarra = false)
                item {
                    Column(Modifier.padding(horizontal = MargineSchermo).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Campo(cerca, { cerca = it }, "Cerca", capitalizza = KeyboardCapitalization.Words)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MargineSchermo)) {
                        Scelta(null, listOf<Pair<String?, String>>(null to "Tutti") + Etichette.StatoPaziente.toList().map { it.first to it.second }, filtro) { filtro = it }
                    }
                }
                if (visibili.isEmpty()) item { Vuoto("Nessun paziente", "Prova a cambiare ricerca o filtro.") }
                items(visibili, key = { it.id }) { p ->
                    Riga(
                        p.nomeCompleto,
                        p.prossimaVisita?.let { "Prossima visita ${it.conOra()}" } ?: p.percorso?.let { Etichette.TipoPercorso[it.tipo] },
                        fine = {
                            if (p.situazionePagamenti == "SCADUTO") Pastiglia("Scaduto", Tono.Critico)
                            else Pastiglia(Etichette.StatoPaziente[p.stato] ?: "", tonoPaziente(p.stato))
                        },
                        onClick = { nav.navigate("paziente/${p.id}") },
                    )
                }
                piede()
            }
        }
    }
}

private data class Scheda(
    val p: PazienteRiga, val visite: List<VisitaRiga>, val check: List<CheckDettaglio>, val pagamenti: PagamentiLista,
    val fatture: List<FatturaRiga>, val task: List<TaskRiga>, val percorsi: List<PercorsoSintesi>,
)

@Composable
fun SchedaPaziente(id: Long, nav: NavHostController) {
    val context = LocalContext.current
    val esegui = azione()
    val avvisi = LocalAvvisi.current
    var scheda by rememberSaveable { mutableStateOf("Visite") }
    var archivia by rememberSaveable { mutableStateOf(false) }
    var invito by remember { mutableStateOf<InvitoApp?>(null) }
    Schermo({ nav.popBackStack() }, azioni = {
        BottoneIcona(Icons.Rounded.Edit, "Modifica") { nav.navigate("pazienteForm?id=$id") }
    }) {
        Async("scheda-$id", {
            Scheda(
                Grafo.edo.paziente(id), Grafo.edo.visitePaziente(id), Grafo.edo.checkPaziente(id), Grafo.edo.pagamentiPaziente(id),
                Grafo.edo.fatturePaziente(id), Grafo.edo.taskPaziente(id), Grafo.edo.percorsiPaziente(id),
            )
        }) { d, r ->
            val p = d.p
            LazyColumn(Modifier.fillMaxSize()) {
                testata(Etichette.StatoPaziente[p.stato], p.nomeCompleto, p.situazione)
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MargineSchermo), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AzioneQuieta("Chat", icona = Icons.Rounded.ChatBubble) { nav.navigate("chat/$id?nome=" + cod(p.nomeCompleto)) }
                        p.cellulare?.takeIf { it.isNotBlank() }?.let { n ->
                            AzioneQuieta("Chiama", icona = Icons.Rounded.Call) { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$n".toUri())) }
                            AzioneQuieta("WhatsApp", icona = Icons.Rounded.Forum) { context.startActivity(Intent(Intent.ACTION_VIEW, "https://wa.me/${n.filter(Char::isDigit)}".toUri())) }
                        }
                        p.email?.takeIf { it.isNotBlank() }?.let { e ->
                            AzioneQuieta("Email", icona = Icons.Rounded.Mail) { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$e".toUri())) }
                        }
                        AzioneQuieta(if (p.accessoApp) "Nuovo invito app" else "Invita all'app", icona = Icons.Rounded.PhoneIphone) {
                            esegui { invito = Grafo.edo.invito(id) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = MargineSchermo), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        p.percorso?.let { Numero("${it.visiteEffettuate}/${it.visiteTotali}", "Visite") }
                        p.residuo?.let { Numero(it.euroTondo(), "Residuo") }
                        d.check.firstOrNull { it.pesoKg != null }?.pesoKg?.let { Numero(it.kg(), "Peso") }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MargineSchermo)) {
                        Scelta(null, listOf("Visite", "Check", "Pagamenti", "Fatture", "Task", "Dati").map { it to it }, scheda) { scheda = it }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                when (scheda) {
                    "Visite" -> {
                        item { Azioni("Nuova visita") { nav.navigate("visitaForm?paziente=$id") } }
                        if (d.visite.isEmpty()) item { Vuoto("Nessuna visita", "Fissa la prima dal pulsante qui sopra.") }
                        items(d.visite.sortedByDescending { it.inizio }, key = { "v${it.id}" }) { v ->
                            Riga(Etichette.TipoVisita[v.tipo] ?: v.tipo, "${v.inizio.conOra()} · ${Etichette.Modalita[v.modalita]}",
                                fine = { Pastiglia(Etichette.StatoVisita[v.stato] ?: "", tonoVisita(v.stato)) }, onClick = { nav.navigate("visitaForm?paziente=$id&id=${v.id}") })
                        }
                    }
                    "Check" -> {
                        item { Azioni("Inserisci check") { nav.navigate("checkNuovo/$id") } }
                        if (d.check.isEmpty()) item { Vuoto("Nessun check", "Compare qui quando il paziente lo invia.") }
                        items(d.check, key = { "c${it.id}" }) { c ->
                            Riga("Settimana del ${c.settimana.corto()}", listOfNotNull(c.pesoKg?.kg(), c.energia?.let { "energia $it/5" }).joinToString(" · "),
                                fine = { if (c.haSegnali) Pastiglia("Segnali", Tono.Attenzione) else if (c.haFeedback) Pastiglia("Risposto", Tono.Positivo) },
                                onClick = { nav.navigate("checkDettaglio/$id/${c.id}") })
                        }
                    }
                    "Pagamenti" -> {
                        item {
                            Row(Modifier.padding(horizontal = MargineSchermo), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AzioneQuieta("Nuovo percorso", icona = Icons.Rounded.Add) { nav.navigate("percorsoForm?paziente=$id") }
                                AzioneQuieta("Nuovo pagamento", icona = Icons.Rounded.Add) { nav.navigate("pagamentoForm?paziente=$id") }
                            }
                        }
                        d.percorsi.forEach { c ->
                            item(key = "pc${c.id}") { Riga(Etichette.TipoPercorso[c.tipo] ?: c.tipo, "${Etichette.Piano[c.pianoPagamento]} · ${c.importoTotale.euro()}", fine = { Pastiglia(c.stato.lowercase().capitalizzato()) }) }
                        }
                        items(d.pagamenti.righe, key = { "p${it.id}" }) { q -> RigaPagamento(q, nav, ricarica = r.esegui) }
                    }
                    "Fatture" -> {
                        item { Azioni("Fattura libera") { nav.navigate("fatturaForm?paziente=$id") } }
                        if (d.fatture.isEmpty()) item { Vuoto("Nessuna fattura", "Si emettono a pagamento ricevuto.") }
                        items(d.fatture, key = { "f${it.id}" }) { f -> RigaFattura(f, nav, ricarica = r.esegui) }
                    }
                    "Task" -> {
                        if (d.task.isEmpty()) item { Vuoto("Nessun task", "Tutto in ordine.") }
                        items(d.task, key = { "t${it.id}" }) { t -> Riga(t.titolo, t.scadenza?.breve(), fine = { if (t.stato == "FATTO") Pastiglia("Fatto", Tono.Positivo) }, onClick = { nav.navigate("task") }) }
                    }
                    else -> {
                        val dati = listOf(
                            "Email" to p.email, "Cellulare" to p.cellulare, "Codice fiscale" to p.codiceFiscale, "Nascita" to p.dataNascita?.breve(),
                            "Indirizzo" to listOfNotNull(p.indirizzo, p.cap, p.citta, p.provincia).joinToString(" ").ifBlank { null },
                            "Fonte" to Etichette.Fonte[p.fonte], "Peso iniziale" to p.pesoInizialeKg?.kg(), "Peso obiettivo" to p.pesoObiettivoKg?.kg(), "Nota" to p.nota,
                        )
                        items(dati) { (k, v) -> Riga(k, v ?: "—") }
                        item {
                            Spacer(Modifier.height(14.dp))
                            AzioneQuieta("Archivia paziente", Modifier.padding(horizontal = MargineSchermo), tono = Tono.Critico) { archivia = true }
                        }
                    }
                }
                piede()
            }
        }
    }
    invito?.let { i ->
        Foglio({ invito = null }) {
            Testo("Invito pronto", Tipo.Title)
            Testo(if (i.emailInviata) "L'email è partita. Puoi anche copiare il link e mandarlo su WhatsApp." else "Gmail è spento: copia il link e mandalo tu al paziente.", colore = colori.inkSecondary)
            AzioneBrace("Copia il link", icona = Icons.Rounded.ContentCopy) {
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Invito EdoDoc", i.link))
                avvisi.mostra("Link copiato")
                invito = null
            }
        }
    }
    if (archivia) {
        Conferma("Archiviare il paziente?", "Sparirà dagli elenchi. I dati fiscali restano conservati.", "Archivia", pericolosa = true, onConferma = {
            archivia = false
            esegui { Grafo.edo.archiviaPaziente(id); nav.popBackStack() }
        }, onAnnulla = { archivia = false })
    }
}

@Composable
private fun Azioni(testo: String, onClick: () -> Unit) {
    AzioneQuieta(testo, Modifier.padding(horizontal = MargineSchermo), icona = Icons.Rounded.Add, onClick = onClick)
}

fun tonoVisita(s: String) = when (s) {
    "EFFETTUATA" -> Tono.Positivo
    "ANNULLATA" -> Tono.Neutro
    "NON_PRESENTATO" -> Tono.Critico
    else -> Tono.Brace
}

@Composable
fun PazienteForm(id: Long?, onFine: () -> Unit) {
    Schermo(onFine) {
        if (id == null) Modulo(null, onFine) else Async("paziente-form-$id", { Grafo.edo.paziente(id) }, scheletro = {}) { p, _ -> Modulo(p, onFine) }
    }
}

@Composable
private fun Modulo(p: PazienteRiga?, onFine: () -> Unit) {
    var nome by rememberSaveable { mutableStateOf(p?.nome ?: "") }
    var cognome by rememberSaveable { mutableStateOf(p?.cognome ?: "") }
    var email by rememberSaveable { mutableStateOf(p?.email ?: "") }
    var cellulare by rememberSaveable { mutableStateOf(p?.cellulare ?: "") }
    var cf by rememberSaveable { mutableStateOf(p?.codiceFiscale ?: "") }
    var nascita by remember { mutableStateOf(p?.dataNascita) }
    var indirizzo by rememberSaveable { mutableStateOf(p?.indirizzo ?: "") }
    var cap by rememberSaveable { mutableStateOf(p?.cap ?: "") }
    var citta by rememberSaveable { mutableStateOf(p?.citta ?: "") }
    var provincia by rememberSaveable { mutableStateOf(p?.provincia ?: "") }
    var stato by rememberSaveable { mutableStateOf(p?.stato ?: "LEAD") }
    var fonte by rememberSaveable { mutableStateOf(p?.fonte) }
    var situazione by rememberSaveable { mutableStateOf(p?.situazione ?: "") }
    var nota by rememberSaveable { mutableStateOf(p?.nota ?: "") }
    var pesoI by rememberSaveable { mutableStateOf(p?.pesoInizialeKg?.perCampo() ?: "") }
    var pesoO by rememberSaveable { mutableStateOf(p?.pesoObiettivoKg?.perCampo() ?: "") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    ColonnaForm {
        Testo(if (p == null) "Nuovo paziente" else "Modifica", Tipo.Display)
        Campo(nome, { nome = it }, "Nome", capitalizza = KeyboardCapitalization.Words)
        Campo(cognome, { cognome = it }, "Cognome", capitalizza = KeyboardCapitalization.Words)
        Campo(email, { email = it }, "Email", tastiera = KeyboardType.Email, capitalizza = KeyboardCapitalization.None)
        Campo(cellulare, { cellulare = it }, "Cellulare", tastiera = KeyboardType.Phone)
        Scelta("Stato", Etichette.StatoPaziente.toList(), stato) { stato = it }
        Scelta("Fonte", Etichette.Fonte.toList(), fonte) { fonte = it }
        Campo(cf, { cf = it.uppercase() }, "Codice fiscale", capitalizza = KeyboardCapitalization.Characters)
        CampoData("Data di nascita", nascita, opzionale = true) { nascita = it }
        Campo(indirizzo, { indirizzo = it }, "Indirizzo")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo(cap, { cap = it }, "CAP", Modifier.weight(1f), tastiera = KeyboardType.Number)
            Campo(citta, { citta = it }, "Città", Modifier.weight(2f), capitalizza = KeyboardCapitalization.Words)
            Campo(provincia, { provincia = it.uppercase().take(5) }, "Prov.", Modifier.weight(1f), capitalizza = KeyboardCapitalization.Characters)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Campo(pesoI, { pesoI = it }, "Peso iniziale", Modifier.weight(1f), tastiera = KeyboardType.Decimal)
            Campo(pesoO, { pesoO = it }, "Peso obiettivo", Modifier.weight(1f), tastiera = KeyboardType.Decimal)
        }
        Campo(situazione, { situazione = it }, "Situazione", righeMin = 2)
        Campo(nota, { nota = it }, "Nota", righeMin = 2)
        AzioneBrace("Salva", abilitata = nome.isNotBlank() && cognome.isNotBlank(), inCorso = inCorso) {
            inCorso = true
            val corpo = PazienteSalva(
                nome.trim(), cognome.trim(), email.trim().ifBlank { null }, cellulare.trim().ifBlank { null }, cf.trim().ifBlank { null }, nascita,
                indirizzo.ifBlank { null }, cap.ifBlank { null }, citta.ifBlank { null }, provincia.ifBlank { null }, stato, fonte, situazione.ifBlank { null },
                nota.ifBlank { null }, importo(pesoI), importo(pesoO),
            )
            esegui {
                try {
                    if (p == null) Grafo.edo.creaPaziente(corpo) else Grafo.edo.aggiornaPaziente(p.id, corpo)
                    onFine()
                } finally {
                    inCorso = false
                }
            }
        }
    }
}
