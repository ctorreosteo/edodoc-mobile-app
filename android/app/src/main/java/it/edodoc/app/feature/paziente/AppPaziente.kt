package it.edodoc.app.feature.paziente

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import it.edodoc.app.Grafo
import it.edodoc.app.core.push.Destinazione
import it.edodoc.app.feature.comune.AnteprimaPdf
import it.edodoc.app.feature.comune.ChatSchermo
import it.edodoc.app.feature.comune.ProfiloSchermo
import it.edodoc.app.model.Utente
import it.edodoc.app.ui.nav.Scheda
import it.edodoc.app.ui.nav.Shell
import it.edodoc.app.ui.nav.long
import it.edodoc.app.ui.nav.rotta
import it.edodoc.app.ui.nav.scheda
import it.edodoc.app.ui.nav.testo
import kotlinx.coroutines.delay

@Composable
fun AppPaziente(utente: Utente, destinazione: MutableState<Destinazione?>) {
    val nav = rememberNavController()
    var nonLetti by remember { mutableLongStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { nonLetti = Grafo.edo.riepilogo().messaggiDaLeggere }
            delay(30_000)
        }
    }
    LaunchedEffect(destinazione.value) {
        val d = destinazione.value ?: return@LaunchedEffect
        destinazione.value = null
        when {
            d.tipo == "FEEDBACK" && d.checkId != null -> nav.navigate("feedback/${d.checkId}")
            d.tipo == "MESSAGGIO" -> nav.scheda("chat")
        }
    }
    val schede = listOf(
        Scheda("oggi", "Oggi", Icons.Rounded.WbTwilight),
        Scheda("corsi", "Corsi", Icons.Rounded.AutoStories),
        Scheda("chat", "Chat", Icons.Rounded.ChatBubble, nonLetti),
        Scheda("percorso", "Percorso", Icons.Rounded.Route),
    )
    Shell(nav, "oggi", schede) {
        rotta("oggi") { OggiPaziente(utente, nav) }
        rotta("corsi") { CorsiSchermo(nav) }
        rotta("chat") { ChatSchermo(null, "Dr. Bo", null) { nonLetti = 0 } }
        rotta("percorso") { PercorsoSchermo(nav) }
        rotta("check") { CheckSchermo(nav) }
        rotta("storico") { StoricoCheck(nav) }
        rotta("feedback/{id}", numeri = listOf("id")) { FeedbackSchermo(it.long("id")) { nav.popBackStack() } }
        rotta("corso/{id}", numeri = listOf("id")) { CorsoDettaglio(it.long("id"), nav) }
        rotta("lezione/{id}", numeri = listOf("id")) { LezioneSchermo(it.long("id")) { nav.popBackStack() } }
        rotta("documenti") { DocumentiSchermo(nav) }
        rotta("pdf/{id}?nome={nome}", numeri = listOf("id"), testi = listOf("nome")) { e ->
            val id = e.long("id")
            AnteprimaPdf("p$id", e.testo("nome") ?: "fattura.pdf", { Grafo.edo.scaricaFattura(id, it) }) { nav.popBackStack() }
        }
        rotta("profilo") { ProfiloSchermo(utente) { nav.popBackStack() } }
    }
}
