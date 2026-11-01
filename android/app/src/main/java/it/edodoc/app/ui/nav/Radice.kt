package it.edodoc.app.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import it.edodoc.app.Grafo
import it.edodoc.app.core.push.Destinazione
import it.edodoc.app.feature.auth.Accesso
import it.edodoc.app.feature.comune.AggiornamentoObbligatorio
import it.edodoc.app.feature.dottore.AppDottore
import it.edodoc.app.feature.paziente.AppPaziente
import it.edodoc.app.ui.kit.AvvisiHost
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun Radice(invito: MutableState<String?>, destinazione: MutableState<Destinazione?>) {
    val sessione by Grafo.api.sessione.collectAsState()
    val context = LocalContext.current
    val permesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val utente = sessione?.utente
    val ambito = rememberCoroutineScope()
    var daAggiornare by rememberSaveable { mutableStateOf(false) }
    // Anche al ritorno in primo piano: l'app può restare aperta per giorni. Una volta trovato, il blocco non si toglie
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ambito.launch { if (Grafo.aggiornamento.disponibile()) daAggiornare = true }
    }
    LaunchedEffect(utente?.id) {
        if (utente != null) {
            Grafo.push.registra()
            if (Build.VERSION.SDK_INT >= 33) permesso.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Niente dati sanitari lasciati in cache da chi esce
            listOf("fatture", "vocali").forEach { File(context.cacheDir, it).deleteRecursively() }
        }
    }
    Box(Modifier.fillMaxSize().background(colori.canvas)) {
        AvvisiHost {
            when {
                daAggiornare -> AggiornamentoObbligatorio()
                utente == null || invito.value != null -> Accesso(invito)
                utente.isDottore -> AppDottore(utente, destinazione)
                else -> AppPaziente(utente, destinazione)
            }
        }
    }
}
