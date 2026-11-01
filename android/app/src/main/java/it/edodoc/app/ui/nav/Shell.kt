package it.edodoc.app.ui.nav

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import it.edodoc.app.ui.kit.LocalSpazioBarra
import it.edodoc.app.ui.kit.VetroHost
import it.edodoc.app.ui.kit.vetro
import it.edodoc.app.ui.theme.Forme
import it.edodoc.app.ui.theme.LocalMovimentoRidotto
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori

data class Scheda(val rotta: String, val titolo: String, val icona: ImageVector, val daLeggere: Long = 0)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalCondivisione = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimazione = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Lo stesso elemento tra elenco e dettaglio. Con Riduci movimento diventa una dissolvenza. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.condivisa(chiave: String): Modifier {
    val scope = LocalCondivisione.current
    val animazione = LocalAnimazione.current
    if (scope == null || animazione == null || LocalMovimentoRidotto.current) return this
    return with(scope) { this@condivisa.sharedElement(rememberSharedContentState(chiave), animazione) }
}

private val mollaPos = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntOffset.VisibilityThreshold)

private fun entra(ridotto: Boolean, verso: Int): EnterTransition =
    if (ridotto) fadeIn() else fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally(mollaPos) { verso * it / 8 }

private fun esce(ridotto: Boolean, verso: Int): ExitTransition =
    if (ridotto) fadeOut() else fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + slideOutHorizontally(mollaPos) { verso * it / 8 }

// Il ritorno segue il dito nel predictive back: niente dissolvenze, con due schermate trasparenti il testo si accavallerebbe
private fun riemerge(ridotto: Boolean): EnterTransition =
    if (ridotto) fadeIn() else slideInHorizontally(mollaPos) { -it / 5 }

private fun scivolaVia(ridotto: Boolean): ExitTransition =
    if (ridotto) fadeOut() else slideOutHorizontally(mollaPos) { it }

/** Rotta con argomenti numerici: rotta("corso/{id}") -> it.long("id"). */
fun NavGraphBuilder.rotta(
    percorso: String,
    numeri: List<String> = emptyList(),
    testi: List<String> = emptyList(),
    contenuto: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    val argomenti: List<NamedNavArgument> =
        numeri.map { navArgument(it) { type = NavType.LongType } } + testi.map { navArgument(it) { type = NavType.StringType; nullable = true } }
    composable(percorso, argomenti) {
        CompositionLocalProvider(LocalAnimazione provides this) { contenuto(it) }
    }
}

fun NavBackStackEntry.long(nome: String): Long = arguments?.getLong(nome) ?: 0
fun NavBackStackEntry.testo(nome: String): String? = arguments?.getString(nome)

fun NavHostController.scheda(rotta: String) = navigate(rotta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** L'impalcatura delle due app: rotte, transizioni, predictive back e capsula di navigazione in vetro. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Shell(nav: NavHostController, inizio: String, schede: List<Scheda>, grafo: NavGraphBuilder.() -> Unit) {
    val ridotto = LocalMovimentoRidotto.current
    val corrente by nav.currentBackStackEntryAsState()
    val rottaCorrente = corrente?.destination?.route
    val suScheda = schede.any { it.rotta == rottaCorrente }
    VetroHost(
        Modifier.fillMaxSize(),
        contenuto = {
            CompositionLocalProvider(LocalSpazioBarra provides 88.dp) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalCondivisione provides this) {
                        NavHost(
                            nav, inizio,
                            enterTransition = { entra(ridotto, 1) },
                            exitTransition = { esce(ridotto, -1) },
                            popEnterTransition = { riemerge(ridotto) },
                            popExitTransition = { scivolaVia(ridotto) },
                            builder = grafo,
                        )
                    }
                }
            }
        },
        sopra = {
            AnimatedVisibility(
                suScheda,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                enter = if (ridotto) fadeIn() else slideInVertically(mollaPos) { it * 2 } + fadeIn(),
                exit = if (ridotto) fadeOut() else slideOutVertically(mollaPos) { it * 2 } + fadeOut(),
            ) {
                Barra(schede, rottaCorrente) { nav.scheda(it) }
            }
        },
    )
}

@Composable
private fun Barra(schede: List<Scheda>, attiva: String?, onScheda: (String) -> Unit) {
    val c = colori
    Row(
        Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 10.dp).widthIn(max = 460.dp).fillMaxWidth().height(66.dp)
            .vetro(Forme.Capsula).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        schede.forEach { s ->
            val scelta = s.rotta == attiva
            val peso by animateFloatAsState(if (scelta) 2.1f else 1f, spring(.72f, 340f), label = "peso")
            Box(
                Modifier.weight(peso).fillMaxHeight().clip(Forme.Capsula)
                    .background(if (scelta) c.ember.copy(alpha = if (c.scuro) .18f else .14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(remember = true, role = Role.Tab) { onScheda(s.rotta) }
                    .semantics {
                        selected = scelta
                        contentDescription = s.titolo + if (s.daLeggere > 0) ", ${s.daLeggere} da leggere" else ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Icon(s.icona, null, tint = if (scelta) c.ember else c.inkSecondary, modifier = Modifier.size(23.dp))
                        if (s.daLeggere > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(Forme.Capsula).background(c.ember))
                    }
                    AnimatedVisibility(scelta, enter = expandHorizontally(spring(.8f, 400f)) + fadeIn(), exit = shrinkHorizontally(spring(.8f, 400f)) + fadeOut()) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            Text(s.titolo, style = Tipo.Label, color = c.ember, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.clickable(remember: Boolean, role: Role, onClick: () -> Unit): Modifier =
    this.clickable(MutableInteractionSource(), null, role = role, onClick = onClick)
