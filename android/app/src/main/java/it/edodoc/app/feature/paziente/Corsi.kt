package it.edodoc.app.feature.paziente

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import it.edodoc.app.Grafo
import it.edodoc.app.model.AppCorso
import it.edodoc.app.ui.kit.Async
import it.edodoc.app.ui.kit.Copertina
import it.edodoc.app.ui.kit.LocalSpazioBarra
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Markdown
import it.edodoc.app.ui.kit.Riga
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.Vuoto
import it.edodoc.app.ui.kit.durata
import it.edodoc.app.ui.kit.durataLunga
import it.edodoc.app.ui.kit.piede
import it.edodoc.app.ui.kit.sezione
import it.edodoc.app.ui.kit.testata
import it.edodoc.app.ui.nav.condivisa
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori

private fun meta(c: AppCorso) = buildList {
    add(if (c.lezioni == 1) "1 lezione" else "${c.lezioni} lezioni")
    if (c.secondiVideo > 0) add(durataLunga(c.secondiVideo))
}.joinToString(" · ")

@Composable
fun CorsiSchermo(nav: NavHostController) {
    Schermo(forza = .7f) {
        Async("corsi", { Grafo.edo.corsi() }) { corsi, _ ->
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                testata("Corsi", "Impara con calma", conBarra = false)
                if (corsi.isEmpty()) item { Vuoto("Nessun corso, per ora", "Quando il Dr. Bo ne pubblica uno, lo trovi qui.") }
                items(corsi, key = { it.id }) { c -> Locandina(c) { nav.navigate("corso/${c.id}") } }
                piede()
            }
        }
    }
}

@Composable
private fun Locandina(c: AppCorso, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(.82f).clip(Forme.Pannello).clickable(role = Role.Button, onClick = onClick)) {
        Copertina(c.copertinaId, Modifier.fillMaxSize().condivisa("copertina-${c.id}"))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = .78f))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Sopratitolo(meta(c), colore = Color.White.copy(alpha = .78f))
            Spacer(Modifier.height(8.dp))
            Testo(c.titolo, Tipo.Title, Color.White, righe = 3)
        }
    }
}

@Composable
fun CorsoDettaglio(id: Long, nav: NavHostController) {
    Schermo({ nav.popBackStack() }, forza = .5f) {
        Async("corso-$id", { Grafo.edo.corso(id) }) { d, _ ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Box(Modifier.fillMaxWidth().aspectRatio(.95f)) {
                        Copertina(d.corso.copertinaId, Modifier.fillMaxSize().condivisa("copertina-$id"))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to colori.canvas)))
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = MargineSchermo)) {
                            Sopratitolo(meta(d.corso))
                            Spacer(Modifier.height(8.dp))
                            Testo(d.corso.titolo, Tipo.Display)
                        }
                    }
                }
                d.corso.descrizione?.takeIf { it.isNotBlank() }?.let {
                    item { Testo(it, colore = colori.inkSecondary, modifier = Modifier.padding(start = MargineSchermo, end = MargineSchermo, top = 14.dp)) }
                }
                d.moduli.forEach { m ->
                    sezione(m.titolo)
                    items(m.lezioni, key = { it.id }) { l ->
                        Riga(
                            l.titolo,
                            l.durataSecondi?.let { durata(it) } ?: if (l.tipo == "TESTO") "Da leggere" else null,
                            inizio = {
                                Icon(if (l.tipo == "VIDEO") Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.Article, null, tint = colori.ember, modifier = Modifier.size(22.dp))
                            },
                            onClick = { nav.navigate("lezione/${l.id}") },
                        )
                    }
                }
                piede()
            }
        }
    }
}

@Composable
fun LezioneSchermo(id: Long, onIndietro: () -> Unit) {
    val orizzontale = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Schermo(if (orizzontale) null else onIndietro, forza = .5f) {
        Async("lezione-$id", { Grafo.edo.lezione(id) }) { l, _ ->
            val hls = l.riproduzione?.hlsUrl
            // L'URL firmato dura un'ora: quello con cui parte il player resta valido per tutta la visione
            val url = remember(l.id) { hls }
            if (l.tipo == "VIDEO" && url != null && orizzontale) {
                Video(url, Modifier.fillMaxSize().background(Color.Black))
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(top = 72.dp, bottom = 32.dp)) {
                    if (l.tipo == "VIDEO") {
                        if (url != null) {
                            Video(url, Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9).clip(Forme.Controllo).background(Color.Black))
                        } else {
                            Vuoto("Video non ancora disponibile", "Il Dr. Bo lo sta preparando: riprova tra poco.")
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                    Column(Modifier.padding(horizontal = MargineSchermo)) {
                        Testo(l.titolo, Tipo.Display.copy(fontSize = Tipo.Title.fontSize * 1.3f, lineHeight = Tipo.Title.lineHeight * 1.3f))
                        l.descrizione?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(10.dp))
                            Testo(it, colore = colori.inkSecondary)
                        }
                        l.contenuto?.takeIf { it.isNotBlank() && l.tipo == "TESTO" }?.let {
                            Spacer(Modifier.height(22.dp))
                            Markdown(it)
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Video(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(HlsMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(MediaItem.fromUri(Uri.parse(url))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView({ PlayerView(it).apply { this.player = player } }, modifier)
}
