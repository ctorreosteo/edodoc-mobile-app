package it.edodoc.app.feature.comune

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.edodoc.app.BuildConfig
import it.edodoc.app.Grafo
import it.edodoc.app.ui.kit.AzioneBrace
import it.edodoc.app.ui.kit.Logo
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.launch

/**
 * Sostituisce tutta l'app finché non si installa la versione nuova. Chi annulla il flusso di Play
 * torna qui e non altrove: non c'è modo di chiuderla, il tasto indietro esce dall'app.
 */
@Composable
fun AggiornamentoObbligatorio() {
    val context = LocalContext.current
    val ambito = rememberCoroutineScope()
    val flusso = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    LaunchedEffect(Unit) { Grafo.aggiornamento.avvia(flusso) }
    Schermo(fuoco = Offset(.75f, .22f), forza = 1.25f) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = MargineSchermo).padding(top = 56.dp, bottom = 28.dp),
        ) {
            Logo(Modifier.width(76.dp).height(30.dp))
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Testo("Aggiorna l'app.", Tipo.Display)
                Testo("È disponibile una nuova versione. Per continuare a usare EdoDoc devi installarla.", colore = colori.inkSecondary)
                AzioneBrace("Aggiorna") {
                    ambito.launch {
                        if (!Grafo.aggiornamento.avvia(flusso)) {
                            val scheda = Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, scheda)) }
                                .onFailure { if (it !is ActivityNotFoundException) throw it }
                        }
                    }
                }
            }
        }
    }
}
