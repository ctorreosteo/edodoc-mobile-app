package it.edodoc.app

import it.edodoc.app.feature.auth.tokenDaInvito
import it.edodoc.app.feature.comune.Bolla
import it.edodoc.app.feature.comune.Stato
import it.edodoc.app.feature.comune.unisci
import it.edodoc.app.ui.kit.Blocco
import it.edodoc.app.ui.kit.blocchiMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ChatTest {
    private val ora = LocalDateTime.of(2026, 11, 1, 10, 0)
    private fun bolla(id: Long?, cid: String?, stato: Stato = Stato.Inviato) = Bolla("k${id ?: cid}", id, true, "ciao", ora, false, cid, stato)

    @Test
    fun `la risposta del server sostituisce la bolla ottimistica con lo stesso clientId`() {
        val pending = bolla(null, "uuid-1", Stato.Invio)
        val r = unisci(listOf(pending), listOf(bolla(5, "uuid-1")))
        assertEquals(1, r.size)
        assertEquals(5L, r[0].id)
        assertEquals(Stato.Inviato, r[0].stato)
        assertEquals("La chiave resta: la bolla non deve saltare", pending.chiave, r[0].chiave)
    }

    @Test
    fun `lo stesso messaggio da polling e da invio non si duplica`() {
        val r = unisci(listOf(bolla(5, "uuid-1")), listOf(bolla(5, "uuid-1"), bolla(6, null)))
        assertEquals(listOf(5L, 6L), r.map { it.id })
    }

    @Test
    fun `i messaggi in invio restano in fondo, le pagine vecchie in cima`() {
        val r = unisci(listOf(bolla(10, null), bolla(null, "p", Stato.Invio)), listOf(bolla(3, null)))
        assertEquals(listOf(3L, 10L, null), r.map { it.id })
    }

    @Test
    fun `invito da link intero, da token o da testo sbagliato`() {
        assertEquals("abc", tokenDaInvito(" edodoc://imposta-password?token=abc "))
        assertEquals("abc", tokenDaInvito("https://x.it/imposta?a=1&token=abc"))
        assertEquals("abc-123", tokenDaInvito("abc-123"))
        assertNull(tokenDaInvito("https://x.it/altro"))
        assertNull(tokenDaInvito("non è un token"))
    }

    @Test
    fun `markdown delle lezioni`() {
        val b = blocchiMarkdown("# Titolo\n\nRiga uno\nriga due\n\n- punto\n1. primo\n> nota")
        assertEquals(Blocco.Titolo(1, "Titolo"), b[0])
        assertEquals(Blocco.Paragrafo("Riga uno riga due"), b[1])
        assertEquals(Blocco.Punto("•", "punto"), b[2])
        assertEquals(Blocco.Punto("1.", "primo"), b[3])
        assertEquals(Blocco.Citazione("nota"), b[4])
    }
}
