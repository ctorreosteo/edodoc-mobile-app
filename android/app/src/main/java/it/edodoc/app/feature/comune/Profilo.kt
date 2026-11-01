package it.edodoc.app.feature.comune

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.edodoc.app.Grafo
import it.edodoc.app.BuildConfig
import it.edodoc.app.model.Utente
import it.edodoc.app.ui.kit.AzioneBrace
import it.edodoc.app.ui.kit.Campo
import it.edodoc.app.ui.kit.Conferma
import it.edodoc.app.ui.kit.Foglio
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Riga
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.azione
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori

@Composable
fun ProfiloSchermo(utente: Utente, onIndietro: () -> Unit) {
    var password by rememberSaveable { mutableStateOf(false) }
    var esci by rememberSaveable { mutableStateOf(false) }
    var licenze by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    Schermo(onIndietro) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 76.dp)) {
            Column(Modifier.padding(horizontal = MargineSchermo)) {
                Sopratitolo(if (utente.isDottore) "Il tuo account" else "Il tuo profilo")
                Spacer(Modifier.height(10.dp))
                Testo(listOfNotNull(utente.nome, utente.cognome).joinToString(" ").ifBlank { utente.username }, Tipo.Display)
                Spacer(Modifier.height(6.dp))
                Testo(utente.username, colore = colori.inkSecondary)
            }
            Spacer(Modifier.height(28.dp))
            Riga("Cambia password", onClick = { password = true })
            Riga("Licenze", "Font Montserrat, SIL Open Font License", onClick = { licenze = true })
            Riga("Esci", onClick = { esci = true }, filetto = false)
            Spacer(Modifier.height(30.dp))
            Testo("EdoDoc ${BuildConfig.VERSION_NAME}", Tipo.Label, colori.inkSecondary, Modifier.padding(horizontal = MargineSchermo))
        }
    }
    if (password) CambioPassword { password = false }
    if (licenze) Licenze { licenze = false }
    if (esci) {
        Conferma("Vuoi uscire?", "Dovrai inserire di nuovo la password per entrare.", "Esci", pericolosa = true,
            onConferma = { esci = false; esegui { Grafo.api.logout(Grafo.push.tokenCorrente()) } }, onAnnulla = { esci = false })
    }
}

@Composable
private fun Licenze(onChiudi: () -> Unit) {
    val context = LocalContext.current
    val testo = remember { context.assets.open("licenze/montserrat-OFL.txt").bufferedReader().use { it.readText() } }
    Foglio(onChiudi) { Testo(testo, Tipo.Label.copy(fontWeight = Tipo.Body.fontWeight), colori.inkSecondary) }
}

@Composable
private fun CambioPassword(onChiudi: () -> Unit) {
    var attuale by rememberSaveable { mutableStateOf("") }
    var nuova by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    val esegui = azione()
    Foglio(onChiudi) {
        Testo("Nuova password", Tipo.Title)
        Testo("Il cambio chiude l'accesso su tutti i tuoi telefoni: dopo dovrai rientrare.", colore = colori.inkSecondary)
        Campo(attuale, { attuale = it }, "Password attuale", tastiera = KeyboardType.Password, password = true)
        Campo(nuova, { nuova = it }, "Nuova password", tastiera = KeyboardType.Password, password = true, aiuto = "Almeno 10 caratteri")
        AzioneBrace("Cambia password", abilitata = attuale.isNotBlank() && nuova.length >= 10, inCorso = inCorso) {
            inCorso = true
            esegui {
                try {
                    Grafo.edo.cambiaPassword(attuale, nuova)
                    // Il server ha già chiuso tutte le sessioni, compresa questa
                    Grafo.api.chiudi()
                } finally {
                    inCorso = false
                }
            }
        }
    }
}
