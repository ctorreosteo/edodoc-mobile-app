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

private fun stato(r: PagamentoRiga): Pair<String, Tono> = when {
    r.stato == "PAGATO" -> "Pagato" to Tono.Positivo
    r.stato == "ANNULLATO" -> "Annullato" to Tono.Neutro
    r.scaduto -> "Scaduto" to Tono.Critico
    else -> "Da pagare" to Tono.Attenzione
}

@Composable
fun AcquistiSchermo(nav: NavHostController) {
    var annulla by remember { mutableStateOf<Acquisto?>(null) }
    val esegui = azione()
    Schermo({ nav.popBackStack() }, sopra = { Aggiungi("Nuovo percorso") { nav.navigate("percorsoForm") } }) {
        Async("acquisti", { Grafo.edo.acquisti() }) { a, r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Chi ha comprato cosa", "Acquisti")
                if (a.percorsi.isEmpty()) item { Vuoto("Nessun acquisto", "Crea il primo percorso.") }
                items(a.percorsi, key = { it.percorso.id }) { c ->
                    Riga(
                        c.pazienteNome,
                        "${Etichette.TipoPercorso[c.percorso.tipo]} · ${c.pagato.euroTondo()} versati" + if (c.daPagare.signum() > 0) ", ${c.daPagare.euroTondo()} da pagare" else "",
                        fine = {
                            when {
                                c.percorso.stato == "ANNULLATO" -> Pastiglia("Annullato")
                                c.scaduto.signum() > 0 -> Pastiglia("Scaduto", Tono.Critico)
                                c.daPagare.signum() > 0 -> Pastiglia("Rate", Tono.Attenzione)
                                else -> Pastiglia("Saldato", Tono.Positivo)
                            }
                        },
                        onClick = { annulla = c },
                    )
                }
                if (a.fuoriPercorso.isNotEmpty()) {
                    sezione("Fuori percorso")
                    items(a.fuoriPercorso, key = { "f${it.id}" }) { p -> RigaPagamento(p, nav, r.esegui) }
                }
                piede()
            }
            annulla?.let { c ->
                Foglio({ annulla = null }) {
                    Testo(c.pazienteNome, Tipo.Title)
                    Testo("${Etichette.TipoPercorso[c.percorso.tipo]} · ${Etichette.Piano[c.percorso.pianoPagamento]} · ${c.percorso.importoTotale.euro()}", colore = colori.inkSecondary)
                    c.pagamenti.forEach { p -> RigaBreve(p) }
                    AzioneQuieta("Scheda paziente", icona = Icons.Rounded.Person) { annulla = null; nav.navigate("paziente/${c.percorso.pazienteId}") }
                    if (c.percorso.stato == "ATTIVO") {
                        var conferma by remember { mutableStateOf(false) }
                        AzioneQuieta("Annulla percorso", tono = Tono.Critico) { conferma = true }
                        if (conferma) {
                            Conferma("Annullare il percorso?", "Le rate non pagate verranno annullate.", "Annulla percorso", pericolosa = true, onConferma = {
                                annulla = null
                                esegui { Grafo.edo.annullaPercorso(c.percorso.id); r.esegui() }
                            }, onAnnulla = { conferma = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RigaBreve(p: PagamentoRiga) {
    val (s, t) = stato(p)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Testo(p.descrizione)
            Testo("${p.importo.euro()} · ${p.scadenza.breve()}", Tipo.Label, colori.inkSecondary)
        }
        Pastiglia(s, t)
    }
}

@Composable
fun PercorsoForm(paziente: Long?, onFine: () -> Unit) {
    var pazienteId by rememberSaveable { mutableStateOf(paziente) }
    var tipo by rememberSaveable { mutableStateOf("METODO_BUSSOLA") }
    var piano by rememberSaveable { mutableStateOf("UNICO") }
    var inizio by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var note by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    Schermo(onFine) {
        Async("listino", { Grafo.edo.listino() }) { l, _ ->
            val mantenimento = tipo == "MANTENIMENTO"
            val prezzo = when {
                mantenimento && piano == "UNICO" -> l.mantenimento.euroTondo()
                mantenimento -> "2 rate da ${l.rataMantenimento.euroTondo()}"
                piano == "UNICO" -> l.unico.euroTondo()
                else -> "3 rate da ${l.rataPercorso.euroTondo()}"
            }
            ColonnaForm {
                Testo("Nuovo percorso", Tipo.Display)
                ScegliPaziente(pazienteId) { pazienteId = it.id }
                Scelta("Tipo", Etichette.TipoPercorso.toList(), tipo) { tipo = it }
                Scelta("Piano di pagamento", Etichette.Piano.toList(), piano) { piano = it }
                Numero(prezzo, "Dal listino")
                CampoData("Data di inizio", inizio) { inizio = it }
                Campo(note, { note = it }, "Note", righeMin = 2)
                AzioneBrace("Crea il percorso", abilitata = pazienteId != null && inizio != null, inCorso = inCorso) {
                    inCorso = true
                    esegui {
                        try {
                            Grafo.edo.creaPercorso(PercorsoCrea(pazienteId!!, tipo, piano, inizio!!, note.ifBlank { null }))
                            onFine()
                        } finally {
                            inCorso = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PagamentiSchermo(nav: NavHostController) {
    var mese by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(mese)
    Schermo({ nav.popBackStack() }, sopra = { Aggiungi("Nuovo pagamento") { nav.navigate("pagamentoForm") } }) {
        Async("pagamenti-$mese", { Grafo.edo.pagamentiPeriodo(ym.atDay(1), ym.atEndOfMonth()) }) { l, r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Pagamenti", ym.nome().capitalizzato())
                item {
                    Sfoglia("Mese", { mese = ym.minusMonths(1).toString() }, { mese = ym.plusMonths(1).toString() })
                    Row(Modifier.fillMaxWidth().padding(horizontal = MargineSchermo, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Numero(l.riepilogo.pagato.euroTondo(), "Pagato")
                        Numero(l.riepilogo.daPagare.euroTondo(), "Da pagare")
                        if (l.riepilogo.scaduto.signum() > 0) Numero(l.riepilogo.scaduto.euroTondo(), "Scaduto", colore = colori.critical)
                    }
                }
                if (l.righe.isEmpty()) item { Vuoto("Nessun pagamento", "In questo mese non c'è nulla.") }
                items(l.righe, key = { it.id }) { p -> RigaPagamento(p, nav, r.esegui) }
                piede()
            }
        }
    }
}

/** Una rata con tutte le sue azioni: registra, sollecita, fattura, annulla. */
@Composable
fun RigaPagamento(p: PagamentoRiga, nav: NavHostController, ricarica: () -> Unit) {
    var aperto by remember { mutableStateOf(false) }
    var registra by remember { mutableStateOf(false) }
    var modifica by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf<EmailRichiesta?>(null) }
    val esegui = azione()
    val (s, t) = stato(p)
    Riga(p.pazienteNome, "${p.descrizione} · ${p.importo.euro()}", fine = { Pastiglia(s, t) }, onClick = { aperto = true })
    if (aperto) {
        Foglio({ aperto = false }) {
            Testo(p.descrizione, Tipo.Title)
            Testo("${p.pazienteNome} · ${p.importo.euro()} · scadenza ${p.scadenza.breve()}", colore = colori.inkSecondary)
            if (p.stato == "DA_PAGARE") {
                AzioneBrace("Registra il pagamento", icona = Icons.Rounded.Check) { aperto = false; registra = true }
                AzioneQuieta("Sollecita per email", icona = Icons.Rounded.Mail) { aperto = false; email = EmailRichiesta("SOLLECITO_PAGAMENTO", p.pazienteId, p.id) }
                AzioneQuieta("Modifica", icona = Icons.Rounded.Edit) { aperto = false; modifica = true }
                AzioneQuieta("Annulla pagamento", tono = Tono.Critico) { aperto = false; esegui { Grafo.edo.annullaPagamento(p.id); ricarica() } }
            }
            if (p.stato == "PAGATO") {
                if (!p.fatturato) AzioneBrace("Emetti la fattura", icona = Icons.AutoMirrored.Rounded.ReceiptLong) { aperto = false; nav.navigate("fatturaForm?pagamento=${p.id}") }
                else Pastiglia("Fatturato", Tono.Positivo)
                AzioneQuieta("Chiedi i dati di fatturazione", icona = Icons.Rounded.Mail) { aperto = false; email = EmailRichiesta("DATI_FATTURAZIONE", p.pazienteId, p.id) }
                if (!p.fatturato) AzioneQuieta("Annulla registrazione", tono = Tono.Critico) { aperto = false; esegui { Grafo.edo.annullaRegistrazione(p.id); ricarica() } }
            }
            AzioneQuieta("Scheda paziente", icona = Icons.Rounded.Person) { aperto = false; nav.navigate("paziente/${p.pazienteId}") }
        }
    }
    if (registra) Registra(p, { registra = false }, ricarica)
    if (modifica) Foglio({ modifica = false }) { CorpoPagamento(p.pazienteId, p) { modifica = false; ricarica() } }
    email?.let { EmailFoglio(it) { email = null } }
}

@Composable
private fun Registra(p: PagamentoRiga, onChiudi: () -> Unit, ricarica: () -> Unit) {
    var data by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var metodo by rememberSaveable { mutableStateOf("BONIFICO") }
    var contabile by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    Foglio(onChiudi) {
        Testo("Registra ${p.importo.euro()}", Tipo.Title)
        CampoData("Data del pagamento", data) { data = it }
        Scelta("Metodo", Etichette.Metodo.toList(), metodo) { metodo = it }
        Interruttore("Contabile ricevuta", contabile) { contabile = it }
        Campo(note, { note = it }, "Note")
        AzioneBrace("Registra", abilitata = data != null, inCorso = inCorso) {
            inCorso = true
            esegui {
                try {
                    Grafo.edo.registraPagamento(p.id, PagamentoRegistra(data!!, metodo, contabile, note.ifBlank { null }))
                    onChiudi()
                    ricarica()
                } finally {
                    inCorso = false
                }
            }
        }
    }
}

@Composable
fun PagamentoForm(paziente: Long?, p: PagamentoRiga?, onFine: () -> Unit) {
    Schermo(onFine) {
        ColonnaForm {
            Testo("Nuovo pagamento", Tipo.Display)
            CorpoPagamento(paziente, p, onFine)
        }
    }
}

@Composable
private fun CorpoPagamento(paziente: Long?, p: PagamentoRiga?, onFine: () -> Unit) {
    var pazienteId by rememberSaveable { mutableStateOf(paziente) }
    var tipo by rememberSaveable { mutableStateOf(p?.tipo ?: "ALTRO") }
    var descrizione by rememberSaveable { mutableStateOf(p?.descrizione ?: "") }
    var valore by rememberSaveable { mutableStateOf(p?.importo?.perCampo() ?: "") }
    var scadenza by remember { mutableStateOf<LocalDate?>(p?.scadenza ?: LocalDate.now()) }
    var note by rememberSaveable { mutableStateOf(p?.note ?: "") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    if (p == null) ScegliPaziente(pazienteId) { pazienteId = it.id }
    Scelta("Tipo", Etichette.TipoPagamento.toList(), tipo) { tipo = it }
    Campo(descrizione, { descrizione = it }, "Descrizione")
    Campo(valore, { valore = it }, "Importo in euro", tastiera = KeyboardType.Decimal)
    CampoData("Scadenza", scadenza) { scadenza = it }
    Campo(note, { note = it }, "Note")
    val i = importo(valore)
    AzioneBrace("Salva", abilitata = pazienteId != null && descrizione.isNotBlank() && i != null && i.signum() > 0 && scadenza != null, inCorso = inCorso) {
        val corpo = PagamentoSalva(pazienteId!!, tipo, descrizione.trim(), i!!, scadenza!!, note.ifBlank { null })
        inCorso = true
        esegui {
            try {
                if (p == null) Grafo.edo.creaPagamento(corpo) else Grafo.edo.aggiornaPagamento(p.id, corpo)
                onFine()
            } finally {
                inCorso = false
            }
        }
    }
}

/** Il modello dalla config, modificabile prima dell'invio, come nel gestionale. */
@Composable
fun EmailFoglio(r: EmailRichiesta, onChiudi: () -> Unit) {
    var bozza by remember { mutableStateOf<EmailBozza?>(null) }
    var a by rememberSaveable { mutableStateOf("") }
    var oggetto by rememberSaveable { mutableStateOf("") }
    var corpo by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    val avvisi = LocalAvvisi.current
    LaunchedEffect(r) {
        esegui {
            try {
                val b = Grafo.edo.bozzaEmail(r)
                bozza = b
                a = b.destinatario ?: ""
                oggetto = b.oggetto ?: ""
                corpo = b.testo ?: ""
            } catch (e: ApiException) {
                onChiudi()
                throw e
            }
        }
    }
    Foglio(onChiudi) {
        val b = bozza
        if (b == null) {
            ScheletroBlocco(200.dp)
        } else {
            Testo("Email a ${b.pazienteNome ?: "paziente"}", Tipo.Title)
            Campo(a, { a = it }, "Destinatario", tastiera = KeyboardType.Email, capitalizza = KeyboardCapitalization.None)
            Campo(oggetto, { oggetto = it }, "Oggetto")
            Campo(corpo, { corpo = it }, "Testo", righeMin = 6)
            b.allegato?.let { Testo("Allegato: $it", Tipo.Label, colori.inkSecondary) }
            if (!b.invioAttivo) Testo("Gmail è spento: si accende da Impostazioni.", Tipo.Label, colori.warning)
            AzioneBrace("Invia", abilitata = b.invioAttivo && a.isNotBlank() && oggetto.isNotBlank() && corpo.isNotBlank(), inCorso = inCorso) {
                inCorso = true
                esegui {
                    try {
                        Grafo.edo.inviaEmail(EmailInvia(r.tipo, r.pazienteId, r.pagamentoId, r.fatturaId, a.trim(), oggetto, corpo))
                        avvisi.mostra("Email inviata")
                        onChiudi()
                    } finally {
                        inCorso = false
                    }
                }
            }
        }
    }
}

@Composable
fun FattureSchermo(nav: NavHostController) {
    var mese by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(mese)
    Schermo({ nav.popBackStack() }, azioni = {
        BottoneIcona(Icons.Rounded.HealthAndSafety, "Tessera Sanitaria") { nav.navigate("tessera") }
    }, sopra = { Aggiungi("Fattura libera") { nav.navigate("fatturaForm") } }) {
        Async("fatture-$mese", { Grafo.edo.fatturePeriodo(ym.atDay(1), ym.atEndOfMonth()) }) { l, r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Fatture", ym.nome().capitalizzato())
                item { Sfoglia("Mese", { mese = ym.minusMonths(1).toString() }, { mese = ym.plusMonths(1).toString() }) }
                if (l.isEmpty()) item { Vuoto("Nessuna fattura", "In questo mese non ne hai emesse.") }
                items(l, key = { it.id }) { f -> RigaFattura(f, nav, r.esegui) }
                piede()
            }
        }
    }
}

@Composable
fun RigaFattura(f: FatturaRiga, nav: NavHostController, ricarica: () -> Unit) {
    var aperta by remember { mutableStateOf(false) }
    var annulla by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf<EmailRichiesta?>(null) }
    var motivo by rememberSaveable { mutableStateOf("") }
    val esegui = azione()
    val annullata = f.stato == "ANNULLATA"
    Riga("N. ${f.numero} · ${f.intestatario ?: ""}", "${f.dataEmissione.breve()} · ${f.importoTotale.euro()}", fine = {
        when {
            annullata -> Pastiglia("Annullata", Tono.Neutro)
            f.emailInviataIl != null -> Pastiglia("Inviata", Tono.Positivo)
            else -> {}
        }
    }, onClick = { aperta = true })
    if (aperta) {
        Foglio({ aperta = false }) {
            Testo("Fattura n. ${f.numero}", Tipo.Title)
            Testo("${f.intestatario ?: ""} · ${f.importoTotale.euro()}", colore = colori.inkSecondary)
            f.descrizione?.let { Testo(it) }
            f.motivoAnnullamento?.let { Testo("Annullata: $it", Tipo.Label, colori.critical) }
            AzioneBrace("Apri il PDF", icona = Icons.Rounded.PictureAsPdf) { aperta = false; nav.navigate("pdfDottore/${f.id}?nome=" + cod(f.nomeFile ?: "fattura-${f.numero}.pdf")) }
            if (!annullata) {
                AzioneQuieta("Invia per email", icona = Icons.Rounded.Mail) { aperta = false; email = EmailRichiesta("FATTURA", f.pazienteId, f.pagamentoId, f.id) }
                AzioneQuieta("Annulla fattura", tono = Tono.Critico) { aperta = false; annulla = true }
            }
        }
    }
    if (annulla) {
        Foglio({ annulla = false }) {
            Testo("Annullare la fattura?", Tipo.Title)
            Testo("Non si può tornare indietro e il numero ${f.numero} resta occupato.", colore = colori.inkSecondary)
            Campo(motivo, { motivo = it }, "Motivo")
            AzioneBrace("Annulla la fattura", abilitata = motivo.isNotBlank()) {
                annulla = false
                esegui { Grafo.edo.annullaFattura(f.id, motivo.trim()); ricarica() }
            }
        }
    }
    email?.let { EmailFoglio(it) { email = null } }
}

@Composable
fun FatturaForm(pagamento: Long?, paziente: Long?, onFine: () -> Unit) {
    var pazienteId by rememberSaveable { mutableStateOf(paziente) }
    var valore by rememberSaveable { mutableStateOf("") }
    var descrizione by rememberSaveable { mutableStateOf("") }
    var data by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var opposizione by rememberSaveable { mutableStateOf(false) }
    var anteprima by remember { mutableStateOf<FatturaAnteprima?>(null) }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    fun corpo() = FatturaEmetti(pagamento, if (pagamento == null) pazienteId else null, if (pagamento == null) importo(valore) else null, data, descrizione.ifBlank { null }, opposizione)
    Schermo(onFine) {
        ColonnaForm {
            Testo("Fattura", Tipo.Display)
            if (pagamento == null) {
                ScegliPaziente(pazienteId) { pazienteId = it.id; anteprima = null }
                Campo(valore, { valore = it; anteprima = null }, "Importo in euro", tastiera = KeyboardType.Decimal)
            }
            CampoData("Data", data) { data = it; anteprima = null }
            Campo(descrizione, { descrizione = it; anteprima = null }, "Descrizione", righeMin = 2, aiuto = "Vuota: quella predefinita del pagamento")
            Interruttore("Opposizione alla Tessera Sanitaria", opposizione) { opposizione = it }
            val a = anteprima
            if (a == null) {
                AzioneBrace("Vedi l'anteprima", abilitata = pagamento != null || (pazienteId != null && importo(valore) != null), inCorso = inCorso) {
                    inCorso = true
                    esegui {
                        try { anteprima = Grafo.edo.anteprimaFattura(corpo()) } finally { inCorso = false }
                    }
                }
            } else {
                Filetto()
                Sopratitolo("Fattura n. ${a.numero} del ${a.data.breve()}")
                Testo(a.intestatario ?: "—", Tipo.Headline)
                listOfNotNull(a.indirizzo, a.codiceFiscale, a.descrizione).forEach { Testo(it, colore = colori.inkSecondary) }
                Riepilogo("Imponibile", a.imponibile.euro())
                Riepilogo("Contributo", a.contributo.euro())
                Riepilogo("Bollo", a.bollo.euro())
                Numero(a.importoTotale.euro(), "Totale")
                Testo("Una fattura emessa non si modifica: si può solo annullare e il numero resta occupato.", Tipo.Label, colori.inkSecondary)
                AzioneBrace("Emetti la fattura", inCorso = inCorso) {
                    inCorso = true
                    esegui {
                        try {
                            Grafo.edo.emettiFattura(corpo())
                            onFine()
                        } finally {
                            inCorso = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Riepilogo(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Testo(k, colore = colori.inkSecondary, modifier = Modifier.weight(1f))
        Testo(v, Tipo.Numero)
    }
}

@Composable
fun TesseraSchermo(onFine: () -> Unit) {
    var mese by rememberSaveable { mutableStateOf(YearMonth.now().minusMonths(1).toString()) }
    val ym = YearMonth.parse(mese)
    val esegui = azione()
    Schermo(onFine) {
        Async("tessera-$mese", { Grafo.edo.tessera(ym) }) { l, r ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("Tessera Sanitaria", ym.nome().capitalizzato(), "Da inserire sul Sistema TS")
                item { Sfoglia("Mese", { mese = ym.minusMonths(1).toString() }, { mese = ym.plusMonths(1).toString() }) }
                if (l.isEmpty()) item { Vuoto("Niente da inserire", "Questo mese è a posto.") }
                items(l, key = { it.fatturaId }) { t ->
                    Riga(
                        "N. ${t.numero} · ${t.importo.euro()}", "${t.data.breve()} · ${t.codiceFiscale ?: "—"}",
                        fine = { AzioneQuieta("Inserita") { esegui { Grafo.edo.tesseraInserita(t.fatturaId, true); r.esegui() } } },
                    )
                }
                piede()
            }
        }
    }
}
