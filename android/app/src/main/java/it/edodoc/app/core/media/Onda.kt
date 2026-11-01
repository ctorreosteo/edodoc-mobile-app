package it.edodoc.app.core.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlin.math.max
import kotlin.math.sqrt

/** La forma d'onda vera del vocale: decodifica l'audio e ne ricava il livello di [barre] intervalli, in 0..1. */
fun formaOnda(percorso: String, barre: Int): List<Float> {
    val estrattore = MediaExtractor()
    estrattore.setDataSource(percorso)
    val traccia = (0 until estrattore.trackCount).firstOrNull {
        estrattore.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: return emptyList()
    estrattore.selectTrack(traccia)
    val formato = estrattore.getTrackFormat(traccia)
    val durataUs = formato.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1)
    val codec = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
    val somme = DoubleArray(barre)
    val conteggi = IntArray(barre)
    try {
        codec.configure(formato, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var finito = false
        var fineIngresso = false
        while (!finito) {
            if (!fineIngresso) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val letti = estrattore.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (letti < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        fineIngresso = true
                    } else {
                        codec.queueInputBuffer(i, 0, letti, estrattore.sampleTime, 0)
                        estrattore.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val buffer = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                val barra = ((info.presentationTimeUs.toDouble() / durataUs) * barre).toInt().coerceIn(0, barre - 1)
                while (buffer.hasRemaining()) {
                    val v = buffer.get() / 32768.0
                    somme[barra] += v * v
                    conteggi[barra]++
                }
                codec.releaseOutputBuffer(o, false)
                finito = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
    } finally {
        runCatching { codec.stop() }
        codec.release()
        estrattore.release()
    }
    val livelli = FloatArray(barre) { if (conteggi[it] > 0) sqrt(somme[it] / conteggi[it]).toFloat() else 0f }
    val massimo = max(livelli.max(), .001f)
    return livelli.map { (it / massimo).coerceIn(0f, 1f) }
}
