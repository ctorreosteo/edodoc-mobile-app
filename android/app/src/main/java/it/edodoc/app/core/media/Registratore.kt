package it.edodoc.app.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Vocale del dottore: AAC mono in m4a. Con 32 kbps i 5 MB del backend sono circa 13 minuti. */
class Registratore(private val context: Context) {
    private var recorder: MediaRecorder? = null
    val file: File = File(context.cacheDir, "vocali/registrazione.m4a")
    var inizio = 0L
        private set

    fun avvia(onLimite: () -> Unit) {
        file.parentFile?.mkdirs()
        file.delete()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(32_000)
        r.setMaxFileSize(MASSIMO_BYTE)
        r.setOnInfoListener { _, cosa, _ ->
            if (cosa == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimite()
        }
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        inizio = System.currentTimeMillis()
    }

    /** In 0..1, per l'onda dal vivo. */
    fun livello(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)

    /** I secondi registrati. 0: troppo breve, e il file non vale. */
    fun ferma(): Int {
        val r = recorder ?: return 0
        recorder = null
        val secondi = ((System.currentTimeMillis() - inizio) / 1000).toInt()
        val valido = runCatching { r.stop() }.isSuccess
        r.release()
        return if (valido) secondi.coerceAtLeast(1) else 0
    }

    fun annulla() {
        ferma()
        file.delete()
    }

    private companion object {
        const val MASSIMO_BYTE = 4_900_000L
    }
}
