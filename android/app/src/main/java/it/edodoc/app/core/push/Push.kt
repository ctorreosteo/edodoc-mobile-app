package it.edodoc.app.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.edodoc.app.Grafo
import it.edodoc.app.MainActivity
import it.edodoc.app.R
import it.edodoc.app.core.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Dove porta il tocco su una notifica. I valori di data arrivano sempre come stringhe. */
data class Destinazione(val tipo: String, val checkId: Long?, val pazienteId: Long?) {
    companion object {
        fun da(valori: Bundle?): Destinazione? {
            val tipo = valori?.getString("tipo") ?: return null
            if (tipo != "MESSAGGIO" && tipo != "FEEDBACK") return null
            return Destinazione(tipo, valori.getString("checkId")?.toLongOrNull(), valori.getString("pazienteId")?.toLongOrNull())
        }
    }
}

class Push(private val context: Context, private val api: Api) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun creaCanali() {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(NotificationChannel(CANALE_MESSAGGI, context.getString(R.string.canale_messaggi), NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(CANALE_FEEDBACK, context.getString(R.string.canale_feedback), NotificationManager.IMPORTANCE_HIGH))
    }

    /** Senza google-services.json Firebase non esiste: l'app funziona, solo senza push. */
    private val disponibile get() = FirebaseApp.getApps(context).isNotEmpty()

    /** A ogni apertura: i token si rinnovano e il backend ne elimina di non validi. */
    fun registra() {
        if (!disponibile) return
        scope.launch {
            runCatching { api.registraDispositivo(FirebaseMessaging.getInstance().token.await()) }
        }
    }

    fun registra(token: String) {
        if (Grafo.api.sessione.value == null) return
        scope.launch { runCatching { api.registraDispositivo(token) } }
    }

    suspend fun tokenCorrente(): String? =
        if (disponibile) runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() else null

    companion object {
        const val CANALE_MESSAGGI = "messaggi"
        const val CANALE_FEEDBACK = "feedback"
    }
}

class EdoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Grafo.push.registra(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val titolo = message.notification?.title ?: "EdoDoc"
        val testo = message.notification?.body ?: return
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        message.data.forEach { (k, v) -> intent.putExtra(k, v) }
        val apertura = PendingIntent.getActivity(this, message.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val canale = if (message.data["tipo"] == "FEEDBACK") Push.CANALE_FEEDBACK else Push.CANALE_MESSAGGI
        val notifica = NotificationCompat.Builder(this, canale)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(getColor(R.color.ember))
            .setContentTitle(titolo)
            .setContentText(testo)
            .setAutoCancel(true)
            .setContentIntent(apertura)
            .build()
        getSystemService<NotificationManager>()?.notify(message.hashCode(), notifica)
    }
}
