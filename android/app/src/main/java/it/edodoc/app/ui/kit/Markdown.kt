package it.edodoc.app.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.edodoc.app.ui.theme.Tipo
import it.edodoc.app.ui.theme.colori

sealed interface Blocco {
    data class Titolo(val livello: Int, val testo: String) : Blocco
    data class Paragrafo(val testo: String) : Blocco
    data class Punto(val segno: String, val testo: String) : Blocco
    data class Citazione(val testo: String) : Blocco
    data object Separatore : Blocco
}

private val NUMERATO = Regex("""^(\d+)\.\s+(.*)$""")

/** Il sottoinsieme di Markdown usato dalle lezioni di testo del gestionale. */
fun blocchiMarkdown(sorgente: String): List<Blocco> {
    val blocchi = mutableListOf<Blocco>()
    val paragrafo = StringBuilder()
    fun chiudi() {
        if (paragrafo.isNotBlank()) blocchi += Blocco.Paragrafo(paragrafo.toString().trim())
        paragrafo.clear()
    }
    sorgente.lines().forEach { grezza ->
        val riga = grezza.trim()
        val numerato = NUMERATO.find(riga)
        when {
            riga.isEmpty() -> chiudi()
            riga.startsWith("#") -> {
                chiudi()
                val livello = riga.takeWhile { it == '#' }.length
                blocchi += Blocco.Titolo(livello, riga.drop(livello).trim())
            }
            riga == "---" || riga == "***" -> { chiudi(); blocchi += Blocco.Separatore }
            riga.startsWith("- ") || riga.startsWith("* ") -> { chiudi(); blocchi += Blocco.Punto("•", riga.drop(2)) }
            numerato != null -> { chiudi(); blocchi += Blocco.Punto(numerato.groupValues[1] + ".", numerato.groupValues[2]) }
            riga.startsWith(">") -> { chiudi(); blocchi += Blocco.Citazione(riga.drop(1).trim()) }
            else -> paragrafo.append(riga).append(' ')
        }
    }
    chiudi()
    return blocchi
}

private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|_(.+?)_|\[(.+?)]\((https?://[^)\s]+)\)""")

@Composable
private fun inline(testo: String): AnnotatedString {
    val ember = colori.ember
    return buildAnnotatedString {
        var indice = 0
        INLINE.findAll(testo).forEach { m ->
            append(testo.substring(indice, m.range.first))
            val (grassetto, corsivo, corsivo2, etichetta, url) = m.destructured
            when {
                grassetto.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(grassetto) }
                corsivo.isNotEmpty() || corsivo2.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(corsivo + corsivo2) }
                else -> withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = ember, textDecoration = TextDecoration.Underline)))) { append(etichetta) }
            }
            indice = m.range.last + 1
        }
        append(testo.substring(indice))
    }
}

@Composable
fun Markdown(sorgente: String, modifier: Modifier = Modifier) {
    val c = colori
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocchiMarkdown(sorgente).forEach { b ->
            when (b) {
                is Blocco.Titolo -> Text(inline(b.testo), style = if (b.livello <= 1) Tipo.Title else Tipo.Headline, color = c.ink, modifier = Modifier.padding(top = 8.dp))
                is Blocco.Paragrafo -> Text(inline(b.testo), style = Tipo.Body, color = c.ink)
                is Blocco.Punto -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Testo(b.segno, Tipo.Body, c.ember)
                    Text(inline(b.testo), style = Tipo.Body, color = c.ink)
                }
                is Blocco.Citazione -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(c.ember))
                    Text(inline(b.testo), style = Tipo.Body, color = c.inkSecondary)
                }
                Blocco.Separatore -> Filetto()
            }
        }
    }
}
