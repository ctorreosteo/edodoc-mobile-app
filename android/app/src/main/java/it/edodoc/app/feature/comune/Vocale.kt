package it.edodoc.app.feature.comune

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import it.edodoc.app.core.media.formaOnda
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.durata
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val BARRE = 56

/** Disegna l'onda: la parte ascoltata è accesa di brace. */
@Composable
fun Onda(livelli: List<Float>, avanzamento: Float, modifier: Modifier = Modifier) {
    val c = colori
    Canvas(modifier) {
        val n = livelli.size.coerceAtLeast(1)
        val passo = size.width / n
        val larghezza = passo * .55f
        livelli.forEachIndexed { i, l ->
            val h = (l.coerceAtLeast(.05f)) * size.height
            val acceso = (i + .5f) / n <= avanzamento
            drawRoundRect(
                if (acceso) c.ember else c.ink.copy(alpha = .22f),
                Offset(i * passo, (size.height - h) / 2),
                Size(larghezza, h),
                CornerRadius(larghezza / 2),
            )
        }
    }
}

/** La forma d'onda reale è l'interfaccia: si trascina per scorrere. */
@Composable
fun LettoreVocale(file: File, secondi: Int, modifier: Modifier = Modifier) {
    val c = colori
    val context = LocalContext.current
    val livelli by produceState(List(BARRE) { .05f }, file) {
        value = withContext(Dispatchers.Default) { runCatching { formaOnda(file.absolutePath, BARRE) }.getOrNull() } ?: value
    }
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            prepare()
        }
    }
    var inRiproduzione by remember { mutableStateOf(false) }
    var avanzamento by remember { mutableFloatStateOf(0f) }
    var trascinando by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val ascolto = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                inRiproduzione = isPlaying
            }

            override fun onPlaybackStateChanged(stato: Int) {
                if (stato == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                    avanzamento = 0f
                }
            }
        }
        player.addListener(ascolto)
        onDispose { player.release() }
    }
    LaunchedEffect(inRiproduzione, trascinando) {
        while (inRiproduzione && !trascinando) {
            val totale = player.duration.takeIf { it > 0 } ?: (secondi * 1000L)
            avanzamento = (player.currentPosition.toFloat() / totale).coerceIn(0f, 1f)
            delay(40)
        }
    }
    fun vaiA(frazione: Float) {
        val totale = player.duration.takeIf { it > 0 } ?: (secondi * 1000L)
        avanzamento = frazione.coerceIn(0f, 1f)
        player.seekTo((avanzamento * totale).toLong())
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(c.ember)
                .clickable(role = Role.Button) { if (player.isPlaying) player.pause() else player.play() }
                .semantics { contentDescription = if (inRiproduzione) "Metti in pausa" else "Ascolta il vocale" },
            contentAlignment = Alignment.Center,
        ) { Icon(if (inRiproduzione) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = c.onEmber, modifier = Modifier.size(30.dp)) }
        Column(Modifier.weight(1f)) {
            Onda(
                livelli, avanzamento,
                Modifier.fillMaxWidth().height(64.dp)
                    .pointerInput(Unit) { detectTapGestures { vaiA(it.x / size.width) } }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { trascinando = true },
                            onDragEnd = { trascinando = false },
                            onDragCancel = { trascinando = false },
                        ) { cambio, _ -> vaiA(cambio.position.x / size.width) }
                    }
                    .semantics {
                        contentDescription = "Posizione nel vocale"
                        progressBarRangeInfo = ProgressBarRangeInfo(avanzamento, 0f..1f)
                        setProgress { vaiA(it); true }
                    },
            )
            Testo("${durata((avanzamento * secondi).toInt())} / ${durata(secondi)}", Tipo.Numero.copy(fontSize = Tipo.Label.fontSize), c.inkSecondary)
        }
    }
}
