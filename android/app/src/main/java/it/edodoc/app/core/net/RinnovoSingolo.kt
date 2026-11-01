package it.edodoc.app.core.net

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Un solo /rinnova alla volta: un refresh token vale una volta sola. Chi riceve un 401 mentre un rinnovo
 * è in corso aspetta il suo turno e trova già il token nuovo. Il rinnovo non si annulla con la schermata
 * che lo ha chiesto: la risposta persa farebbe perdere anche la sessione.
 */
class RinnovoSingolo(
    private val tokenCorrente: () -> String?,
    private val rinnova: suspend () -> String?,
) {
    private val mutex = Mutex()

    suspend fun rinnova(tokenUsato: String?): String? = mutex.withLock {
        val corrente = tokenCorrente()
        if (corrente != null && corrente != tokenUsato) corrente
        else withContext(NonCancellable) { rinnova() }
    }
}
