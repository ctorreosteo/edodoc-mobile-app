package it.edodoc.app

import com.google.android.play.core.install.model.UpdateAvailability
import it.edodoc.app.core.aggiornamento.daInstallare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AggiornamentoTest {
    @Test
    fun `una versione nuova blocca l'app`() {
        assertTrue(daInstallare(UpdateAvailability.UPDATE_AVAILABLE))
    }

    @Test
    fun `un aggiornamento lasciato a metà blocca l'app`() {
        assertTrue(daInstallare(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS))
    }

    @Test
    fun `senza versione nuova o senza risposta chiara l'app resta aperta`() {
        assertFalse(daInstallare(UpdateAvailability.UPDATE_NOT_AVAILABLE))
        assertFalse(daInstallare(UpdateAvailability.UNKNOWN))
    }
}
