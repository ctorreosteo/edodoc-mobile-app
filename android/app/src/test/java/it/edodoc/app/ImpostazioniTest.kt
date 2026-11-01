package it.edodoc.app

import it.edodoc.app.feature.dottore.valoreVoce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImpostazioniTest {
    @Test
    fun `i campi numerici rifiutano il testo che fermerebbe fatture e Finance`() {
        assertNull(valoreVoce("FATTURA_PROSSIMO_NUMERO", "abc"))
        assertNull(valoreVoce("FATTURA_PROSSIMO_NUMERO", ""))
        assertNull(valoreVoce("FATTURA_PROSSIMO_NUMERO", "12,5"))
        assertNull(valoreVoce("FISCO_ALIQUOTA_CASSA", "cinque"))
        assertNull(valoreVoce("LISTINO_RATA_PERCORSO", "-1"))
    }

    @Test
    fun `i numeri validi arrivano nel formato del backend`() {
        assertEquals("459", valoreVoce("FATTURA_PROSSIMO_NUMERO", " 459 "))
        assertEquals("0.04", valoreVoce("FATTURA_ALIQUOTA_CONTRIBUTO", "0,04"))
        assertEquals("1500.50", valoreVoce("LISTINO_PAGAMENTO_UNICO", "1500.50"))
    }

    @Test
    fun `testi e interruttori`() {
        assertEquals("Via Roma 1", valoreVoce("VISITA_LUOGO_PRESENZA", " Via Roma 1 "))
        assertEquals("true", valoreVoce("FATTURA_BOLLO_DENTRO_IL_PREZZO", "true"))
        assertNull(valoreVoce("FATTURA_BOLLO_DENTRO_IL_PREZZO", "forse"))
    }
}
