package it.edodoc.app

import it.edodoc.app.core.net.JsonEdo
import it.edodoc.app.model.CheckDettaglio
import it.edodoc.app.model.PazienteRiga
import it.edodoc.app.model.Risposte
import it.edodoc.app.model.VisitaRiga
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class DtoTest {
    @Test
    fun `le date restano locali, con e senza microsecondi`() {
        val v = JsonEdo.decodeFromString<VisitaRiga>(
            """{"id":1,"pazienteId":2,"pazienteNome":"Luca","tipo":"CONTROLLO","modalita":"ONLINE","stato":"PROGRAMMATA","inizio":"2026-11-13T11:15:00","fine":"2026-11-13T11:45:00.123456"}""",
        )
        assertEquals(LocalDateTime.of(2026, 11, 13, 11, 15), v.inizio)
        assertEquals(123456000, v.fine!!.nano)
    }

    @Test
    fun `gli importi restano esatti e i campi sconosciuti non rompono`() {
        val p = JsonEdo.decodeFromString<PazienteRiga>(
            """{"id":1,"nome":"A","cognome":"B","residuo":1500.10,"pesoInizialeKg":82.35,"campoFuturo":{"x":1},"stato":"STATO_NUOVO","percorso":{"id":1,"pazienteId":1,"tipo":"METODO_BUSSOLA","dataInizio":"2026-11-01","visiteTotali":6,"visiteFissate":1,"visiteEffettuate":0,"stato":"ATTIVO"}}""",
        )
        assertEquals(BigDecimal("1500.10"), p.residuo)
        assertEquals(LocalDate.of(2026, 11, 1), p.percorso!!.dataInizio)
        assertEquals("STATO_NUOVO", p.stato)
    }

    @Test
    fun `un feedback solo vocale conta come feedback da leggere`() {
        val c = JsonEdo.decodeFromString<CheckDettaglio>(
            """{"id":69,"pazienteId":16,"pazienteNome":"Luca","settimana":"2026-11-02","feedback":null,"feedbackIl":"2026-11-01T18:00:00","feedbackLettoIl":null,"feedbackAudioId":"7f1c","feedbackAudioSecondi":12}""",
        )
        assertTrue(c.haFeedback)
        assertTrue(c.daLeggere)
        assertNull(c.feedback)
        assertFalse(c.haSegnali)
    }

    @Test
    fun `il peso parte come numero JSON, non come stringa`() {
        val json = JsonEdo.encodeToString(Risposte(pastiOttimali = 14, energia = 3, pesoKg = BigDecimal("81.5"), impegno = 4))
        assertTrue(json, json.contains("\"pesoKg\":81.5"))
        assertTrue(json, json.contains("\"energia\":3"))
    }
}
