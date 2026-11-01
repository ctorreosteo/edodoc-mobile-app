package it.edodoc.app.feature.paziente

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import it.edodoc.app.Grafo
import it.edodoc.app.model.AppPagamento
import it.edodoc.app.model.AppVisita
import it.edodoc.app.ui.kit.Async
import it.edodoc.app.ui.kit.AzioneBrace
import it.edodoc.app.ui.kit.BottoneIcona
import it.edodoc.app.ui.kit.Etichette
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Numero
import it.edodoc.app.ui.kit.Pastiglia
import it.edodoc.app.ui.kit.Riga
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.Tono
import it.edodoc.app.ui.kit.Vuoto
import it.edodoc.app.ui.kit.breve
import it.edodoc.app.ui.kit.capitalizzato
import it.edodoc.app.ui.kit.euro
import it.edodoc.app.ui.kit.lungo
import it.edodoc.app.ui.kit.ora
import it.edodoc.app.ui.kit.parlato
import it.edodoc.app.ui.kit.piede
import it.edodoc.app.ui.kit.sezione
import it.edodoc.app.ui.kit.testata
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private sealed interface Nodo {
    data class Visita(val v: AppVisita) : Nodo
    data class DaFissare(val numero: Int) : Nodo
}

private fun conto(giorno: LocalDate): String {
    val giorni = ChronoUnit.DAYS.between(LocalDate.now(), giorno)
    return when {
        giorni <= 0 -> "Oggi"
        giorni == 1L -> "Domani"
        else -> "Tra $giorni giorni"
    }
}

/** Una linea verticale con un nodo per visita: i futuri vuoti, il prossimo acceso con il conto alla rovescia. */
@Composable
fun PercorsoSchermo(nav: NavHostController) {
    Schermo(azioni = { BottoneIcona(Icons.AutoMirrored.Rounded.ReceiptLong, "Fatture e pagamenti") { nav.navigate("documenti") } }) {
        Async("percorso", { Grafo.edo.percorso() }) { d, _ ->
            val p = d.percorso
            val prossima = prossimaVisita(d.visite)
            val nodi = buildList {
                d.visite.filter { it.stato != "ANNULLATA" }.forEach { add(Nodo.Visita(it)) }
                if (p != null && p.stato == "ATTIVO") {
                    val fissate = d.visite.count { it.stato != "ANNULLATA" && it.numeroVisita != null }
                    (fissate + 1..p.visiteTotali).forEach { add(Nodo.DaFissare(it)) }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.statusBarsPadding().padding(start = MargineSchermo, end = MargineSchermo, top = 72.dp, bottom = 26.dp)) {
                        Sopratitolo(p?.let { Etichette.TipoPercorso[it.tipo] } ?: "Il tuo percorso")
                        Spacer(Modifier.height(6.dp))
                        if (p != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${p.visiteEffettuate}", style = Tipo.DisplayXL.copy(fontSize = 96.sp, lineHeight = 100.sp), color = colori.ink)
                                Spacer(Modifier.width(10.dp))
                                Testo((if (p.visiteEffettuate == 1) "visita su " else "visite su ") + p.visiteTotali, Tipo.Headline, colori.inkSecondary, Modifier.padding(bottom = 16.dp))
                            }
                            if (p.stato != "ATTIVO") {
                                Pastiglia(if (p.stato == "ANNULLATO") "Annullato" else "Concluso", Tono.Neutro)
                            } else {
                                p.dataFine?.let { Testo("Fino al ${it.breve()}", Tipo.Label, colori.inkSecondary) }
                            }
                        } else {
                            Testo("Inizia con la prima visita.", Tipo.Display)
                        }
                    }
                }
                if (nodi.isEmpty()) item { Vuoto("Nessuna visita ancora", "Quando il Dr. Bo ne fissa una, la trovi qui.") }
                items(nodi.size) { i ->
                    Tappa(nodi[i], (nodi[i] as? Nodo.Visita)?.v?.id == prossima?.id, i == 0, i == nodi.lastIndex)
                }
                piede()
            }
        }
    }
}

@Composable
private fun Tappa(nodo: Nodo, prossima: Boolean, prima: Boolean, ultima: Boolean) {
    val c = colori
    val context = LocalContext.current
    val visita = (nodo as? Nodo.Visita)?.v
    val fatta = visita?.stato == "EFFETTUATA"
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = MargineSchermo).semantics(mergeDescendants = true) {}) {
        Box(Modifier.width(32.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = 11.dp.toPx()
                val y = 26.dp.toPx()
                val tratteggio = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
                if (!prima) drawLine(if (fatta || prossima) c.ember else c.ink.copy(alpha = .22f), Offset(x, 0f), Offset(x, y), 1.5.dp.toPx(), pathEffect = if (fatta || prossima) null else tratteggio)
                if (!ultima) drawLine(if (fatta) c.ember else c.ink.copy(alpha = .22f), Offset(x, y), Offset(x, size.height), 1.5.dp.toPx(), pathEffect = if (fatta) null else tratteggio)
                when {
                    prossima -> {
                        drawCircle(c.ember.copy(alpha = .22f), 11.dp.toPx(), Offset(x, y))
                        drawCircle(c.ember, 6.dp.toPx(), Offset(x, y))
                    }
                    fatta -> drawCircle(c.ember, 5.dp.toPx(), Offset(x, y))
                    else -> {
                        drawCircle(c.canvas, 5.dp.toPx(), Offset(x, y))
                        drawCircle(c.ink.copy(alpha = .35f), 5.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 22.dp)) {
            when (nodo) {
                is Nodo.DaFissare -> {
                    Testo("Visita ${nodo.numero}", Tipo.Headline, c.inkTertiary)
                    Testo("Da fissare", Tipo.Label, c.inkTertiary)
                }
                is Nodo.Visita -> {
                    val v = nodo.v
                    if (prossima) {
                        Sopratitolo(conto(v.inizio.toLocalDate()), colore = c.ember)
                        Spacer(Modifier.height(4.dp))
                    }
                    Testo(listOfNotNull(Etichette.TipoVisita[v.tipo], v.numeroVisita?.let { "$it di ${v.visiteTotali ?: "?"}" }).joinToString(" · "), Tipo.Headline, if (fatta || prossima) c.ink else c.inkSecondary)
                    Testo(
                        "${v.inizio.toLocalDate().lungo().capitalizzato()}, ${v.inizio.ora()} · ${Etichette.Modalita[v.modalita] ?: v.modalita}",
                        Tipo.Label.copy(fontWeight = Tipo.Body.fontWeight), c.inkSecondary,
                    )
                    if (v.stato == "NON_PRESENTATO") {
                        Spacer(Modifier.height(6.dp))
                        Pastiglia("Non presentato", Tono.Attenzione)
                    }
                    if (prossima && v.meetUrl != null) {
                        Spacer(Modifier.height(14.dp))
                        AzioneBrace("Entra nella visita", icona = Icons.Rounded.Videocam) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, v.meetUrl.toUri()))
                        }
                    }
                }
            }
        }
    }
}

private fun statoPagamento(p: AppPagamento): Pair<String, Tono> = when {
    p.stato == "PAGATO" -> "Pagato" to Tono.Positivo
    p.stato == "ANNULLATO" -> "Annullato" to Tono.Neutro
    p.scaduto -> "Scaduto" to Tono.Critico
    else -> "Da pagare" to Tono.Attenzione
}

@Composable
fun DocumentiSchermo(nav: NavHostController) {
    fun apri(id: Long, nome: String?) = nav.navigate("pdf/$id?nome=" + android.net.Uri.encode(nome ?: "fattura-$id.pdf"))
    Schermo({ nav.popBackStack() }) {
        Async("documenti", { Grafo.edo.pagamenti() to Grafo.edo.fatture() }) { (pagamenti, fatture), _ ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("I tuoi documenti", "Fatture e pagamenti")
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = MargineSchermo).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Numero(pagamenti.riepilogo.pagato.euro(), "Pagato")
                        if (pagamenti.riepilogo.daPagare.signum() > 0) Numero(pagamenti.riepilogo.daPagare.euro(), "Da pagare")
                    }
                }
                sezione("Fatture")
                if (fatture.isEmpty()) item { Vuoto("Nessuna fattura", "Arrivano dopo ogni pagamento ricevuto.") }
                items(fatture, key = { "f${it.id}" }) { f ->
                    Riga(
                        "Fattura n. ${f.numero}", "${f.dataEmissione.breve()} · ${f.importoTotale.euro()}",
                        fine = { Icon(Icons.Rounded.Download, "Scarica la fattura", tint = colori.ember) },
                        onClick = { apri(f.id, f.nomeFile) },
                    )
                }
                sezione("Pagamenti")
                items(pagamenti.righe, key = { "p${it.id}" }) { p ->
                    val (stato, tono) = statoPagamento(p)
                    Riga(p.descrizione, "${p.importo.euro()} · ${(p.dataPagamento ?: p.scadenza).breve()}", fine = { Pastiglia(stato, tono) })
                }
                piede()
            }
        }
    }
}
