package it.edodoc.app.feature.paziente

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import it.edodoc.app.Grafo
import it.edodoc.app.model.AppRiepilogoPercorso
import it.edodoc.app.model.AppVisita
import it.edodoc.app.model.CheckDettaglio
import it.edodoc.app.model.RiepilogoApp
import it.edodoc.app.model.StatoCheck
import it.edodoc.app.model.Utente
import it.edodoc.app.ui.kit.Async
import it.edodoc.app.ui.kit.AzioneBrace
import it.edodoc.app.ui.kit.Ellisse
import it.edodoc.app.ui.kit.Etichette
import it.edodoc.app.ui.kit.Filetto
import it.edodoc.app.ui.kit.LocalSpazioBarra
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Riga
import it.edodoc.app.ui.kit.ScheletroBlocco
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.capitalizzato
import it.edodoc.app.ui.kit.lungo
import it.edodoc.app.ui.kit.ora
import it.edodoc.app.ui.kit.parlato
import it.edodoc.app.ui.nav.scheda
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import java.time.LocalDate
import java.time.LocalDateTime

private data class DatiOggi(val riepilogo: RiepilogoApp, val check: StatoCheck, val percorso: AppRiepilogoPercorso, val daLeggere: CheckDettaglio?)

private data class Invito(val frase: String, val sotto: String?, val azione: String?, val rotta: String?, val fuoco: Offset)

/** Un'unica frase dice cosa fare adesso: l'ordine è l'ordine di importanza per il paziente. */
private fun invito(d: DatiOggi, nome: String?): Invito {
    val r = d.riepilogo
    val prossima = prossimaVisita(d.percorso.visite)
    return when {
        d.daLeggere != null -> Invito(
            "Il Dr. Bo ti ha risposto.",
            if (d.daLeggere.feedbackAudioId != null) "Ha lasciato un vocale per te." else "Il feedback al tuo check è pronto.",
            if (d.daLeggere.feedbackAudioId != null) "Ascolta" else "Leggi", "feedback/${d.daLeggere.id}", Offset(.5f, .62f),
        )
        !r.checkCompilato -> Invito("È il momento del tuo check.", "Una domanda alla volta, un paio di minuti.", "Inizia", "check", Offset(.5f, .62f))
        r.messaggiDaLeggere > 0 -> Invito(
            if (r.messaggiDaLeggere == 1L) "Hai un messaggio." else "Hai ${r.messaggiDaLeggere} messaggi.",
            "Dal Dr. Bo.", "Apri la chat", "#chat", Offset(.5f, .62f),
        )
        prossima != null && !prossima.inizio.toLocalDate().isAfter(LocalDate.now().plusDays(7)) -> Invito(
            "Ci vediamo ${prossima.inizio.toLocalDate().parlato()} alle ${prossima.inizio.ora()}.",
            Etichette.TipoVisita[prossima.tipo], if (prossima.meetUrl != null) "Vedi la visita" else null, "#percorso", Offset(.8f, .24f),
        )
        else -> Invito("Questa settimana è a posto${nome?.let { ", $it" } ?: ""}.", if (d.check.check?.haFeedback == true) "Il feedback del Dr. Bo è nei tuoi check." else "Check inviato. Il Dr. Bo lo leggerà lunedì.", null, null, Offset(.8f, .14f))
    }
}

fun prossimaVisita(visite: List<AppVisita>): AppVisita? {
    val ora = LocalDateTime.now()
    return visite.filter { it.stato == "PROGRAMMATA" && !(it.fine ?: it.inizio).isBefore(ora) }.minByOrNull { it.inizio }
}

@Composable
fun OggiPaziente(utente: Utente, nav: NavHostController) {
    val barra = LocalSpazioBarra.current
    // In orizzontale l'azione deve restare sopra la capsula senza scorrere
    val basso = LocalConfiguration.current.screenHeightDp < 480
    Async(
        "oggi",
        {
            val riepilogo = Grafo.edo.riepilogo()
            val check = Grafo.edo.checkCorrente()
            val percorso = Grafo.edo.percorso()
            val daLeggere = when {
                riepilogo.feedbackDaLeggere == 0L -> null
                check.check?.daLeggere == true -> check.check
                else -> Grafo.edo.storicoCheck().firstOrNull { it.daLeggere }
            }
            DatiOggi(riepilogo, check, percorso, daLeggere)
        },
        scheletro = {
            Schermo {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(MargineSchermo), verticalArrangement = Arrangement.Bottom) {
                    ScheletroBlocco(60.dp, larghezza = .8f)
                    Spacer(Modifier.height(12.dp))
                    ScheletroBlocco(60.dp, larghezza = .6f)
                    Spacer(Modifier.height(200.dp))
                }
            }
        },
    ) { d, _ ->
        val i = invito(d, utente.nome)
        Schermo(fuoco = i.fuoco, forza = 1.2f) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = barra + 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = MargineSchermo, end = MargineSchermo, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sopratitolo(LocalDate.now().lungo(), Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(colori.ink.copy(alpha = .08f))
                            .clickable(role = Role.Button) { nav.navigate("profilo") }.semantics { contentDescription = "Il tuo profilo" },
                        contentAlignment = Alignment.Center,
                    ) { Testo((utente.nome ?: "?").take(1).uppercase(), Tipo.Label) }
                }
                Spacer(Modifier.height(if (basso) 12.dp else 96.dp))
                Column(Modifier.padding(horizontal = MargineSchermo).heightIn(min = if (basso) 0.dp else 260.dp)) {
                    Testo(i.frase, Tipo.Display.copy(fontSize = Tipo.Display.fontSize * 1.14f, lineHeight = Tipo.Display.lineHeight * 1.14f), modifier = Modifier.semantics { heading() })
                    i.sotto?.let {
                        Spacer(Modifier.height(14.dp))
                        Testo(it, Tipo.Body, colori.inkSecondary)
                    }
                    if (i.azione != null && i.rotta != null) {
                        Spacer(Modifier.height(30.dp))
                        AzioneBrace(i.azione, icona = Icons.AutoMirrored.Rounded.ArrowForward) {
                            when {
                                i.rotta.startsWith("#") -> nav.scheda(i.rotta.drop(1))
                                else -> nav.navigate(i.rotta)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                Filetto(Modifier.padding(horizontal = MargineSchermo))
                d.percorso.percorso?.let { p ->
                    Riga(
                        "Visita ${p.visiteEffettuate.coerceAtMost(p.visiteTotali)} di ${p.visiteTotali}",
                        prossimaVisita(d.percorso.visite)?.let { "Prossima: ${it.inizio.toLocalDate().parlato()} alle ${it.ora()}".capitalizzato() } ?: Etichette.TipoPercorso[p.tipo],
                        inizio = { Tacche(p.visiteEffettuate, p.visiteTotali) },
                        onClick = { nav.scheda("percorso") },
                    )
                }
                if (d.check.compilato) {
                    Riga("I tuoi check", "Storico settimana per settimana", onClick = { nav.navigate("storico") })
                } else {
                    Riga("I tuoi check", "Storico settimana per settimana", onClick = { nav.navigate("storico") }, filetto = false)
                }
                if (d.check.compilato && d.check.check?.haFeedback == false) {
                    Riga("Modifica il check", "Puoi cambiarlo finché il Dr. Bo non risponde", onClick = { nav.navigate("check") }, filetto = false)
                }
            }
        }
    }
}

private fun AppVisita.ora() = inizio.ora()

@Composable
fun Tacche(fatte: Int, totali: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(totali.coerceAtMost(6)) { i ->
            Ellisse(Modifier.size(width = 9.dp, height = 14.dp), piena = i < fatte)
        }
    }
}
