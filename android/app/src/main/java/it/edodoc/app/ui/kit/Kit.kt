package it.edodoc.app.ui.kit

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.edodoc.app.R
import it.edodoc.app.core.net.ApiException
import it.edodoc.app.ui.theme.Brace
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.LocalMovimentoRidotto
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

val MargineSchermo = 24.dp

/** Spazio sotto al contenuto delle schede: la capsula di navigazione galleggia sopra. */
val LocalSpazioBarra = compositionLocalOf { 0.dp }

fun Modifier.tocco(descrizione: String? = null, ruolo: Role = Role.Button, onClick: () -> Unit): Modifier {
    val conClick = clickable(role = ruolo, onClick = onClick)
    return if (descrizione != null) conClick.semantics { contentDescription = descrizione } else conClick
}

@Composable
fun haptic(): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
}

@Composable
fun Testo(testo: String, stile: TextStyle = Tipo.Body, colore: Color = colori.ink, modifier: Modifier = Modifier, righe: Int = Int.MAX_VALUE, centro: Boolean = false) {
    Text(testo, modifier, style = stile, color = colore, maxLines = righe, overflow = TextOverflow.Ellipsis, textAlign = if (centro) TextAlign.Center else null)
}

@Composable
fun Sopratitolo(testo: String, modifier: Modifier = Modifier, colore: Color = colori.inkSecondary) {
    Text(testo.uppercase(), modifier, style = Tipo.Overline, color = colore, maxLines = 1)
}

@Composable
fun Filetto(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(.75.dp).background(colori.hairline))
}

/** Riga su canvas separata da un filetto: niente card dentro card. */
@Composable
fun Riga(
    titolo: String,
    sotto: String? = null,
    modifier: Modifier = Modifier,
    inizio: (@Composable () -> Unit)? = null,
    fine: (@Composable RowScope.() -> Unit)? = null,
    filetto: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = MargineSchermo, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            inizio?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Testo(titolo, Tipo.Headline.copy(fontSize = Tipo.Body.fontSize), righe = 2)
                if (!sotto.isNullOrBlank()) Testo(sotto, Tipo.Label.copy(fontWeight = Tipo.Body.fontWeight), colori.inkSecondary, righe = 2)
            }
            if (fine != null) fine() else if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colori.inkTertiary)
            }
        }
        if (filetto) Filetto(Modifier.padding(horizontal = MargineSchermo))
    }
}

enum class Tono { Neutro, Brace, Positivo, Attenzione, Critico }

@Composable
fun Tono.colore(): Color = when (this) {
    Tono.Neutro -> colori.inkSecondary
    Tono.Brace -> colori.ember
    Tono.Positivo -> colori.positive
    Tono.Attenzione -> colori.warning
    Tono.Critico -> colori.critical
}

@Composable
fun Pastiglia(testo: String, tono: Tono = Tono.Neutro, modifier: Modifier = Modifier) {
    val c = tono.colore()
    Row(
        modifier.clip(Forme.Capsula).background(c.copy(alpha = .13f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(testo, style = Tipo.Label.copy(fontSize = Tipo.Overline.fontSize), color = c, maxLines = 1)
    }
}

@Composable
fun Conteggio(n: Long, modifier: Modifier = Modifier) {
    if (n <= 0) return
    Box(modifier.size(22.dp).clip(CircleShape).background(colori.ember).semantics { contentDescription = "$n da leggere" }, contentAlignment = Alignment.Center) {
        Text(if (n > 9) "9+" else "$n", style = Tipo.Label.copy(fontSize = Tipo.Overline.fontSize), color = colori.onEmber)
    }
}

/** L'unica azione importante della schermata. */
@Composable
fun AzioneBrace(
    testo: String,
    modifier: Modifier = Modifier,
    abilitata: Boolean = true,
    inCorso: Boolean = false,
    icona: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = colori
    val interazione = remember { MutableInteractionSource() }
    val premuto by interazione.collectIsPressedAsState()
    val scala by animateFloatAsState(if (premuto) .97f else 1f, spring(.6f, 500f), label = "scala")
    val attiva = abilitata && !inCorso
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .graphicsLayer { scaleX = scala; scaleY = scala; alpha = if (abilitata) 1f else .4f }
            .clip(Forme.Capsula)
            .background(Brush.horizontalGradient(listOf(c.ember, c.emberSoft)))
            .clickable(interazione, null, enabled = attiva, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (inCorso) {
            PuntiAttesa(c.onEmber)
        } else {
            icona?.let {
                Icon(it, null, tint = c.onEmber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(testo, style = Tipo.Headline.copy(fontSize = Tipo.Body.fontSize), color = c.onEmber, maxLines = 1)
        }
    }
}

@Composable
fun AzioneQuieta(testo: String, modifier: Modifier = Modifier, icona: ImageVector? = null, tono: Tono = Tono.Neutro, abilitata: Boolean = true, onClick: () -> Unit) {
    val c = colori
    val colore = if (tono == Tono.Neutro) c.ink else tono.colore()
    Row(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (abilitata) 1f else .4f }
            .clip(Forme.Capsula)
            .border(.75.dp, if (tono == Tono.Neutro) c.hairline else colore.copy(alpha = .35f), Forme.Capsula)
            .clickable(enabled = abilitata, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icona?.let {
            Icon(it, null, tint = colore, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(testo, style = Tipo.Label, color = colore, maxLines = 1)
    }
}

@Composable
fun PuntiAttesa(colore: Color, modifier: Modifier = Modifier) {
    val ridotto = LocalMovimentoRidotto.current
    val t = rememberInfiniteTransition(label = "attesa")
    Row(modifier.semantics { contentDescription = "Attendere" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(.25f, 1f, infiniteRepeatable(tween(520, delayMillis = i * 120), RepeatMode.Reverse), label = "punto")
            Box(Modifier.size(7.dp).graphicsLayer { alpha = if (ridotto) .7f else a }.clip(CircleShape).background(colore))
        }
    }
}

@Composable
fun BottoneIcona(icona: ImageVector, descrizione: String, modifier: Modifier = Modifier, vetro: Boolean = true, tinta: Color = colori.ink, onClick: () -> Unit) {
    Box(
        modifier.size(46.dp).then(if (vetro) Modifier.vetro(CircleShape, 16.dp) else Modifier.clip(CircleShape))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = descrizione },
        contentAlignment = Alignment.Center,
    ) { Icon(icona, null, tint = tinta, modifier = Modifier.size(22.dp)) }
}

/**
 * Lo scheletro di ogni schermata interna: brace dietro, contenuto da bordo a bordo, titolo grande
 * nel contenuto e barra con il ritorno in vetro.
 */
@Composable
fun Schermo(
    onIndietro: (() -> Unit)? = null,
    azioni: @Composable RowScope.() -> Unit = {},
    fuoco: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(.8f, .05f),
    forza: Float = .8f,
    sopra: @Composable BoxScope.() -> Unit = {},
    contenuto: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(colori.canvas)) {
        VetroHost(
            Modifier.fillMaxSize(),
            contenuto = {
                Brace(fuoco, forza)
                contenuto()
            },
            sopra = {
                if (onIndietro != null) {
                    BottoneIcona(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp), onClick = onIndietro)
                }
                Row(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = azioni,
                )
                sopra()
            },
        )
    }
}

/** Spazio iniziale per stare sotto a status bar e barra in vetro. */
fun LazyListScope.testata(sopratitolo: String?, titolo: String, sotto: String? = null, conBarra: Boolean = true) {
    item(key = "testata") {
        Column(Modifier.statusBarsPadding().padding(start = MargineSchermo, end = MargineSchermo, top = if (conBarra) 72.dp else 28.dp, bottom = 20.dp)) {
            sopratitolo?.let {
                Sopratitolo(it)
                Spacer(Modifier.height(10.dp))
            }
            Text(titolo, Modifier.semantics { heading() }, style = Tipo.Display, color = colori.ink)
            sotto?.let {
                Spacer(Modifier.height(8.dp))
                Testo(it, colore = colori.inkSecondary)
            }
        }
    }
}

fun LazyListScope.piede() {
    item(key = "piede") {
        val barra = LocalSpazioBarra.current
        Spacer(Modifier.navigationBarsPadding().height(barra + 28.dp))
    }
}

fun LazyListScope.sezione(titolo: String) {
    item {
        Sopratitolo(titolo, Modifier.padding(start = MargineSchermo, end = MargineSchermo, top = 28.dp, bottom = 6.dp).semantics { heading() })
    }
}

@Composable
fun ColonnaForm(modifier: Modifier = Modifier, contenuto: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(start = MargineSchermo, end = MargineSchermo, top = 72.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = contenuto,
    )
}

@Composable
fun ScheletroBlocco(altezza: Dp, modifier: Modifier = Modifier, larghezza: Float = 1f) {
    val ridotto = LocalMovimentoRidotto.current
    val t = rememberInfiniteTransition(label = "scheletro")
    val a by t.animateFloat(.05f, .12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.fillMaxWidth(larghezza).height(altezza).clip(Forme.Controllo).background(colori.ink.copy(alpha = if (ridotto) .08f else a)))
}

@Composable
fun ScheletroLista(righe: Int = 7) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = MargineSchermo, end = MargineSchermo, top = 76.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScheletroBlocco(14.dp, larghezza = .3f)
        ScheletroBlocco(48.dp, larghezza = .75f)
        Spacer(Modifier.height(18.dp))
        repeat(righe) { ScheletroBlocco(58.dp) }
    }
}

@Composable
fun Errore(messaggio: String, offline: Boolean, riprova: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (offline) Icons.Rounded.CloudOff else Icons.Rounded.Refresh, null, tint = colori.ember, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(18.dp))
        Testo(if (offline) "Sei offline" else "Qualcosa è andato storto", Tipo.Title, centro = true)
        Spacer(Modifier.height(8.dp))
        Testo(if (offline) "Controlla la connessione e riprova." else messaggio, colore = colori.inkSecondary, centro = true)
        Spacer(Modifier.height(24.dp))
        AzioneQuieta("Riprova", icona = Icons.Rounded.Refresh, onClick = riprova)
    }
}

@Composable
fun Vuoto(titolo: String, testo: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ellisse(Modifier.size(width = 44.dp, height = 26.dp))
        Spacer(Modifier.height(20.dp))
        Testo(titolo, Tipo.Headline, centro = true)
        Spacer(Modifier.height(6.dp))
        Testo(testo, colore = colori.inkSecondary, centro = true)
    }
}

/** L'ellisse della O del logo. */
@Composable
fun Ellisse(modifier: Modifier = Modifier, piena: Boolean = false, colore: Color = colori.ember) {
    Box(modifier.clip(androidx.compose.foundation.shape.GenericShape { size, _ -> addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) })
        .then(if (piena) Modifier.background(colore) else Modifier.border(1.5.dp, colore, androidx.compose.foundation.shape.GenericShape { size, _ -> addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) })))
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(painterResource(R.drawable.logo_edo), "EdoDoc", modifier)
}

// Moduli

@Composable
fun Campo(
    valore: String,
    onValore: (String) -> Unit,
    etichetta: String,
    modifier: Modifier = Modifier,
    tastiera: KeyboardType = KeyboardType.Text,
    azioneIme: ImeAction = ImeAction.Next,
    onFine: (() -> Unit)? = null,
    password: Boolean = false,
    righeMin: Int = 1,
    aiuto: String? = null,
    capitalizza: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
) {
    val c = colori
    Column(modifier.fillMaxWidth()) {
        Sopratitolo(etichetta)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = valore,
            onValueChange = onValore,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = etichetta },
            textStyle = Tipo.Body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ember),
            minLines = righeMin,
            singleLine = righeMin == 1,
            keyboardOptions = KeyboardOptions(capitalization = capitalizza, keyboardType = tastiera, imeAction = if (righeMin > 1) ImeAction.Default else azioneIme),
            keyboardActions = KeyboardActions(onDone = { onFine?.invoke() }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { interno ->
                Column {
                    Box(Modifier.padding(vertical = 10.dp)) { interno() }
                    Filetto()
                }
            },
        )
        aiuto?.let {
            Spacer(Modifier.height(4.dp))
            Testo(it, Tipo.Label.copy(fontWeight = Tipo.Body.fontWeight), c.inkSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Scelta(etichetta: String?, opzioni: List<Pair<T, String>>, selezionata: T?, modifier: Modifier = Modifier, onScelta: (T) -> Unit) {
    val c = colori
    Column(modifier.fillMaxWidth()) {
        etichetta?.let {
            Sopratitolo(it)
            Spacer(Modifier.height(10.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opzioni.forEach { (valore, testo) ->
                val attiva = valore == selezionata
                Box(
                    Modifier.heightIn(min = 44.dp).clip(Forme.Capsula)
                        .background(if (attiva) c.ember else Color.Transparent)
                        .border(.75.dp, if (attiva) c.ember else c.hairline, Forme.Capsula)
                        .clickable(role = Role.RadioButton) { onScelta(valore) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(testo, style = Tipo.Label, color = if (attiva) c.onEmber else c.ink) }
            }
        }
    }
}

@Composable
fun Interruttore(etichetta: String, attivo: Boolean, sotto: String? = null, modifier: Modifier = Modifier, abilitato: Boolean = true, onCambia: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().graphicsLayer { alpha = if (abilitato) 1f else .55f }
            .clickable(enabled = abilitato, role = Role.Switch) { onCambia(!attivo) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Testo(etichetta)
            sotto?.let { Testo(it, Tipo.Label.copy(fontWeight = Tipo.Body.fontWeight), colori.inkSecondary) }
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(attivo, null, enabled = abilitato, colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = colori.ember, checkedThumbColor = colori.onEmber, uncheckedTrackColor = Color.Transparent,
            uncheckedBorderColor = colori.inkTertiary, uncheckedThumbColor = colori.inkTertiary,
        ))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoData(etichetta: String, valore: LocalDate?, modifier: Modifier = Modifier, opzionale: Boolean = false, onValore: (LocalDate?) -> Unit) {
    var aperto by rememberSaveable { mutableStateOf(false) }
    CampoSelezione(etichetta, valore?.breve() ?: "Scegli", modifier) { aperto = true }
    if (aperto) {
        val stato = rememberDatePickerState(initialSelectedDateMillis = (valore ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { aperto = false },
            confirmButton = {
                TextButton({
                    onValore(stato.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    aperto = false
                }) { Text("Conferma") }
            },
            dismissButton = {
                if (opzionale) TextButton({ onValore(null); aperto = false }) { Text("Togli") }
                else TextButton({ aperto = false }) { Text("Annulla") }
            },
        ) { DatePicker(stato) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoOra(etichetta: String, valore: LocalTime, modifier: Modifier = Modifier, onValore: (LocalTime) -> Unit) {
    var aperto by rememberSaveable { mutableStateOf(false) }
    CampoSelezione(etichetta, "%02d:%02d".format(valore.hour, valore.minute), modifier) { aperto = true }
    if (aperto) {
        val stato = rememberTimePickerState(valore.hour, valore.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { aperto = false },
            confirmButton = { TextButton({ onValore(LocalTime.of(stato.hour, stato.minute)); aperto = false }) { Text("Conferma") } },
            dismissButton = { TextButton({ aperto = false }) { Text("Annulla") } },
            text = { TimePicker(stato) },
        )
    }
}

@Composable
fun CampoSelezione(etichetta: String, valore: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}) {
        Sopratitolo(etichetta)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(vertical = 10.dp)) { Testo(valore, righe = 1) }
        Filetto()
    }
}

@Composable
fun Conferma(titolo: String, testo: String, conferma: String, pericolosa: Boolean = false, onConferma: () -> Unit, onAnnulla: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnulla,
        shape = Forme.Pannello,
        containerColor = colori.canvasElevated,
        title = { Testo(titolo, Tipo.Title) },
        text = { Testo(testo, colore = colori.inkSecondary) },
        confirmButton = { TextButton(onConferma) { Text(conferma, style = Tipo.Label, color = if (pericolosa) colori.critical else colori.ember) } },
        dismissButton = { TextButton(onAnnulla) { Text("Annulla", style = Tipo.Label, color = colori.inkSecondary) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Foglio(onChiudi: () -> Unit, contenuto: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colori.canvasElevated,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(36.dp, 4.dp).clip(Forme.Capsula).background(colori.inkTertiary)) },
    ) {
        // Il foglio è una finestra a parte: gli avvisi dell'app resterebbero nascosti sotto
        LocalAvvisi.current.messaggio?.let {
            Testo(it, Tipo.Label, colori.ember, Modifier.padding(horizontal = MargineSchermo, vertical = 6.dp))
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = MargineSchermo, end = MargineSchermo, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = contenuto,
        )
    }
}

// Avvisi

class Avvisi(private val scope: CoroutineScope) {
    var messaggio by mutableStateOf<String?>(null)
        private set
    private var numero = 0

    fun mostra(testo: String) {
        messaggio = testo
        val mio = ++numero
        scope.launch {
            delay(3200)
            if (mio == numero) messaggio = null
        }
    }
}

val LocalAvvisi = compositionLocalOf<Avvisi> { error("Avvisi non disponibili") }

@Composable
fun AvvisiHost(contenuto: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val avvisi = remember { Avvisi(scope) }
    CompositionLocalProvider(LocalAvvisi provides avvisi) {
        Box(Modifier.fillMaxSize()) {
            contenuto()
            val testo = avvisi.messaggio
            AnimatedVisibility(
                testo != null,
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 62.dp, start = 20.dp, end = 20.dp),
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                var ultimo by remember { mutableStateOf("") }
                LaunchedEffect(testo) { if (testo != null) ultimo = testo }
                Box(Modifier.clip(Forme.Controllo).background(colori.canvasElevated).border(.75.dp, colori.hairline, Forme.Controllo).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Testo(testo ?: ultimo, Tipo.Label)
                }
            }
        }
    }
}

/** Esegue un'azione verso il server: l'errore diventa un avviso, mai un crash. */
@Composable
fun azione(): (suspend () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val avvisi = LocalAvvisi.current
    return { blocco ->
        scope.launch {
            try {
                blocco()
            } catch (e: ApiException) {
                avvisi.mostra(e.message ?: "Si è verificato un errore. Riprova.")
            }
        }
    }
}

/** Divisione semplice dei KPI: numero in Display leggero, etichetta in Overline. */
@Composable
fun Numero(valore: String, etichetta: String, modifier: Modifier = Modifier, colore: Color = colori.ink) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(valore, style = Tipo.Title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light, fontSize = Tipo.Display.fontSize * .68f, fontFeatureSettings = "tnum"), color = colore, maxLines = 1)
        Sopratitolo(etichetta)
    }
}
