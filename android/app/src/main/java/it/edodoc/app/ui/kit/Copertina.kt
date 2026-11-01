package it.edodoc.app.ui.kit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import it.edodoc.app.Grafo
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

/** Le copertine si leggono senza login. Senza copertina, resta la brace: mai un riquadro grigio. */
@Composable
fun Copertina(id: String?, modifier: Modifier = Modifier, versione: Int = 0) {
    val c = colori
    var immagine by remember(id, versione) { mutableStateOf(id?.let { cache.get(it) }) }
    LaunchedEffect(id, versione) {
        if (id != null && (immagine == null || versione > 0)) {
            immagine = withContext(Dispatchers.IO) {
                runCatching {
                    val richiesta = Request.Builder().url(Grafo.urlCopertina(id)).build()
                    Grafo.api.http.newCall(richiesta).execute().use { r ->
                        if (!r.isSuccessful) return@use null
                        val byte = r.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(byte, 0, byte.size)
                    }
                }.getOrNull()?.also { cache.put(id, it) }
            }
        }
    }
    Box(modifier.background(Brush.linearGradient(listOf(c.brand, c.ember.copy(alpha = .75f))))) {
        // Senza immagine, la O del logo in grande sulla brace
        if (immagine == null) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension * 1.25f
                drawOval(Color.White.copy(alpha = .1f), Offset(size.width - w * .72f, size.height * .1f), Size(w, w * .58f), style = Stroke(size.minDimension * .09f))
            }
        }
        immagine?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
