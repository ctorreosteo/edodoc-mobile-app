package it.edodoc.app.feature.comune

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.edodoc.app.ui.kit.Async
import it.edodoc.app.ui.kit.BottoneIcona
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.theme.Forme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun cartellaFatture(context: Context) = File(context.cacheDir, "fatture").apply { mkdirs() }

fun condividiPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Condividi la fattura"))
}

private suspend fun pagine(file: File, larghezza: Int): List<Bitmap> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { pdf ->
            (0 until pdf.pageCount).map { i ->
                pdf.openPage(i).use { p ->
                    val bitmap = Bitmap.createBitmap(larghezza, larghezza * p.height / p.width, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AColor.WHITE)
                    p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}

/** Anteprima nell'app, con condivisione: il PDF scaricato resta nella cache privata. */
@Composable
fun AnteprimaPdf(chiave: String, nome: String, scarica: suspend (File) -> File, onIndietro: () -> Unit) {
    val context = LocalContext.current
    val file = File(cartellaFatture(context), nome.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "fattura.pdf" })
    Schermo(onIndietro, azioni = {
        BottoneIcona(Icons.Rounded.IosShare, "Condividi") { if (file.exists()) condividiPdf(context, file) }
    }) {
        Async("pdf-$chiave", { val f = scarica(file); pagine(f, 1240) }) { pagine, _ ->
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(pagine) { p ->
                    Image(p.asImageBitmap(), "Pagina della fattura", Modifier.fillMaxWidth().aspectRatio(p.width.toFloat() / p.height).clip(Forme.Controllo).background(Color.White), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
