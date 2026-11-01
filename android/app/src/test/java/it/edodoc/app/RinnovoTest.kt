package it.edodoc.app

import it.edodoc.app.core.net.ApiClient
import it.edodoc.app.core.net.ApiException
import it.edodoc.app.core.session.SessionStore
import it.edodoc.app.model.Sessione
import it.edodoc.app.model.Utente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private class MemoriaStore(var sessione: Sessione?) : SessionStore {
    override fun carica() = sessione
    override fun salva(sessione: Sessione) { this.sessione = sessione }
    override fun cancella() { sessione = null }
}

class RinnovoTest {
    private val server = MockWebServer()
    private val rinnovi = AtomicInteger()
    private var rispostaRinnovo = 200

    private val utente = Utente(1, "a@b.it", "A", "B", "PAZIENTE", 1)
    private fun sessione(token: String) = Sessione(token, "refresh-$token", utente)
    private val sessioneJson = """{"success":true,"data":{"token":"nuovo","refreshToken":"r2","utente":{"id":1,"username":"a@b.it","ruolo":"PAZIENTE"}}}"""

    @Before
    fun avvia() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/auth/rinnova" -> {
                    rinnovi.incrementAndGet()
                    // Come il backend: un secondo rinnovo con lo stesso refresh token verrebbe rifiutato
                    Thread.sleep(120)
                    if (rispostaRinnovo == 200 && rinnovi.get() == 1) MockResponse().setBody(sessioneJson)
                    else MockResponse().setResponseCode(400).setBody("""{"success":false,"message":"Sessione scaduta"}""")
                }
                request.getHeader("Authorization") == "Bearer nuovo" -> MockResponse().setBody("""{"success":true,"data":"ok"}""")
                else -> MockResponse().setResponseCode(401).setBody("""{"success":false,"message":"Sessione scaduta"}""")
            }
        }
        server.start()
    }

    @After
    fun ferma() = server.shutdown()

    private fun client(store: SessionStore) = ApiClient(server.url("/").toString().trimEnd('/'), store)

    @Test
    fun `venti richieste con token scaduto fanno un solo rinnovo e riescono tutte`() = runBlocking {
        val api = client(MemoriaStore(sessione("vecchio")))
        val esiti = withContext(Dispatchers.IO) {
            (1..20).map { async { api.get("/app/riepilogo", String.serializer()) } }.awaitAll()
        }
        assertEquals(List(20) { "ok" }, esiti)
        assertEquals(1, rinnovi.get())
        assertEquals("nuovo", api.sessione.value?.token)
    }

    @Test
    fun `un 400 sul rinnovo chiude la sessione e torna al login`() = runBlocking {
        rispostaRinnovo = 400
        val store = MemoriaStore(sessione("vecchio"))
        val api = client(store)
        try {
            api.get("/app/riepilogo", String.serializer())
            fail("Doveva fallire")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertNull(api.sessione.value)
        assertNull(store.sessione)
    }

    @Test
    fun `una rete caduta durante il rinnovo non butta via la sessione`() = runBlocking {
        val store = MemoriaStore(sessione("vecchio"))
        val api = client(store)
        server.shutdown()
        assertThrows(ApiException::class.java) {
            runBlocking { api.get("/app/riepilogo", String.serializer()) }
        }
        assertNotNull(store.sessione)
    }
}
