package it.edodoc.app.ui.kit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.edodoc.app.core.net.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class Caricamento<T>(val dati: T? = null, val errore: String? = null, val offline: Boolean = false, val inCorso: Boolean = true)

/** Vive quanto la voce del back stack: i dati restano mentre si aggiorna, ruotando e tornando indietro. */
class Caricatore : ViewModel() {
    val stato = MutableStateFlow(Caricamento<Any>())
    private var lavoro: Job? = null

    fun carica(blocco: suspend () -> Any?) {
        lavoro?.cancel()
        stato.value = stato.value.copy(inCorso = true, errore = null, offline = false)
        lavoro = viewModelScope.launch {
            try {
                stato.value = Caricamento(dati = blocco(), inCorso = false)
            } catch (e: ApiException) {
                stato.value = stato.value.copy(errore = e.message, offline = e.offline, inCorso = false)
            }
        }
    }
}

class Ricarica(val esegui: () -> Unit)

/**
 * Tutti i dati delle schermate passano da qui: scheletro al primo caricamento, errore con riprova,
 * aggiornamento silenzioso al ritorno sulla schermata. Se ci sono dati, un errore non li toglie.
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> Async(
    chiave: String,
    carica: suspend () -> T,
    scheletro: @Composable () -> Unit = { ScheletroLista() },
    contenuto: @Composable (T, Ricarica) -> Unit,
) {
    val vm = viewModel<Caricatore>(key = chiave)
    val stato by vm.stato.collectAsState()
    val blocco by rememberUpdatedState(carica)
    val ricarica = Ricarica { vm.carica { blocco() } }
    LaunchedEffect(chiave) { if (vm.stato.value.dati == null) ricarica.esegui() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (vm.stato.value.dati != null) ricarica.esegui() }
    val dati = stato.dati as T?
    val fase = when {
        dati != null -> 2
        stato.errore != null -> 1
        else -> 0
    }
    AnimatedContent(fase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "async") { f ->
        when (f) {
            2 -> if (dati != null) contenuto(dati, ricarica)
            1 -> Errore(stato.errore ?: "", stato.offline) { ricarica.esegui() }
            else -> scheletro()
        }
    }
}
