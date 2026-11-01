package it.edodoc.app.ui.kit

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.colori

/** Ciò che sta dietro al vetro: registrato una volta sola da chi lo ospita, ridisegnato sfocato da ogni pannello. */
class Sfondo(val layer: GraphicsLayer) {
    var origine by mutableStateOf(Offset.Zero)
}

private val LocalSfondo = compositionLocalOf<Sfondo?> { null }

@Composable
fun VetroHost(modifier: Modifier = Modifier, contenuto: @Composable BoxScope.() -> Unit, sopra: @Composable BoxScope.() -> Unit) {
    if (Build.VERSION.SDK_INT < 31) {
        Box(modifier) {
            contenuto()
            sopra()
        }
        return
    }
    val layer = rememberGraphicsLayer()
    val sfondo = remember(layer) { Sfondo(layer) }
    Box(modifier.onGloballyPositioned { sfondo.origine = it.positionInRoot() }) {
        Box(
            Modifier.matchParentSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) { contenuto() }
        CompositionLocalProvider(LocalSfondo provides sfondo) { sopra() }
    }
}

/** Il vetro vero: blur del contenuto retrostante da API 31, superficie traslucida prima. */
@Composable
fun Modifier.vetro(forma: Shape = Forme.Pannello, sfocatura: Dp = 28.dp): Modifier {
    val c = colori
    val sfondo = LocalSfondo.current
    val raggio = with(LocalDensity.current) { sfocatura.toPx() }
    val base = this.clip(forma)
    val conBlur = if (sfondo != null && Build.VERSION.SDK_INT >= 31) {
        val sfocato = rememberGraphicsLayer()
        val posizione = remember { Posizione() }
        base.onGloballyPositioned { posizione.aggiorna(it) }.drawBehind {
            sfocato.renderEffect = BlurEffect(raggio, raggio, TileMode.Mirror)
            sfocato.record {
                translate(sfondo.origine.x - posizione.valore.x, sfondo.origine.y - posizione.valore.y) { drawLayer(sfondo.layer) }
            }
            drawLayer(sfocato)
        }
    } else if (Build.VERSION.SDK_INT >= 31) {
        // Pannello nel contenuto: nessun livello da sfocare, resta una lastra che lascia passare la brace
        return base.background(c.ink.copy(alpha = if (c.scuro) .07f else .05f)).border(BorderStroke(.75.dp, c.glassBordo), forma)
    } else {
        base.background(c.canvasElevated.copy(alpha = .92f))
    }
    return conBlur.background(c.glass).border(BorderStroke(.75.dp, c.glassBordo), forma)
}

private class Posizione {
    var valore by mutableStateOf(Offset.Zero)
    fun aggiorna(coordinate: LayoutCoordinates) {
        valore = coordinate.positionInRoot()
    }
}
