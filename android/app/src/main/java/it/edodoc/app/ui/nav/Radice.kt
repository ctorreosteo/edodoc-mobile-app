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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import it.edodoc.app.Grafo
import it.edodoc.app.core.push.Destinazione
import it.edodoc.app.feature.auth.Accesso
import it.edodoc.app.feature.dottore.AppDottore
import it.edodoc.app.feature.paziente.AppPaziente
import it.edodoc.app.ui.kit.AvvisiHost
import it.edodoc.app.ui.theme.colori
import java.io.File

@Composable
fun Radice(invito: MutableState<String?>, destinazione: MutableState<Destinazione?>) {
    val sessione by Grafo.api.sessione.collectAsState()
    val context = LocalContext.current
    val permesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val utente = sessione?.utente
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
                utente == null || invito.value != null -> Accesso(invito)
                utente.isDottore -> AppDottore(utente, destinazione)
                else -> AppPaziente(utente, destinazione)
            }
        }
    }
}
