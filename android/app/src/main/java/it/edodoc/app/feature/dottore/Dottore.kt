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
import it.edodoc.app.core.push.Destinazione
import it.edodoc.app.feature.comune.AnteprimaPdf
import it.edodoc.app.feature.comune.ChatSchermo
import it.edodoc.app.feature.comune.ProfiloSchermo
import it.edodoc.app.ui.nav.Scheda
import it.edodoc.app.ui.nav.Shell
import it.edodoc.app.ui.nav.long
import it.edodoc.app.ui.nav.rotta
import it.edodoc.app.ui.nav.scheda
import it.edodoc.app.ui.nav.testo
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay

fun cod(s: String): String = android.net.Uri.encode(s)

@Composable
fun AppDottore(utente: Utente, destinazione: MutableState<Destinazione?>) {
    val nav = rememberNavController()
    var nonLetti by remember { mutableLongStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { nonLetti = Grafo.edo.conversazioni().sumOf { it.nonLetti } }
            delay(30_000)
        }
    }
    LaunchedEffect(destinazione.value) {
        val d = destinazione.value ?: return@LaunchedEffect
        destinazione.value = null
        if (d.tipo == "MESSAGGIO") {
            nav.scheda("conversazioni")
            d.pazienteId?.let { nav.navigate("chat/$it?nome=" + cod("Paziente")) }
        }
    }
    val schede = listOf(
        Scheda("oggi", "Oggi", Icons.Rounded.WbTwilight),
        Scheda("pazienti", "Pazienti", Icons.Rounded.Groups),
        Scheda("settimana", "Check", Icons.Rounded.Checklist),
        Scheda("conversazioni", "Chat", Icons.Rounded.ChatBubble, nonLetti),
        Scheda("altro", "Altro", Icons.Rounded.GridView),
    )
    val su: () -> Unit = { nav.popBackStack() }
    Shell(nav, "oggi", schede) {
        rotta("oggi") { Mattina(utente, nav) }
        rotta("pazienti") { PazientiSchermo(nav) }
        rotta("settimana") { SettimanaSchermo(nav) }
        rotta("conversazioni") { ConversazioniSchermo(nav) }
        rotta("altro") { AltroSchermo(nav) }
        rotta("profilo") { ProfiloSchermo(utente, su) }

        rotta("paziente/{id}", numeri = listOf("id")) { SchedaPaziente(it.long("id"), nav) }
        rotta("pazienteForm?id={id}", testi = listOf("id")) { PazienteForm(it.testo("id")?.toLongOrNull(), su) }
        rotta("chat/{id}?nome={nome}", numeri = listOf("id"), testi = listOf("nome")) { ChatSchermo(it.long("id"), it.testo("nome") ?: "Chat", su) }
        rotta("checkDettaglio/{paziente}/{id}", numeri = listOf("paziente", "id")) { CheckDottore(it.long("paziente"), it.long("id"), su) }
        rotta("checkNuovo/{paziente}?data={data}", numeri = listOf("paziente"), testi = listOf("data")) { CheckManuale(it.long("paziente"), it.testo("data")?.let(LocalDate::parse), su) }

        rotta("agenda") { AgendaSchermo(nav) }
        rotta("visitaForm?paziente={paziente}&id={id}", testi = listOf("paziente", "id")) {
            VisitaForm(it.testo("paziente")?.toLongOrNull(), it.testo("id")?.toLongOrNull(), su)
        }
        rotta("acquisti") { AcquistiSchermo(nav) }
        rotta("percorsoForm?paziente={paziente}", testi = listOf("paziente")) { PercorsoForm(it.testo("paziente")?.toLongOrNull(), su) }
        rotta("pagamenti") { PagamentiSchermo(nav) }
        rotta("pagamentoForm?paziente={paziente}", testi = listOf("paziente")) { PagamentoForm(it.testo("paziente")?.toLongOrNull(), null, su) }
        rotta("fatture") { FattureSchermo(nav) }
        rotta("fatturaForm?pagamento={pagamento}&paziente={paziente}", testi = listOf("pagamento", "paziente")) {
            FatturaForm(it.testo("pagamento")?.toLongOrNull(), it.testo("paziente")?.toLongOrNull(), su)
        }
        rotta("tessera") { TesseraSchermo(su) }
        rotta("pdfDottore/{id}?nome={nome}", numeri = listOf("id"), testi = listOf("nome")) { e ->
            val id = e.long("id")
            AnteprimaPdf("d$id", e.testo("nome") ?: "fattura.pdf", { Grafo.edo.scaricaFatturaDottore(id, it) }, su)
        }
        rotta("corsi") { CorsiDottore(nav) }
        rotta("corsoEdit/{id}", numeri = listOf("id")) { CorsoEdit(it.long("id"), su) }
        rotta("finance") { FinanceSchermo(su) }
        rotta("task") { TaskSchermo(su) }
        rotta("impostazioni") { ImpostazioniSchermo(su) }
    }
}

/** La mattina si apre sul numero di ciò che chiede attenzione. */
@Composable
private fun Mattina(utente: Utente, nav: NavHostController) {
    Schermo(fuoco = Offset(.82f, .16f), forza = 1.1f) {
        Async("dashboard", { Grafo.edo.dashboard() }) { d, _ ->
            val attenzione = d.checkConSegnali + d.taskInRitardo.toInt() + d.visiteOggi.size
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.statusBarsPadding().padding(start = MargineSchermo, end = MargineSchermo, top = 18.dp, bottom = 22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Sopratitolo(LocalDate.now().lungo(), Modifier.weight(1f))
                            Box(
                                Modifier.size(44.dp).clip(Forme.Capsula).background(colori.ink.copy(alpha = .08f))
                                    .clickable(role = Role.Button) { nav.navigate("profilo") }.semantics { contentDescription = "Il tuo account" },
                                contentAlignment = Alignment.Center,
                            ) { Testo((utente.nome ?: "E").take(1), Tipo.Label) }
                        }
                        Spacer(Modifier.height(34.dp))
                        Text("$attenzione", style = Tipo.DisplayXL.copy(fontSize = Tipo.DisplayXL.fontSize * 1.5f, lineHeight = Tipo.DisplayXL.lineHeight * 1.5f), color = if (attenzione > 0) colori.ember else colori.ink)
                        Testo(
                            if (attenzione == 0) "Niente chiede attenzione, oggi." else if (attenzione == 1) "cosa chiede la tua attenzione" else "cose chiedono la tua attenzione",
                            Tipo.Headline, colori.inkSecondary,
                        )
                    }
                }
                if (d.visiteOggi.isNotEmpty()) {
                    sezione("Visite di oggi")
                    items(d.visiteOggi, key = { "v${it.id}" }) { v ->
                        Riga(v.pazienteNome, "${v.inizio.ora()} · ${Etichette.TipoVisita[v.tipo]}" + (v.numeroVisita?.let { " $it di ${v.visiteTotali}" } ?: ""), onClick = { nav.navigate("agenda") })
                    }
                }
                sezione("Check della settimana")
                item {
                    Riga("${d.checkCompilati} di ${d.checkAttesi} compilati", if (d.checkConSegnali > 0) "${d.checkConSegnali} con segnali" else "Nessun segnale",
                        fine = { if (d.checkConSegnali > 0) Pastiglia("${d.checkConSegnali}", Tono.Attenzione) }, onClick = { nav.scheda("settimana") })
                }
                if (d.taskUrgenti.isNotEmpty()) {
                    sezione("Task urgenti")
                    items(d.taskUrgenti, key = { "t${it.id}" }) { t ->
                        Riga(t.titolo, listOfNotNull(t.pazienteNome, t.scadenza?.breve()).joinToString(" · "),
                            fine = { if (t.inRitardo) Pastiglia("In ritardo", Tono.Critico) }, onClick = { nav.navigate("task") })
                    }
                }
                if (d.rinnovi.isNotEmpty()) {
                    sezione("Rinnovi da proporre")
                    items(d.rinnovi, key = { "r${it.percorsoId}" }) { r ->
                        Riga(r.pazienteNome, "Visita ${r.visiteFissate} di ${r.visiteTotali}", onClick = { nav.navigate("paziente/${r.pazienteId}") })
                    }
                }
                sezione("Soldi")
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = MargineSchermo, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Numero(d.incassatoMese.euroTondo(), "Questo mese")
                        Numero(d.daIncassare.euroTondo(), "Da incassare")
                    }
                    if (d.scaduto.signum() > 0) {
                        Riga("Pagamenti scaduti", d.scaduto.euro(), fine = { Pastiglia("Scaduto", Tono.Critico) }, onClick = { nav.navigate("pagamenti") })
                    }
                }
                if (d.prossimeScadenze.isNotEmpty()) {
                    sezione("Scadenze fiscali")
                    items(d.prossimeScadenze, key = { "s${it.id}" }) { s ->
                        Riga(s.titolo, "${s.dataScadenza.breve()}" + (s.importoStimato?.let { " · ${it.euro()}" } ?: ""), onClick = { nav.navigate("finance") })
                    }
                }
                piede()
            }
        }
    }
}

@Composable
private fun ConversazioniSchermo(nav: NavHostController) {
    Schermo {
        Async("conversazioni", { Grafo.edo.conversazioni() }) { lista, r ->
            LaunchedEffect(Unit) {
                while (true) {
                    delay(10_000)
                    r.esegui()
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Conversazioni", "Chat", conBarra = false)
                if (lista.isEmpty()) item { Vuoto("Nessuna conversazione", "Quando un paziente ti scrive, la trovi qui.") }
                items(lista, key = { it.pazienteId }) { c ->
                    Riga(
                        c.pazienteNome,
                        c.ultimoMessaggio?.let { (if (it.mittente == "DOTTORE") "Tu: " else "") + it.testo },
                        fine = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                c.ultimoMessaggio?.let { Testo(it.inviatoIl.toLocalDate().corto(), Tipo.Label, colori.inkSecondary) }
                                Conteggio(c.nonLetti)
                            }
                        },
                        onClick = { nav.navigate("chat/${c.pazienteId}?nome=" + cod(c.pazienteNome)) },
                    )
                }
                piede()
            }
        }
    }
}

@Composable
private fun AltroSchermo(nav: NavHostController) {
    val voci = listOf(
        Triple("agenda", "Agenda", "Visite e stati" to Icons.Rounded.CalendarMonth),
        Triple("acquisti", "Acquisti", "Percorsi e rate" to Icons.Rounded.Sell),
        Triple("pagamenti", "Pagamenti", "Incassi e solleciti" to Icons.Rounded.Payments),
        Triple("fatture", "Fatture", "Emissione e Tessera Sanitaria" to Icons.AutoMirrored.Rounded.ReceiptLong),
        Triple("corsi", "Corsi", "Moduli, lezioni e video" to Icons.Rounded.AutoStories),
        Triple("finance", "Finance", "Cassa, livelli e scadenze" to Icons.Rounded.AccountBalance),
        Triple("task", "Task", "Promemoria" to Icons.Rounded.TaskAlt),
        Triple("impostazioni", "Impostazioni", "Integrazioni e modelli email" to Icons.Rounded.Tune),
    )
    Schermo {
        LazyColumn(Modifier.fillMaxSize()) {
            testata("EdoDoc HQ", "Tutto il resto", conBarra = false)
            items(voci, key = { it.first }) { (rotta, titolo, dettaglio) ->
                Riga(titolo, dettaglio.first, inizio = { Icon(dettaglio.second, null, tint = colori.ember, modifier = Modifier.size(24.dp)) }, onClick = { nav.navigate(rotta) })
            }
            piede()
        }
    }
}

/** Scelta del paziente nei moduli: cerca per nome tra tutti gli attivi. */
@Composable
fun ScegliPaziente(valore: Long?, modifier: Modifier = Modifier, onValore: (PazienteRiga) -> Unit) {
    var aperto by rememberSaveable { mutableStateOf(false) }
    var elenco by remember { mutableStateOf<List<PazienteRiga>>(emptyList()) }
    var cerca by rememberSaveable { mutableStateOf("") }
    val esegui = azione()
    LaunchedEffect(Unit) { esegui { elenco = Grafo.edo.pazienti() } }
    CampoSelezione("Paziente", elenco.firstOrNull { it.id == valore }?.nomeCompleto ?: "Scegli", modifier) { aperto = true }
    if (aperto) {
        Foglio({ aperto = false }) {
            Campo(cerca, { cerca = it }, "Cerca", capitalizza = KeyboardCapitalization.Words)
            elenco.filter { it.nomeCompleto.contains(cerca, ignoreCase = true) }.take(40).forEach { p ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onValore(p); aperto = false }.padding(vertical = 12.dp)) {
                    Testo(p.nomeCompleto)
                }
            }
        }
    }
}

/** Un importo scritto all'italiana: 1.500,50. null se non è un numero. */
fun importo(testo: String): BigDecimal? =
    testo.trim().replace(".", "").replace(",", ".").toBigDecimalOrNull()

fun BigDecimal.perCampo(): String = stripTrailingZeros().toPlainString().replace('.', ',')

/** Mese precedente e successivo, per scorrere gli elenchi per periodo. */
@Composable
fun Sfoglia(etichetta: String, indietro: () -> Unit, avanti: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BottoneIcona(Icons.Rounded.ChevronLeft, "Precedente", vetro = false, onClick = indietro)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Testo(etichetta.capitalizzato(), Tipo.Headline) }
        BottoneIcona(Icons.Rounded.ChevronRight, "Successivo", vetro = false, onClick = avanti)
    }
}

fun YearMonth.nome(): String = atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ITALIAN))

/** Bottone rotondo in brace per creare: sta sopra alla capsula, nell'unico punto del pollice. */
@Composable
fun BoxScope.Aggiungi(descrizione: String, conBarra: Boolean = false, onClick: () -> Unit) {
    val barra = if (conBarra) LocalSpazioBarra.current else 0.dp
    Box(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 22.dp, bottom = barra + 16.dp).size(58.dp).clip(Forme.Capsula)
            .background(colori.ember).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = descrizione },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, null, tint = colori.onEmber, modifier = Modifier.size(26.dp)) }
}
