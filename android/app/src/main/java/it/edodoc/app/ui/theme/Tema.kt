package it.edodoc.app.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import it.edodoc.app.R

/** Token di docs/DESIGN.md. */
@Immutable
data class Colori(
    val scuro: Boolean,
    val canvas: Color,
    val canvasElevated: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val hairline: Color,
    val brand: Color,
    val ember: Color,
    val emberSoft: Color,
    val positive: Color,
    val warning: Color,
    val critical: Color,
    val onEmber: Color,
    val glass: Color,
    val glassBordo: Color,
)

private val Inchiostro = Color(0xFFF4F1EC)
private val Notte = Color(0xFF0F1C31)

val ColoriScuri = Colori(
    scuro = true,
    canvas = Color(0xFF080F1C),
    canvasElevated = Color(0xFF101B2E),
    ink = Inchiostro,
    inkSecondary = Inchiostro.copy(alpha = .62f),
    inkTertiary = Inchiostro.copy(alpha = .38f),
    hairline = Color.White.copy(alpha = .09f),
    brand = Color(0xFF1F3A63),
    ember = Color(0xFFFF8F3D),
    emberSoft = Color(0xFFFFC48F),
    positive = Color(0xFF7FD8B0),
    warning = Color(0xFFFFC85C),
    critical = Color(0xFFFF7A70),
    onEmber = Color(0xFF1A0E05),
    glass = Color(0xFF16233A).copy(alpha = .55f),
    glassBordo = Color.White.copy(alpha = .13f),
)

val ColoriChiari = Colori(
    scuro = false,
    canvas = Color(0xFFF6F3EE),
    canvasElevated = Color(0xFFFFFFFF),
    ink = Notte,
    inkSecondary = Notte.copy(alpha = .62f),
    inkTertiary = Notte.copy(alpha = .42f),
    hairline = Notte.copy(alpha = .09f),
    brand = Color(0xFF1F3A63),
    ember = Color(0xFFE8701F),
    emberSoft = Color(0xFFF0944E),
    positive = Color(0xFF1E8A62),
    warning = Color(0xFFB97A00),
    critical = Color(0xFFC93A30),
    onEmber = Color(0xFFFFFFFF),
    glass = Color.White.copy(alpha = .62f),
    glassBordo = Color.White.copy(alpha = .75f),
)

@OptIn(ExperimentalTextApi::class)
private fun peso(w: Int) = Font(
    R.font.montserrat,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Montserrat = FontFamily(peso(300), peso(400), peso(500), peso(600), peso(700))

private val Taglio = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val Base = TextStyle(fontFamily = Montserrat, lineHeightStyle = Taglio)

object Tipo {
    val DisplayXL = Base.copy(fontWeight = FontWeight.Light, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-.04).em)
    val Display = Base.copy(fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-.03).em)
    val Title = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-.015).em)
    val Headline = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-.005).em)
    val Body = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp)
    val Label = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val Overline = Base.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = .16.em)
    val Numero = Base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum")
}

object Forme {
    val Pannello = RoundedCornerShape(28.dp)
    val Controllo = RoundedCornerShape(18.dp)
    val Capsula = RoundedCornerShape(percent = 50)
}

val LocalColori = staticCompositionLocalOf { ColoriScuri }

/** Riduci movimento: animazioni di sistema disattivate. La brace si ferma, le molle diventano dissolvenze. */
val LocalMovimentoRidotto = compositionLocalOf { false }

val colori: Colori
    @Composable get() = LocalColori.current

@Composable
fun EdoTema(content: @Composable () -> Unit) {
    val scuro = isSystemInDarkTheme()
    val c = if (scuro) ColoriScuri else ColoriChiari
    val context = LocalContext.current
    val ridotto = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // L'activity gestisce da sé il cambio di tema: le icone di sistema vanno riallineate qui
    val vista = LocalView.current
    SideEffect {
        (vista.context as? Activity)?.window?.let { finestra ->
            WindowCompat.getInsetsController(finestra, vista).apply {
                isAppearanceLightStatusBars = !scuro
                isAppearanceLightNavigationBars = !scuro
            }
        }
    }
    val schema = if (scuro) {
        darkColorScheme(
            primary = c.ember, onPrimary = c.onEmber, secondary = c.emberSoft, background = c.canvas, onBackground = c.ink,
            surface = c.canvasElevated, onSurface = c.ink, onSurfaceVariant = c.inkSecondary, surfaceVariant = c.canvasElevated,
            surfaceContainerHigh = c.canvasElevated, surfaceContainerHighest = c.canvasElevated, surfaceContainer = c.canvasElevated,
            surfaceContainerLow = c.canvasElevated, outline = c.hairline, outlineVariant = c.hairline, error = c.critical,
        )
    } else {
        lightColorScheme(
            primary = c.ember, onPrimary = c.onEmber, secondary = c.emberSoft, background = c.canvas, onBackground = c.ink,
            surface = c.canvasElevated, onSurface = c.ink, onSurfaceVariant = c.inkSecondary, surfaceVariant = c.canvas,
            surfaceContainerHigh = c.canvasElevated, surfaceContainerHighest = c.canvasElevated, surfaceContainer = c.canvasElevated,
            surfaceContainerLow = c.canvasElevated, outline = c.hairline, outlineVariant = c.hairline, error = c.critical,
        )
    }
    CompositionLocalProvider(
        LocalColori provides c,
        LocalMovimentoRidotto provides ridotto,
        LocalTextSelectionColors provides TextSelectionColors(c.ember, c.ember.copy(alpha = .3f)),
    ) {
        MaterialTheme(colorScheme = schema, content = content)
    }
}
