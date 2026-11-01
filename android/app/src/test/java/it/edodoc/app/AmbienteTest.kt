package it.edodoc.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbienteTest {
    @Test
    fun riconosceGliAccountDiProvaSenzaBadareAlleMaiuscole() {
        assertTrue(Ambiente.isAccountProva(" Prova.Dottore@Edodoc.it "))
        assertTrue(Ambiente.isAccountProva("prova.paziente@edodoc.it"))
    }

    @Test
    fun iPazientiVeriRestanoSulServerReale() {
        assertFalse(Ambiente.isAccountProva("edoardo@edodoc.it"))
        assertFalse(Ambiente.isAccountProva("prova.paziente@edodoc.it.esempio.it"))
    }
}
