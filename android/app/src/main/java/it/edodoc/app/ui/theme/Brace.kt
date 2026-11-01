package it.edodoc.app.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.launch

/** La fonte calda dietro al contenuto: respira in 9 secondi e si sposta verso ciò che conta (posizione in 0..1). */
private const val SHADER = """
uniform float2 risoluzione;
uniform float tempo;
uniform float2 fuoco;
uniform float forza;
uniform float3 canvas;
uniform float3 brace;
uniform float3 profondo;

float rumore(float2 p) {
    return sin(p.x * 1.7 + tempo * .35) * .5 + sin(p.y * 2.3 - tempo * .28) * .5
        + sin((p.x + p.y) * 1.1 + tempo * .21) * .35;
}

half4 main(float2 xy) {
    float2 p = xy / risoluzione.y;
    float2 c = fuoco * float2(risoluzione.x / risoluzione.y, 1.0);
    float respiro = .5 + .5 * sin(tempo * 6.2831 / 9.0);
    float2 deriva = float2(sin(tempo * .23) * .06, cos(tempo * .19) * .05);
    float d = distance(p + rumore(p * 1.4) * .035, c + deriva);
    float nucleo = exp(-d * d * (13.0 - respiro * 3.0));
    float alone = exp(-d * d * 2.6);
    float3 col = mix(canvas, profondo, alone * .85);
    col = mix(col, brace, nucleo * forza * (.72 + respiro * .28));
    float grana = fract(sin(dot(xy, float2(12.9898, 78.233))) * 43758.5453) - .5;
    return half4(half3(col + grana * .012), 1.0);
}
"""

@Composable
fun BoxScope.Brace(
    fuoco: Offset = Offset(.72f, .16f),
    forza: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val c = colori
    val ridotto = LocalMovimentoRidotto.current
    val x = remember { Animatable(fuoco.x) }
    val y = remember { Animatable(fuoco.y) }
    val f = remember { Animatable(forza) }
    LaunchedEffect(fuoco, forza, ridotto) {
        if (ridotto) {
            x.snapTo(fuoco.x); y.snapTo(fuoco.y); f.snapTo(forza)
        } else {
            val molla = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow)
            kotlinx.coroutines.coroutineScope {
                launch { x.animateTo(fuoco.x, molla) }
                launch { y.animateTo(fuoco.y, molla) }
                launch { f.animateTo(forza, molla) }
            }
        }
    }
    var tempo by remember { mutableFloatStateOf(0f) }
    if (Build.VERSION.SDK_INT >= 33 && !ridotto) {
        LaunchedEffect(Unit) {
            val inizio = withFrameNanos { it }
            while (true) {
                withFrameNanos { tempo = (it - inizio) / 1e9f }
            }
        }
    }
    val forzaTema = if (c.scuro) .68f else .42f
    val profondo = if (c.scuro) Color(0xFF14243F) else Color(0xFFFBE9D8)
    if (Build.VERSION.SDK_INT >= 33) {
        val shader = remember { creaShader() }
        Canvas(modifier.fillMaxSize()) {
            disegna(shader, tempo, Offset(x.value, y.value), f.value * forzaTema, c.canvas, c.ember, profondo)
        }
    } else {
        Canvas(modifier.fillMaxSize()) {
            drawRect(c.canvas)
            val centro = Offset(size.width * x.value, size.height * y.value)
            val raggio = size.maxDimension * .62f
            drawRect(Brush.radialGradient(listOf(profondo, Color.Transparent), centro, raggio))
            drawRect(Brush.radialGradient(listOf(c.ember.copy(alpha = f.value * forzaTema), Color.Transparent), centro, raggio * .55f))
        }
    }
}

@RequiresApi(33)
private fun creaShader() = RuntimeShader(SHADER)

@RequiresApi(33)
private fun DrawScope.disegna(
    shader: RuntimeShader, tempo: Float, fuoco: Offset, forza: Float, canvas: Color, brace: Color, profondo: Color,
) {
    shader.setFloatUniform("risoluzione", size.width, size.height)
    shader.setFloatUniform("tempo", tempo)
    shader.setFloatUniform("fuoco", fuoco.x, fuoco.y)
    shader.setFloatUniform("forza", forza.coerceIn(0f, 1f))
    shader.setFloatUniform("canvas", canvas.red, canvas.green, canvas.blue)
    shader.setFloatUniform("brace", brace.red, brace.green, brace.blue)
    shader.setFloatUniform("profondo", profondo.red, profondo.green, profondo.blue)
    drawRect(ShaderBrush(shader))
}
