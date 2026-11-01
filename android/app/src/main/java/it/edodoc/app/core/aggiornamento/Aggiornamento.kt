package it.edodoc.app.core.aggiornamento

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.tasks.await

/** Vale anche per un aggiornamento lasciato a metà: Play lo riprende da dove era. */
fun daInstallare(disponibilita: Int) =
    disponibilita == UpdateAvailability.UPDATE_AVAILABLE ||
        disponibilita == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

/** Decide Google Play. Fuori da Play (debug, installazione manuale) non risulta mai nulla. */
class Aggiornamento(context: Context) {
    private val play = AppUpdateManagerFactory.create(context)

    suspend fun disponibile(): Boolean = info() != null

    /** false: il flusso di Play non è partito. */
    suspend fun avvia(flusso: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        // Un AppUpdateInfo vale per un solo avvio: si rilegge a ogni tentativo
        val info = info() ?: return false
        return play.startUpdateFlowForResult(info, flusso, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
    }

    // Se Play non risponde l'app resta usabile: un paziente non si blocca per un controllo fallito
    private suspend fun info(): AppUpdateInfo? =
        runCatching { play.appUpdateInfo.await() }.getOrNull()?.takeIf { daInstallare(it.updateAvailability()) }
}
