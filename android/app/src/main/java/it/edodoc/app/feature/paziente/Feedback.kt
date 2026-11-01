package it.edodoc.app.feature.paziente

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import it.edodoc.app.Grafo
import it.edodoc.app.feature.comune.LettoreVocale
import it.edodoc.app.model.CheckDettaglio
import it.edodoc.app.ui.kit.Async
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Pastiglia
import it.edodoc.app.ui.kit.Riga
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.Tono
import it.edodoc.app.ui.kit.Vuoto
import it.edodoc.app.ui.kit.conOra
import it.edodoc.app.ui.kit.corto
import it.edodoc.app.ui.kit.kg
import it.edodoc.app.ui.kit.piede
import it.edodoc.app.ui.kit.testata
import it.edodoc.app.ui.kit.vetro
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import java.io.File

private data class DatiFeedback(val check: CheckDettaglio, val audio: File?)

@Composable
fun FeedbackSchermo(id: Long, onIndietro: () -> Unit) {
    val context = LocalContext.current
    Schermo(onIndietro, fuoco = Offset(.5f, .34f), forza = 1.2f) {
        Async("feedback-$id", {
            val check = Grafo.edo.storicoCheck().first { it.id == id }
            val audio = check.feedbackAudioId?.let { a ->
                val file = File(context.cacheDir, "vocali/$a.m4a")
                if (file.exists()) file else Grafo.edo.scaricaVocale(id, false, file)
            }
            DatiFeedback(check, audio)
        }) { (check, audio), _ ->
            LaunchedEffect(check.id) { if (check.daLeggere) runCatching { Grafo.edo.feedbackLetto(check.id) } }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                    .padding(start = MargineSchermo, end = MargineSchermo, top = 76.dp, bottom = 36.dp),
            ) {
                Sopratitolo("Settimana del ${check.settimana.corto()}")
                Spacer(Modifier.height(10.dp))
                Testo(if (audio != null) "Ti ho lasciato un vocale." else "Ecco cosa ne penso.", Tipo.Display)
                Spacer(Modifier.height(30.dp))
                if (audio != null) {
                    Box(Modifier.fillMaxWidth().vetro().padding(18.dp)) {
                        LettoreVocale(audio, check.feedbackAudioSecondi ?: 0)
                    }
                    Spacer(Modifier.height(26.dp))
                }
                check.feedback?.takeIf { it.isNotBlank() }?.let {
                    Testo(it, Tipo.Body.copy(fontSize = Tipo.Headline.fontSize, lineHeight = Tipo.Title.lineHeight))
                }
                Spacer(Modifier.height(22.dp))
                Testo("Dr. Edoardo Bo" + (check.feedbackIl?.let { " · ${it.conOra()}" } ?: ""), Tipo.Label, colori.inkSecondary)
            }
        }
    }
}

@Composable
fun StoricoCheck(nav: NavHostController) {
    Schermo({ nav.popBackStack() }) {
        Async("storico-check", { Grafo.edo.storicoCheck() }) { storico, _ ->
            LazyColumn(Modifier.fillMaxSize()) {
                testata("I tuoi check", "Settimana per settimana")
                val pesi = storico.mapNotNull { it.pesoKg?.toFloat() }.reversed()
                if (pesi.size >= 2) {
                    item {
                        Column(Modifier.padding(horizontal = MargineSchermo).padding(bottom = 18.dp)) {
                            Sopratitolo("Peso, ultimi ${pesi.size} check")
                            Spacer(Modifier.height(10.dp))
                            Andamento(pesi, Modifier.fillMaxWidth().height(84.dp))
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Testo(pesi.first().toBigDecimal().kg(), Tipo.Numero, colori.inkSecondary)
                                Testo(pesi.last().toBigDecimal().kg(), Tipo.Numero)
                            }
                        }
                    }
                }
                if (storico.isEmpty()) item { Vuoto("Ancora nessun check", "Il primo arriva a fine settimana.") }
                items(storico, key = { it.id }) { c ->
                    Riga(
                        "Settimana del ${c.settimana.corto()}",
                        listOfNotNull(c.pesoKg?.kg(), c.energia?.let { "energia $it/5" }, c.impegno?.let { "impegno $it/5" }).joinToString(" · "),
                        fine = {
                            when {
                                c.daLeggere -> Pastiglia("Nuovo", Tono.Brace)
                                c.haFeedback -> Pastiglia(if (c.feedbackAudioId != null) "Vocale" else "Feedback", Tono.Neutro)
                                else -> {}
                            }
                        },
                        onClick = if (c.haFeedback) ({ nav.navigate("feedback/${c.id}") }) else null,
                    )
                }
                piede()
            }
        }
    }
}

@Composable
private fun Andamento(valori: List<Float>, modifier: Modifier = Modifier) {
    val c = colori
    Canvas(modifier.semantics { contentDescription = "Andamento del peso da ${valori.first()} a ${valori.last()} chili" }) {
        val min = valori.min()
        val max = valori.max()
        val ampiezza = (max - min).coerceAtLeast(.1f)
        val m = 6.dp.toPx()
        fun punto(i: Int) = Offset(
            m + (size.width - 2 * m) * i / (valori.size - 1),
            m + (size.height - 2 * m) * (1 - (valori[i] - min) / ampiezza),
        )
        val tratto = Path().apply {
            moveTo(punto(0).x, punto(0).y)
            for (i in 1 until valori.size) lineTo(punto(i).x, punto(i).y)
        }
        drawPath(tratto, c.ember, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.canvas, 7.dp.toPx(), punto(valori.lastIndex))
        drawCircle(c.ember, 5.dp.toPx(), punto(valori.lastIndex))
    }
}
