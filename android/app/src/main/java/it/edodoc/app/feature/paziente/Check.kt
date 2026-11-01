package it.edodoc.app.feature.paziente

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import it.edodoc.app.Grafo
import it.edodoc.app.core.net.ApiException
import it.edodoc.app.model.CheckDettaglio
import it.edodoc.app.model.Risposte
import it.edodoc.app.model.StatoCheck
import it.edodoc.app.ui.kit.Async
import it.edodoc.app.ui.kit.AzioneBrace
import it.edodoc.app.ui.kit.AzioneQuieta
import it.edodoc.app.ui.kit.BottoneIcona
import it.edodoc.app.ui.kit.Campo
import it.edodoc.app.ui.kit.Filetto
import it.edodoc.app.ui.kit.LocalAvvisi
import it.edodoc.app.ui.kit.MargineSchermo
import it.edodoc.app.ui.kit.Scelta
import it.edodoc.app.ui.kit.Schermo
import it.edodoc.app.ui.kit.Sopratitolo
import it.edodoc.app.ui.kit.Testo
import it.edodoc.app.ui.kit.haptic
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.LocalMovimentoRidotto
import it.edodoc.app.ui.theme.Montserrat
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

private const val PASSI = 8

/** Il peso con cui il peso parte nel selettore: l'ultimo noto, non un numero a caso. */
private fun pesoIniziale(stato: StatoCheck, storico: List<CheckDettaglio>): Float =
    (stato.check?.pesoKg ?: storico.firstOrNull { it.pesoKg != null }?.pesoKg)?.toFloat() ?: 70f

@Composable
fun CheckSchermo(nav: NavHostController) {
    Async("check-corrente", { Grafo.edo.checkCorrente() to Grafo.edo.storicoCheck() }) { (stato, storico), _ ->
        val check = stato.check
        if (check?.haFeedback == true) {
            Schermo({ nav.popBackStack() }) {
                Column(Modifier.fillMaxSize().padding(horizontal = MargineSchermo), verticalArrangement = Arrangement.Center) {
                    Testo("Il Dr. Bo ha già risposto.", Tipo.Display)
                    Spacer(Modifier.height(12.dp))
                    Testo("Dopo il feedback il check non si può più cambiare. Se vuoi aggiungere qualcosa, scrivigli in chat.", colore = colori.inkSecondary)
                    Spacer(Modifier.height(28.dp))
                    AzioneBrace("Leggi il feedback") { nav.navigate("feedback/${check.id}") { popUpTo("check") { inclusive = true } } }
                }
            }
        } else {
            Flusso(stato, storico, nav)
        }
    }
}

@Composable
private fun Flusso(stato: StatoCheck, storico: List<CheckDettaglio>, nav: NavHostController) {
    val c0 = stato.check
    var passo by rememberSaveable { mutableStateOf(0) }
    var avanti by rememberSaveable { mutableStateOf(true) }
    var pasti by rememberSaveable { mutableStateOf(c0?.pastiOttimali ?: 14) }
    var liberi by rememberSaveable { mutableStateOf(c0?.pastiLiberi ?: "") }
    var energia by rememberSaveable { mutableStateOf(c0?.energia ?: 0) }
    var allenamento by rememberSaveable { mutableStateOf(c0?.allenamento ?: "") }
    var peso by rememberSaveable { mutableFloatStateOf(pesoIniziale(stato, storico)) }
    var conPeso by rememberSaveable { mutableStateOf(true) }
    var datiPeso by rememberSaveable { mutableStateOf(c0?.andamentoPeso ?: "") }
    var migliorata by rememberSaveable { mutableStateOf(c0?.percezioneMigliorata) }
    var percezione by rememberSaveable { mutableStateOf(c0?.percezione ?: "") }
    var impegno by rememberSaveable { mutableStateOf(c0?.impegno ?: 0) }
    var inviando by rememberSaveable { mutableStateOf(false) }
    var inviato by rememberSaveable { mutableStateOf(false) }
    var bloccato by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val avvisi = LocalAvvisi.current
    val conferma = haptic()
    fun vai(n: Int) {
        avanti = n > passo
        passo = n
    }
    BackHandler(passo > 0 && !inviato) { vai(passo - 1) }

    fun invia() {
        inviando = true
        scope.launch {
            try {
                Grafo.edo.inviaCheck(
                    Risposte(
                        pastiOttimali = pasti, pastiLiberi = liberi.ifBlank { null }, energia = energia, allenamento = allenamento.ifBlank { null },
                        pesoKg = if (conPeso) BigDecimal(peso.toDouble()).setScale(1, RoundingMode.HALF_UP) else null,
                        andamentoPeso = datiPeso.ifBlank { null }, percezione = percezione.ifBlank { null }, percezioneMigliorata = migliorata, impegno = impegno,
                    ),
                )
                conferma()
                inviato = true
            } catch (e: ApiException) {
                if (e.status == 409) bloccato = true else avvisi.mostra(e.message ?: "Invio non riuscito. Riprova.")
            }
            inviando = false
        }
    }

    Schermo(fuoco = Offset(.5f, .35f + .5f * passo / PASSI), forza = .7f + .5f * passo / PASSI) {
        Column(Modifier.fillMaxSize().imePadding().statusBarsPadding().navigationBarsPadding().padding(horizontal = MargineSchermo)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BottoneIcona(Icons.Rounded.Close, "Chiudi il check") { nav.popBackStack() }
                Avanzamento(passo, Modifier.weight(1f))
            }
            AnimatedContent(
                passo,
                Modifier.weight(1f),
                transitionSpec = {
                    val verso = if (avanti) 1 else -1
                    (fadeIn(tween(240)) + slideInHorizontally(spring(1f, Spring.StiffnessMediumLow)) { verso * it / 5 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(spring(1f, Spring.StiffnessMediumLow)) { -verso * it / 5 })
                },
                label = "domanda",
            ) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 34.dp)) {
                    when (p) {
                        0 -> Domanda("Quanti pasti hai seguito come da piano?", "Su 21 della settimana. Trascina attorno al quadrante.") {
                            Quadrante(pasti, 21, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { pasti = it }
                        }
                        1 -> Domanda("E i pasti liberi?", "Quanti e com'è andata, in due parole.") {
                            Campo(liberi, { liberi = it }, "I tuoi pasti liberi", righeMin = 3)
                        }
                        2 -> Domanda("Quanta energia hai sentito?", "In media, durante la settimana.") {
                            CinqueEllissi(energia, "Poca", "Tanta", "Energia") { energia = it }
                        }
                        3 -> Domanda("Hai fatto movimento?", "Allenamenti o passeggiate della settimana.") {
                            Campo(allenamento, { allenamento = it }, "Allenamenti o passeggiate", righeMin = 3)
                        }
                        4 -> Domanda("Quanto pesi oggi?", "Se non hai la bilancia a portata di mano, salta pure.") {
                            SelettorePeso(peso, conPeso, { peso = it }) { conPeso = !conPeso }
                        }
                        5 -> Domanda("Come è andato il peso?", "I dati della settimana, se li hai segnati.") {
                            Campo(datiPeso, { datiPeso = it }, "Dati del peso della settimana", righeMin = 3)
                        }
                        6 -> Domanda("Come ti vede lo specchio?", "Noti miglioramenti rispetto a prima?") {
                            Scelta(null, listOf(true to "Sì, noto miglioramenti", false to "No, nessun miglioramento"), migliorata) { migliorata = it }
                            Spacer(Modifier.height(26.dp))
                            Campo(percezione, { percezione = it }, "A parole tue", righeMin = 2)
                        }
                        else -> Domanda("Quanto impegno ci hai messo?", "Con sincerità: resta tra te e il Dr. Bo.") {
                            CinqueEllissi(impegno, "Poco", "Tanto", "Impegno") { impegno = it }
                        }
                    }
                }
            }
            val pronto = when (passo) {
                2 -> energia > 0
                7 -> impegno > 0
                else -> true
            }
            Box(Modifier.padding(top = 12.dp, bottom = 18.dp)) {
                if (passo < PASSI - 1) {
                    AzioneBrace("Continua", abilitata = pronto, icona = Icons.AutoMirrored.Rounded.ArrowForward) { vai(passo + 1) }
                } else {
                    AzioneBrace("Invia al Dr. Bo", abilitata = pronto, inCorso = inviando, onClick = ::invia)
                }
            }
        }
    }
    if (inviato) InvioCompiuto { nav.popBackStack() }
    if (bloccato) {
        it.edodoc.app.ui.kit.Conferma(
            "Il Dr. Bo ha già risposto", "Mentre compilavi è arrivato il suo feedback, quindi il check non si può più cambiare. Scrivigli in chat se vuoi aggiungere qualcosa.",
            "Ho capito", onConferma = { nav.popBackStack() }, onAnnulla = { nav.popBackStack() },
        )
    }
}

@Composable
private fun Domanda(titolo: String, sotto: String, contenuto: @Composable () -> Unit) {
    Testo(titolo, Tipo.Display.copy(fontSize = 36.sp, lineHeight = 42.sp))
    Spacer(Modifier.height(12.dp))
    Testo(sotto, colore = colori.inkSecondary)
    Spacer(Modifier.height(36.dp))
    contenuto()
    Spacer(Modifier.height(20.dp))
}

/** Le barre della E del logo: una per domanda. */
@Composable
private fun Avanzamento(passo: Int, modifier: Modifier = Modifier) {
    val c = colori
    Row(
        modifier.semantics { contentDescription = "Domanda ${passo + 1} di $PASSI" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(PASSI) { i ->
            val alto by animateDpAsState(if (i == passo) 6.dp else 3.dp, spring(.6f, 400f), label = "barra")
            Box(Modifier.weight(1f).height(alto).clip(Forme.Capsula).background(if (i <= passo) c.ember else c.ink.copy(alpha = .14f)))
        }
    }
}

/** Il quadrante dei pasti: si trascina lungo il cerchio, con un tick a ogni pasto. */
@Composable
private fun Quadrante(valore: Int, massimo: Int, modifier: Modifier = Modifier, onValore: (Int) -> Unit) {
    val c = colori
    val view = LocalView.current
    val ridotto = LocalMovimentoRidotto.current
    val mostrato by animateFloatAsState(valore.toFloat(), if (ridotto) tween(0) else spring(.8f, 500f), label = "quadrante")
    // Il gesto vive per tutto il trascinamento: legge sempre il valore attuale senza ripartire a ogni tick
    val corrente by rememberUpdatedState(valore)
    fun daPunto(p: Offset, w: Float, h: Float) {
        val a = atan2(p.x - w / 2, -(p.y - h / 2))
        val giro = ((a + 2 * PI) % (2 * PI)) / (2 * PI)
        val nuovo = (giro * (massimo + 1)).toInt().coerceIn(0, massimo)
        if (nuovo != corrente) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onValore(nuovo)
        }
    }
    Box(
        modifier.aspectRatio(1f)
            .semantics(mergeDescendants = true) {
                contentDescription = "Pasti seguiti"
                stateDescription = "$valore su $massimo"
                progressBarRangeInfo = ProgressBarRangeInfo(valore.toFloat(), 0f..massimo.toFloat(), massimo - 1)
                setProgress { onValore(it.roundToInt().coerceIn(0, massimo)); true }
            }
            .pointerInput(Unit) { detectTapGestures { daPunto(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures { cambio, _ -> daPunto(cambio.position, size.width.toFloat(), size.height.toFloat()) } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spessore = 3.dp.toPx()
            val raggio = size.minDimension / 2 - 10.dp.toPx()
            repeat(massimo) { i ->
                val attivo = i < mostrato
                rotate(360f * i / massimo) {
                    drawLine(
                        if (attivo) c.ember else c.ink.copy(alpha = .16f),
                        Offset(center.x, center.y - raggio),
                        Offset(center.x, center.y - raggio + if (attivo) 22.dp.toPx() else 14.dp.toPx()),
                        spessore, StrokeCap.Round,
                    )
                }
            }
            val punta = raggio + 2.dp.toPx()
            rotate(360f * mostrato / massimo) {
                drawCircle(c.ember, 7.dp.toPx(), Offset(center.x, center.y - punta))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$valore", style = Tipo.DisplayXL.copy(fontSize = 92.sp, lineHeight = 96.sp), color = c.ink)
            Sopratitolo("su $massimo")
        }
    }
}

/** Cinque ellissi crescenti, come la O del logo: si riempiono fino a quella scelta. */
@Composable
private fun CinqueEllissi(valore: Int, basso: String, alto: String, nome: String, onValore: (Int) -> Unit) {
    val c = colori
    val conferma = haptic()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        (1..5).forEach { n ->
            val piena = n <= valore
            val scala by animateFloatAsState(if (n == valore) 1.14f else 1f, spring(.45f, 420f), label = "ellisse")
            val riempi by animateFloatAsState(if (piena) 1f else 0f, spring(1f, 300f), label = "riempi")
            val w = 38 + n * 6
            val h = (w * .68f)
            Box(
                Modifier.size(width = w.dp + 4.dp, height = 72.dp)
                    .clickable(interactionSource = null, indication = null) { onValore(n); conferma() }
                    .semantics {
                        contentDescription = "$nome $n su 5"
                        selected = n == valore
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Canvas(Modifier.size(w.dp, h.dp).scale(scala)) {
                    drawOval(c.ember.copy(alpha = riempi))
                    drawOval(if (piena) c.ember else c.ink.copy(alpha = .28f), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Testo(basso, Tipo.Label, c.inkSecondary)
        Testo(alto, Tipo.Label, c.inkSecondary)
    }
}

@Composable
private fun SelettorePeso(peso: Float, attivo: Boolean, onPeso: (Float) -> Unit, onSalta: () -> Unit) {
    val c = colori
    val view = LocalView.current
    fun cambia(d: Float) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onPeso(((peso + d) * 10).roundToInt() / 10f)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(peso).replace('.', ','), style = Tipo.DisplayXL.copy(fontSize = 84.sp, lineHeight = 90.sp), color = if (attivo) c.ink else c.inkTertiary)
            Spacer(Modifier.width(6.dp))
            Testo("kg", Tipo.Headline, c.inkSecondary, Modifier.padding(bottom = 16.dp))
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AzioneQuieta("1 kg", icona = Icons.Rounded.Remove, abilitata = attivo) { cambia(-1f) }
            AzioneQuieta("0,1", icona = Icons.Rounded.Remove, abilitata = attivo) { cambia(-.1f) }
            AzioneQuieta("0,1", icona = Icons.Rounded.Add, abilitata = attivo) { cambia(.1f) }
            AzioneQuieta("1 kg", icona = Icons.Rounded.Add, abilitata = attivo) { cambia(1f) }
        }
        Spacer(Modifier.height(20.dp))
        AzioneQuieta(if (attivo) "Oggi niente bilancia" else "Inserisci il peso", onClick = onSalta)
    }
}

/** La brace si espande a tutto schermo: il check è arrivato. */
@Composable
private fun InvioCompiuto(onFine: () -> Unit) {
    val c = colori
    val ridotto = LocalMovimentoRidotto.current
    val raggio = remember { Animatable(if (ridotto) 1f else 0f) }
    val testo = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        raggio.animateTo(1f, spring(1f, Spring.StiffnessVeryLow))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(if (ridotto) 0 else 380)
        testo.animateTo(1f, tween(420))
    }
    BackHandler(onBack = onFine)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(c.ember, size.maxDimension * 1.1f * raggio.value, Offset(size.width / 2, size.height * .86f))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(MargineSchermo)
                .scale(.98f + .02f * testo.value).then(Modifier.semantics(mergeDescendants = true) {}),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (testo.value > 0f) {
                Testo("Fatto.", Tipo.DisplayXL, c.onEmber.copy(alpha = testo.value))
                Spacer(Modifier.height(12.dp))
                Testo("Il tuo check è arrivato. Il Dr. Bo lo leggerà lunedì e ti risponderà qui.", Tipo.Body, c.onEmber.copy(alpha = .86f * testo.value))
                Spacer(Modifier.height(32.dp))
                Box(
                    Modifier.fillMaxWidth().height(58.dp).clip(Forme.Capsula).background(c.onEmber.copy(alpha = testo.value))
                        .clickable(onClick = onFine),
                    contentAlignment = Alignment.Center,
                ) { Testo("Chiudi", Tipo.Headline.copy(fontSize = Tipo.Body.fontSize), c.ember) }
            }
        }
    }
}
