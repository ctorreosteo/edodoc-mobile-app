package it.edodoc.app.core.net

import it.edodoc.app.core.session.SessionStore
import it.edodoc.app.model.Logout
import it.edodoc.app.model.Rinnova
import it.edodoc.app.model.Risposta
import it.edodoc.app.model.Sessione
import it.edodoc.app.model.Utente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** status 0: nessuna risposta dal server. */
class ApiException(message: String, val status: Int) : Exception(message) {
    val offline get() = status == 0
}

val JsonEdo = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

class ApiClient(
    private val baseUrl: String,
    private val store: SessionStore,
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val _sessione = MutableStateFlow(store.carica())
    val sessione: StateFlow<Sessione?> = _sessione

    val utente: Utente? get() = _sessione.value?.utente

    private val rinnovo = RinnovoSingolo({ _sessione.value?.token }) { rinnovaDalServer() }

    fun apri(sessione: Sessione) {
        store.salva(sessione)
        _sessione.value = sessione
    }

    fun chiudi() {
        store.cancella()
        _sessione.value = null
    }

    suspend fun <T> get(percorso: String, serial: KSerializer<T>): T = chiama(richiesta(percorso), serial)

    suspend fun <T> post(percorso: String, corpo: RequestBody, serial: KSerializer<T>): T =
        chiama(richiesta(percorso).post(corpo), serial)

    suspend fun <T> put(percorso: String, corpo: RequestBody, serial: KSerializer<T>): T =
        chiama(richiesta(percorso).put(corpo), serial)

    suspend fun <T> delete(percorso: String, serial: KSerializer<T>): T = chiama(richiesta(percorso).delete(), serial)

    /** La stessa chiamata, con il messaggio del server: serve quando dice qualcosa che il dottore deve leggere. */
    suspend fun <T> postConMessaggio(percorso: String, corpo: RequestBody, serial: KSerializer<T>): Pair<T?, String?> {
        val risposta = esegui(richiesta(percorso).post(corpo))
        val testo = withContext(Dispatchers.IO) { risposta.use { it.body?.string().orEmpty() } }
        val busta = JsonEdo.decodeFromString(Risposta.serializer(serial), testo)
        return busta.data to busta.message
    }

    suspend fun <T> putConMessaggio(percorso: String, corpo: RequestBody, serial: KSerializer<T>): Pair<T?, String?> {
        val risposta = esegui(richiesta(percorso).put(corpo))
        val testo = withContext(Dispatchers.IO) { risposta.use { it.body?.string().orEmpty() } }
        val busta = JsonEdo.decodeFromString(Risposta.serializer(serial), testo)
        return busta.data to busta.message
    }

    /** Senza Bearer e senza rinnovo: login, primo accesso. */
    suspend fun <T> postPubblico(percorso: String, corpo: RequestBody, serial: KSerializer<T>): T {
        val risposta = invia(richiesta(percorso).post(corpo).build())
        return decodifica(risposta, serial)
    }

    /** Scarica con Bearer in un file: PDF delle fatture e vocali. */
    suspend fun scarica(percorso: String, destinazione: File): File {
        val risposta = esegui(richiesta(percorso))
        withContext(Dispatchers.IO) {
            destinazione.parentFile?.mkdirs()
            val temporaneo = File(destinazione.parentFile, destinazione.name + ".part")
            risposta.use { r -> temporaneo.outputStream().use { uscita -> r.body!!.byteStream().copyTo(uscita) } }
            if (!temporaneo.renameTo(destinazione)) {
                temporaneo.delete()
                throw ApiException("Download non riuscito. Riprova.", 0)
            }
        }
        return destinazione
    }

    suspend fun logout(pushToken: String?) {
        val refresh = _sessione.value?.refreshToken
        chiudi()
        if (refresh != null) {
            // Se la rete non c'è il telefono è già uscito: il refresh token scade da solo
            runCatching {
                postPubblico("/auth/logout", json(Logout(refresh, pushToken), Logout.serializer()), Unit.serializer().nullable)
            }
        }
    }

    private fun richiesta(percorso: String) = Request.Builder().url(baseUrl + percorso)

    private suspend fun <T> chiama(builder: Request.Builder, serial: KSerializer<T>): T = decodifica(esegui(builder), serial)

    private suspend fun esegui(builder: Request.Builder): Response {
        val tokenUsato = _sessione.value?.token
        val risposta = invia(conToken(builder, tokenUsato).build())
        if (risposta.code != 401) return controlla(risposta)
        risposta.close()
        val nuovo = rinnovo.rinnova(tokenUsato) ?: throw ApiException("Sessione scaduta", 401)
        return controlla(invia(conToken(builder, nuovo).build()))
    }

    private fun conToken(builder: Request.Builder, token: String?) =
        if (token != null) builder.header("Authorization", "Bearer $token") else builder

    /** null: la sessione non è più rinnovabile e si torna al login. */
    private suspend fun rinnovaDalServer(): String? {
        val refresh = _sessione.value?.refreshToken ?: return null
        return try {
            val nuova = postPubblico("/auth/rinnova", json(Rinnova(refresh), Rinnova.serializer()), Sessione.serializer())
            apri(nuova)
            nuova.token
        } catch (e: ApiException) {
            // Un 400 è un rifiuto definitivo. Una rete caduta no: la sessione resta per il prossimo tentativo
            if (e.offline) throw e
            chiudi()
            null
        }
    }

    private suspend fun controlla(risposta: Response): Response {
        if (risposta.isSuccessful) return risposta
        val corpo = withContext(Dispatchers.IO) { risposta.use { it.body?.string().orEmpty() } }
        val messaggio = runCatching {
            val busta = JsonEdo.decodeFromString(Risposta.serializer(Unit.serializer().nullable), corpo)
            busta.message ?: busta.error
        }.getOrNull()
        throw ApiException(messaggio ?: "Si è verificato un errore. Riprova.", risposta.code)
    }

    private suspend fun <T> decodifica(risposta: Response, serial: KSerializer<T>): T {
        val verificata = controlla(risposta)
        val testo = withContext(Dispatchers.IO) { verificata.use { it.body?.string().orEmpty() } }
        val busta = JsonEdo.decodeFromString(Risposta.serializer(serial), testo)
        @Suppress("UNCHECKED_CAST")
        return busta.data ?: (null as T)
    }

    private suspend fun invia(request: Request): Response = suspendCancellableCoroutine { continuazione ->
        val call = http.newCall(request)
        continuazione.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuazione.isActive) {
                    continuazione.resumeWithException(ApiException("Sei offline. Controlla la connessione e riprova.", 0))
                }
            }

            override fun onResponse(call: Call, response: Response) = continuazione.resume(response)
        })
    }

    companion object {
        private val MEDIA_JSON = "application/json".toMediaType()
        fun <T> json(valore: T, serial: KSerializer<T>): RequestBody =
            JsonEdo.encodeToString(serial, valore).toRequestBody(MEDIA_JSON)

        val VUOTO: RequestBody = ByteArray(0).toRequestBody(null, 0, 0)
    }
}

inline fun <reified T> serialDi(): KSerializer<T> = serializer()
