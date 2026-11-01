package it.edodoc.app.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.edodoc.app.Grafo
import it.edodoc.app.core.net.ApiException
import it.edodoc.app.ui.kit.AzioneBrace
import it.edodoc.app.ui.kit.AzioneQuieta
import it.edodoc.app.ui.kit.Campo
import it.edodoc.app.ui.kit.Logo
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.azione
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori

/** Il token di un invito, da un link intero o da solo. null: quello incollato non è un invito. */
fun tokenDaInvito(testo: String): String? {
    val pulito = testo.trim()
    if (pulito.isEmpty()) return null
    Regex("""[?&]token=([^&\s]+)""").find(pulito)?.let { return it.groupValues[1] }
    return pulito.takeIf { it.none(Char::isWhitespace) && !it.contains("://") }
}

@Composable
fun Accesso(invito: MutableState<String?>) {
    var primo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(invito.value) { if (invito.value != null) primo = true }
    Schermo(fuoco = if (primo) Offset(.2f, .8f) else Offset(.75f, .22f), forza = 1.25f) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = MargineSchermo).padding(top = 56.dp, bottom = 28.dp),
        ) {
            Logo(Modifier.width(76.dp).height(30.dp))
            Spacer(Modifier.weight(1f).height(72.dp))
            AnimatedContent(primo, label = "accesso") { p ->
                if (p) PrimoAccesso(invito) { primo = false } else Entra { primo = true }
            }
        }
    }
}

@Composable
private fun Entra(onPrimo: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    var errore by rememberSaveable { mutableStateOf<String?>(null) }
    val esegui = azione()
    fun entra() {
        if (email.isBlank() || password.isBlank()) return
        inCorso = true
        errore = null
        esegui {
            try {
                Grafo.api.apri(Grafo.edo.login(email, password))
            } catch (e: ApiException) {
                errore = e.message
            }
            inCorso = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Testo("Ciao.", Tipo.Display.copy(fontSize = Tipo.Display.fontSize * 1.2f))
        Campo(email, { email = it }, "Email", tastiera = KeyboardType.Email, capitalizza = KeyboardCapitalization.None)
        Campo(password, { password = it }, "Password", tastiera = KeyboardType.Password, azioneIme = ImeAction.Done, password = true, onFine = ::entra)
        errore?.let { Testo(it, Tipo.Label, colori.critical) }
        AzioneBrace("Entra", abilitata = email.isNotBlank() && password.isNotBlank(), inCorso = inCorso, onClick = ::entra)
        AzioneQuieta("È il mio primo accesso", onClick = onPrimo)
    }
}

@Composable
private fun PrimoAccesso(invito: MutableState<String?>, onIndietro: () -> Unit) {
    val appunti: ClipboardManager = LocalClipboardManager.current
    var link by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var ripeti by rememberSaveable { mutableStateOf("") }
    var inCorso by rememberSaveable { mutableStateOf(false) }
    var errore by rememberSaveable { mutableStateOf<String?>(null) }
    val esegui = azione()
    val token = invito.value ?: tokenDaInvito(link)
    val problema = when {
        token == null -> "Incolla il link che hai ricevuto dal Dr. Bo"
        password.length < 10 -> "La password deve avere almeno 10 caratteri"
        password != ripeti -> "Le due password non coincidono"
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Testo("Iniziamo.", Tipo.Display.copy(fontSize = Tipo.Display.fontSize * 1.2f))
        Testo("Scegli la password con cui entrerai nell'app.", colore = colori.inkSecondary)
        if (invito.value == null) {
            Campo(link, { link = it }, "Link di invito", capitalizza = KeyboardCapitalization.None)
            AzioneQuieta("Incolla", icona = Icons.Rounded.ContentPaste) { appunti.getText()?.let { link = it.text } }
        }
        Campo(password, { password = it }, "Nuova password", tastiera = KeyboardType.Password, password = true, aiuto = "Almeno 10 caratteri")
        Campo(ripeti, { ripeti = it }, "Ripeti la password", tastiera = KeyboardType.Password, azioneIme = ImeAction.Done, password = true)
        errore?.let { Testo(it, Tipo.Label, colori.critical) }
        AzioneBrace("Attiva l'accesso", abilitata = problema == null, inCorso = inCorso) {
            val t = token ?: return@AzioneBrace
            inCorso = true
            errore = null
            esegui {
                try {
                    val sessione = Grafo.edo.impostaPassword(t, password)
                    invito.value = null
                    Grafo.api.apri(sessione)
                } catch (e: ApiException) {
                    errore = e.message
                }
                inCorso = false
            }
        }
        AzioneQuieta("Ho già una password") {
            invito.value = null
            onIndietro()
        }
    }
}
