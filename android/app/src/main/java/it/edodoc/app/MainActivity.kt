package it.edodoc.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import it.edodoc.app.core.push.Destinazione
import it.edodoc.app.ui.nav.Radice
import it.edodoc.app.ui.theme.EdoTema

class MainActivity : ComponentActivity() {
    private val invito = mutableStateOf<String?>(null)
    private val destinazione = mutableStateOf<Destinazione?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        leggi(intent)
        setContent {
            EdoTema {
                Radice(invito, destinazione)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leggi(intent)
    }

    private fun leggi(intent: Intent?) {
        intent?.data?.takeIf { it.scheme == "edodoc" && it.host == "imposta-password" }?.let { invito.value = it.getQueryParameter("token") }
        Destinazione.da(intent?.extras)?.let { destinazione.value = it }
    }
}
