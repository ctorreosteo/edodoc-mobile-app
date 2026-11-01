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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private fun nomeMese(m: Int) = Month.of(m).getDisplayName(TextStyle.FULL, Locale.ITALIAN)

/** Una sola serie, il lordo del mese: si tocca una barra per leggerne il valore. */
@Composable
private fun BarreMesi(mesi: List<FinanceMese>, attivo: Int?, onMese: (Int) -> Unit) {
    val c = colori
    val massimo = mesi.maxOfOrNull { it.lordo.toFloat() }?.coerceAtLeast(1f) ?: 1f
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).padding(horizontal = MargineSchermo)
            .pointerInput(mesi) { detectTapGestures { p -> onMese(((p.x / size.width) * 12).toInt().coerceIn(0, 11) + 1) } }
            .semantics { contentDescription = "Incassi lordi per mese. L'elenco dei mesi è qui sotto." },
    ) {
        val passo = size.width / 12
        val larghezza = passo * .56f
        drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        mesi.forEach { m ->
            val h = (m.lordo.toFloat() / massimo * size.height).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                if (attivo == null || attivo == m.mese) c.ember else c.ember.copy(alpha = .3f),
                Offset((m.mese - 1) * passo + (passo - larghezza) / 2, size.height - h), Size(larghezza, h), CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
fun FinanceSchermo(onFine: () -> Unit) {
    var anno by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var mese by rememberSaveable { mutableStateOf<Int?>(null) }
    var vista by rememberSaveable { mutableStateOf("Riepilogo") }
    var movimento by remember { mutableStateOf<Pair<Boolean, Movimento?>?>(null) }
    var scadenza by remember { mutableStateOf<Pair<Boolean, Scadenza?>?>(null) }
    var paga by remember { mutableStateOf<Scadenza?>(null) }
    Schermo(onFine, sopra = {
        when (vista) {
            "Movimenti" -> Aggiungi("Nuovo movimento") { movimento = true to null }
            "Scadenze" -> Aggiungi("Nuova scadenza") { scadenza = true to null }
        }
    }) {
        Async("finance-$anno", { Triple(Grafo.edo.finance(anno), Grafo.edo.movimenti(anno), Grafo.edo.scadenze(anno)) }) { (f, movimenti, scadenze), r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Finance $anno", f.saldoCassa.euroTondo(), "Saldo di cassa")
                item {
                    Sfoglia("$anno", { anno-- }, { anno++ })
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MargineSchermo, vertical = 10.dp)) {
                        Scelta(null, listOf("Riepilogo", "Movimenti", "Scadenze").map { it to it }, vista) { vista = it }
                    }
                }
                when (vista) {
                    "Riepilogo" -> {
                        item {
                            val m = mese?.let { n -> f.mesi.firstOrNull { it.mese == n } }
                            Column(Modifier.padding(horizontal = MargineSchermo).padding(bottom = 10.dp)) {
                                Numero((m?.lordo ?: f.totali.lordo).euroTondo(), m?.let { "Lordo di ${nomeMese(it.mese)}" } ?: "Lordo dell'anno")
                            }
                            BarreMesi(f.mesi, mese) { mese = if (mese == it) null else it }
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = MargineSchermo), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                Numero(f.totali.netto.euroTondo(), "Netto")
                                Numero(f.totali.uscite.euroTondo(), "Uscite")
                            }
                            if (f.candidatureAperte) {
                                Spacer(Modifier.height(12.dp))
                                Pastiglia("Candidature da chiudere", Tono.Attenzione, Modifier.padding(horizontal = MargineSchermo))
                            }
                        }
                        sezione("Livelli")
                        items(f.livelli, key = { it.nome }) { l ->
                            Riga(l.nome, "${l.totale.euroTondo()} · margine soglia ${l.margineSoglia.euroTondo()} · obiettivo ${l.margineObiettivo.euroTondo()}")
                        }
                        sezione("Mesi")
                        items(f.mesi, key = { "m${it.mese}" }) { m ->
                            Riga(nomeMese(m.mese).capitalizzato(), "Lordo ${m.lordo.euroTondo()} · netto ${m.netto.euroTondo()} · uscite ${m.uscite.euroTondo()}")
                        }
                    }
                    "Movimenti" -> {
                        if (movimenti.isEmpty()) item { Vuoto("Nessun movimento", "Entrate e uscite fuori dai pagamenti.") }
                        items(movimenti, key = { it.id }) { m ->
                            Riga(m.descrizione, "${m.data.breve()} · ${Etichette.Categoria[m.categoria] ?: m.categoria}",
                                fine = { Testo((if (m.tipo == "USCITA") "−" else "+") + m.importo.euro(), Tipo.Numero) }, onClick = { movimento = true to m })
                        }
                    }
                    else -> {
                        if (scadenze.isEmpty()) item { Vuoto("Nessuna scadenza", "Quest'anno è libero.") }
                        items(scadenze, key = { it.id }) { s ->
                            Riga(s.titolo, "${s.dataScadenza.breve()}" + (s.importoStimato?.let { " · ${it.euro()}" } ?: ""),
                                fine = { Pastiglia(if (s.stato == "PAGATA") "Pagata" else "Da pagare", if (s.stato == "PAGATA") Tono.Positivo else Tono.Attenzione) },
                                onClick = { if (s.stato == "PAGATA") scadenza = true to s else paga = s })
                        }
                    }
                }
                piede()
            }
            movimento?.let { (_, m) -> MovimentoFoglio(m, { movimento = null }, r.esegui) }
            scadenza?.let { (_, s) -> ScadenzaFoglio(s, { scadenza = null }, r.esegui) }
            paga?.let { s -> PagaFoglio(s, { paga = null }, { paga = null; scadenza = true to s }, r.esegui) }
        }
    }
}

@Composable
private fun MovimentoFoglio(m: Movimento?, onChiudi: () -> Unit, ricarica: () -> Unit) {
    var tipo by rememberSaveable { mutableStateOf(m?.tipo ?: "USCITA") }
    var categoria by rememberSaveable { mutableStateOf(m?.categoria) }
    var descrizione by rememberSaveable { mutableStateOf(m?.descrizione ?: "") }
    var valore by rememberSaveable { mutableStateOf(m?.importo?.perCampo() ?: "") }
    var data by remember { mutableStateOf<LocalDate?>(m?.data ?: LocalDate.now()) }
    var note by rememberSaveable { mutableStateOf(m?.note ?: "") }
    val esegui = azione()
    val categorie = if (tipo == "ENTRATA") Etichette.CategorieEntrata else Etichette.CategorieUscita
    val i = importo(valore)
    Foglio(onChiudi) {
        Testo(if (m == null) "Nuovo movimento" else "Movimento", Tipo.Title)
        Scelta(null, listOf("ENTRATA" to "Entrata", "USCITA" to "Uscita"), tipo) { tipo = it; categoria = null }
        Scelta("Categoria", categorie.toList(), categoria) { categoria = it }
        Campo(descrizione, { descrizione = it }, "Descrizione")
        Campo(valore, { valore = it }, "Importo in euro", tastiera = KeyboardType.Decimal)
        CampoData("Data", data) { data = it }
        Campo(note, { note = it }, "Note")
        AzioneBrace("Salva", abilitata = categoria != null && descrizione.isNotBlank() && i != null && i.signum() > 0 && data != null) {
            val corpo = MovimentoSalva(data!!, tipo, categoria!!, descrizione.trim(), i!!, note.ifBlank { null })
            esegui {
                if (m == null) Grafo.edo.creaMovimento(corpo) else Grafo.edo.aggiornaMovimento(m.id, corpo)
                onChiudi()
                ricarica()
            }
        }
        if (m != null) AzioneQuieta("Elimina", tono = Tono.Critico) { esegui { Grafo.edo.eliminaMovimento(m.id); onChiudi(); ricarica() } }
    }
}

@Composable
private fun ScadenzaFoglio(s: Scadenza?, onChiudi: () -> Unit, ricarica: () -> Unit) {
    var titolo by rememberSaveable { mutableStateOf(s?.titolo ?: "") }
    var categoria by rememberSaveable { mutableStateOf(s?.categoria) }
    var data by remember { mutableStateOf<LocalDate?>(s?.dataScadenza ?: LocalDate.now()) }
    var stima by rememberSaveable { mutableStateOf(s?.importoStimato?.perCampo() ?: "") }
    var note by rememberSaveable { mutableStateOf(s?.note ?: "") }
    val esegui = azione()
    Foglio(onChiudi) {
        Testo(if (s == null) "Nuova scadenza" else "Scadenza", Tipo.Title)
        Campo(titolo, { titolo = it }, "Titolo")
        Scelta("Categoria", Etichette.CategorieUscita.toList(), categoria) { categoria = it }
        CampoData("Data", data) { data = it }
        Campo(stima, { stima = it }, "Importo stimato", tastiera = KeyboardType.Decimal)
        Campo(note, { note = it }, "Note")
        AzioneBrace("Salva", abilitata = titolo.isNotBlank() && categoria != null && data != null) {
            val corpo = ScadenzaSalva(titolo.trim(), categoria!!, data!!, importo(stima), note.ifBlank { null })
            esegui {
                if (s == null) Grafo.edo.creaScadenza(corpo) else Grafo.edo.aggiornaScadenza(s.id, corpo)
                onChiudi()
                ricarica()
            }
        }
        if (s != null) AzioneQuieta("Elimina", tono = Tono.Critico) { esegui { Grafo.edo.eliminaScadenza(s.id); onChiudi(); ricarica() } }
    }
}

@Composable
private fun PagaFoglio(s: Scadenza, onChiudi: () -> Unit, onModifica: () -> Unit, ricarica: () -> Unit) {
    var data by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var valore by rememberSaveable { mutableStateOf(s.importoStimato?.perCampo() ?: "") }
    val esegui = azione()
    val i = importo(valore)
    Foglio(onChiudi) {
        Testo(s.titolo, Tipo.Title)
        CampoData("Data del pagamento", data) { data = it }
        Campo(valore, { valore = it }, "Importo in euro", tastiera = KeyboardType.Decimal)
        AzioneBrace("Segna come pagata", abilitata = i != null && i.signum() > 0 && data != null) {
            esegui { Grafo.edo.pagaScadenza(s.id, ScadenzaPaga(data!!, i!!)); onChiudi(); ricarica() }
        }
        AzioneQuieta("Modifica", icona = Icons.Rounded.Edit, onClick = onModifica)
    }
}

@Composable
fun TaskSchermo(onFine: () -> Unit) {
    var completati by rememberSaveable { mutableStateOf(false) }
    var form by remember { mutableStateOf<Pair<Boolean, TaskRiga?>?>(null) }
    val esegui = azione()
    Schermo(onFine, sopra = { Aggiungi("Nuovo task") { form = true to null } }) {
        Async("task-$completati", { Grafo.edo.task(completati) to Grafo.edo.tipiTask() }) { (task, tipi), r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Promemoria", "Task")
                item {
                    Box(Modifier.padding(horizontal = MargineSchermo).padding(bottom = 8.dp)) {
                        Scelta(null, listOf(false to "Da fare", true to "Anche fatti"), completati) { completati = it }
                    }
                }
                if (task.isEmpty()) item { Vuoto("Niente da fare", "Goditi il momento.") }
                items(task, key = { it.id }) { t ->
                    val fatto = t.stato == "FATTO"
                    Riga(
                        t.titolo, listOfNotNull(t.pazienteNome, t.scadenza?.breve()).joinToString(" · "),
                        inizio = {
                            Box(
                                Modifier.size(44.dp).clickable(role = Role.Checkbox) { esegui { Grafo.edo.statoTask(t.id, !fatto); r.esegui() } }
                                    .semantics { contentDescription = if (fatto) "Segna da fare" else "Segna fatto" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (fatto) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (fatto) colori.positive else colori.inkTertiary) }
                        },
                        fine = {
                            when {
                                t.inRitardo -> Pastiglia("In ritardo", Tono.Critico)
                                t.priorita == "ALTA" && !fatto -> Pastiglia("Alta", Tono.Attenzione)
                                else -> {}
                            }
                        },
                        onClick = { form = true to t },
                    )
                }
                piede()
            }
            form?.let { (_, t) -> TaskFoglio(t, tipi, { form = null }, r.esegui) }
        }
    }
}

@Composable
private fun TaskFoglio(t: TaskRiga?, tipi: List<TaskTipo>, onChiudi: () -> Unit, ricarica: () -> Unit) {
    var tipo by rememberSaveable { mutableStateOf(t?.tipo?.codice) }
    var titolo by rememberSaveable { mutableStateOf(t?.titolo ?: "") }
    var descrizione by rememberSaveable { mutableStateOf(t?.descrizione ?: "") }
    var scadenza by remember { mutableStateOf(t?.scadenza) }
    var priorita by rememberSaveable { mutableStateOf(t?.priorita) }
    var paziente by rememberSaveable { mutableStateOf(t?.pazienteId) }
    val esegui = azione()
    Foglio(onChiudi) {
        Testo(if (t == null) "Nuovo task" else "Task", Tipo.Title)
        Scelta("Tipo", tipi.map { it.codice to it.etichetta }, tipo) { tipo = it }
        Campo(titolo, { titolo = it }, "Titolo", aiuto = "Vuoto: quello predefinito del tipo")
        Campo(descrizione, { descrizione = it }, "Descrizione", righeMin = 2)
        CampoData("Scadenza", scadenza, opzionale = true) { scadenza = it }
        Scelta("Priorità", Etichette.Priorita.toList(), priorita) { priorita = it }
        ScegliPaziente(paziente) { paziente = it.id }
        AzioneBrace("Salva", abilitata = tipo != null) {
            val corpo = TaskSalva(tipo!!, titolo.ifBlank { null }, descrizione.ifBlank { null }, scadenza, priorita, paziente)
            esegui {
                if (t == null) Grafo.edo.creaTask(corpo) else Grafo.edo.aggiornaTask(t.id, corpo)
                onChiudi()
                ricarica()
            }
        }
        if (t != null) AzioneQuieta("Elimina", tono = Tono.Critico) { esegui { Grafo.edo.eliminaTask(t.id); onChiudi(); ricarica() } }
    }
}

private enum class TipoVoce { Testo, Numero, Intero, Interruttore }

/** Il backend non valida i valori: un testo in un campo numerico fermerebbe fatture e Finance. Stessi tipi del gestionale. */
private fun tipoVoce(chiave: String) = when {
    chiave == "FATTURA_PROSSIMO_NUMERO" || chiave == "CHECK_SOGLIA_ENERGIA_BASSA" -> TipoVoce.Intero
    chiave == "FATTURA_BOLLO_DENTRO_IL_PREZZO" -> TipoVoce.Interruttore
    chiave.startsWith("LISTINO_") || chiave.startsWith("FATTURA_BOLLO_") || chiave == "FATTURA_ALIQUOTA_CONTRIBUTO" ||
        chiave.startsWith("FISCO_") -> TipoVoce.Numero
    else -> TipoVoce.Testo
}

/** Il valore da salvare, o null se non è valido per quel tipo. */
fun valoreVoce(chiave: String, testo: String): String? {
    val pulito = testo.trim()
    return when (tipoVoce(chiave)) {
        TipoVoce.Testo -> pulito
        TipoVoce.Interruttore -> pulito.takeIf { it == "true" || it == "false" }
        TipoVoce.Intero -> pulito.toIntOrNull()?.takeIf { it >= 0 }?.toString()
        TipoVoce.Numero -> pulito.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.toPlainString()
    }
}

/**
 * attiva viene dal backend ed è flag e credenziali insieme: per l'interruttore vale il flag vero in config.
 * Senza credenziali non si accende, altrimenti l'integrazione partirebbe da sola il giorno in cui arrivano.
 * Un flag già acceso si può sempre spegnere.
 */
@Composable
private fun RigaIntegrazione(i: Integrazione, voce: Voce?, onCambia: (Boolean) -> Unit) {
    val acceso = voce?.valore?.trim().equals("true", ignoreCase = true)
    val (stato, tono) = when {
        acceso && i.configurata -> "Attiva" to Tono.Positivo
        acceso -> "In attesa" to Tono.Attenzione
        i.configurata -> "Spenta" to Tono.Neutro
        else -> "Da collegare" to Tono.Neutro
    }
    Column(Modifier.padding(horizontal = MargineSchermo).padding(top = 10.dp)) {
        Pastiglia(stato, tono)
        Interruttore(i.nome, acceso, i.nota, abilitato = i.configurata || acceso, onCambia = onCambia)
        Filetto()
    }
}

@Composable
fun ImpostazioniSchermo(onFine: () -> Unit) {
    var voce by remember { mutableStateOf<Voce?>(null) }
    val esegui = azione()
    Schermo(onFine) {
        Async("impostazioni", { Grafo.edo.impostazioni() }) { p, r ->
            val interruttori = p.integrazioni.map { it.codice }.toSet()
            LazyColumn(Modifier.fillMaxSize()) {
                testata("EdoDoc HQ", "Impostazioni")
                sezione("Integrazioni")
                items(p.integrazioni, key = { it.codice }) { i ->
                    RigaIntegrazione(i, p.voci.firstOrNull { it.chiave == i.codice }) { si ->
                        esegui { Grafo.edo.aggiornaImpostazione(i.codice, si.toString()); r.esegui() }
                    }
                }
                sezione("Parametri e modelli")
                items(p.voci.filter { it.chiave !in interruttori }, key = { it.chiave }) { v ->
                    Riga(v.descrizione ?: v.chiave, v.valore, onClick = { voce = v })
                }
                piede()
            }
            voce?.let { v ->
                var valore by rememberSaveable(v.chiave) { mutableStateOf(v.valore ?: "") }
                val tipo = tipoVoce(v.chiave)
                val pronto = valoreVoce(v.chiave, valore)
                Foglio({ voce = null }) {
                    Testo(v.descrizione ?: v.chiave, Tipo.Title)
                    when (tipo) {
                        TipoVoce.Interruttore -> Interruttore("Attivo", valore == "true") { valore = it.toString() }
                        TipoVoce.Testo -> Campo(valore, { valore = it }, "Valore", righeMin = 4)
                        else -> Campo(
                            valore, { valore = it }, "Valore", tastiera = if (tipo == TipoVoce.Intero) KeyboardType.Number else KeyboardType.Decimal,
                            aiuto = if (v.chiave == "FATTURA_PROSSIMO_NUMERO") "Avanza da solo. Cambialo solo per allinearti alla numerazione esistente" else null,
                        )
                    }
                    if (pronto == null) Testo("Serve un numero valido", Tipo.Label, colori.critical)
                    AzioneBrace("Salva", abilitata = pronto != null) {
                        esegui { Grafo.edo.aggiornaImpostazione(v.chiave, pronto); voce = null; r.esegui() }
                    }
                }
            }
        }
    }
}
