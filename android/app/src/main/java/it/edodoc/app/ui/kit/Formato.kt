package it.edodoc.app.ui.kit

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IT = Locale.ITALIAN
private val EURO = NumberFormat.getCurrencyInstance(Locale.ITALY)
private val GIORNO = DateTimeFormatter.ofPattern("EEEE d MMMM", IT)
private val GIORNO_SETTIMANA = DateTimeFormatter.ofPattern("EEEE", IT)
private val GIORNO_BREVE = DateTimeFormatter.ofPattern("d MMM yyyy", IT)
private val GIORNO_CORTO = DateTimeFormatter.ofPattern("d MMM", IT)
private val ORA = DateTimeFormatter.ofPattern("HH:mm", IT)

fun BigDecimal?.euro(): String = this?.let { EURO.format(it) } ?: "—"
fun BigDecimal?.euroTondo(): String = this?.let {
    NumberFormat.getCurrencyInstance(Locale.ITALY).apply { maximumFractionDigits = 0 }.format(it.setScale(0, RoundingMode.HALF_UP))
} ?: "—"

fun BigDecimal?.kg(): String = this?.let { it.stripTrailingZeros().toPlainString().replace('.', ',') + " kg" } ?: "—"

fun LocalDate.lungo(): String = format(GIORNO)
fun LocalDate.breve(): String = format(GIORNO_BREVE)
fun LocalDate.corto(): String = format(GIORNO_CORTO)
fun LocalDateTime.ora(): String = format(ORA)
fun LocalDateTime.conOra(): String = "${toLocalDate().corto()}, ${ora()}"

/** "oggi", "domani", "martedì 3 novembre": il modo in cui lo direbbe una persona. */
fun LocalDate.parlato(oggi: LocalDate = LocalDate.now()): String = when (this) {
    oggi -> "oggi"
    oggi.plusDays(1) -> "domani"
    oggi.minusDays(1) -> "ieri"
    else -> if (this > oggi && this <= oggi.plusDays(6)) format(GIORNO_SETTIMANA) else lungo()
}

fun durata(secondi: Int): String = "%d:%02d".format(secondi / 60, secondi % 60)

fun durataLunga(secondi: Int): String {
    val minuti = secondi / 60
    return if (minuti >= 60) "${minuti / 60} h ${minuti % 60} min" else "$minuti min"
}

fun String.capitalizzato(): String = replaceFirstChar { it.titlecase(IT) }

object Etichette {
    val StatoPaziente = mapOf("LEAD" to "Lead", "LISTA_ATTESA" to "Lista d'attesa", "ATTIVO" to "Attivo", "MANTENIMENTO" to "Mantenimento", "CONCLUSO" to "Concluso")
    val Fonte = mapOf("YOUTUBE" to "YouTube", "INSTAGRAM" to "Instagram", "MASTERCLASS" to "Masterclass", "LIBRO" to "Libro", "PASSAPAROLA" to "Passaparola", "SITO" to "Sito", "ALTRO" to "Altro")
    val TipoPercorso = mapOf("METODO_BUSSOLA" to "Percorso 6 mesi", "RINNOVO" to "Rinnovo 6 mesi", "MANTENIMENTO" to "Mantenimento")
    val Piano = mapOf("UNICO" to "In anticipo", "RATE" to "A rate")
    val TipoVisita = mapOf("CHIAMATA_CONOSCITIVA" to "Chiamata conoscitiva", "PRIMA_VISITA" to "Prima visita", "CONTROLLO" to "Controllo", "MANTENIMENTO" to "Mantenimento")
    val DurataVisita = mapOf("CHIAMATA_CONOSCITIVA" to 30, "PRIMA_VISITA" to 60, "CONTROLLO" to 30, "MANTENIMENTO" to 30)
    val Modalita = mapOf("ONLINE" to "Online", "PRESENZA" to "Dal vivo")
    val StatoVisita = mapOf("PROGRAMMATA" to "Programmata", "EFFETTUATA" to "Effettuata", "ANNULLATA" to "Annullata", "NON_PRESENTATO" to "Non presentato")
    val SyncGoogle = mapOf("DA_SINCRONIZZARE" to "In attesa di Google", "SINCRONIZZATA" to "Su Google Calendar", "ERRORE" to "Errore Google")
    val Metodo = mapOf("BONIFICO" to "Bonifico", "CARTA" to "Carta", "CONTANTI" to "Contanti", "PAYPAL" to "PayPal", "ALTRO" to "Altro")
    val TipoPagamento = mapOf("ACCONTO" to "Acconto", "UNICO" to "In anticipo", "RATA" to "Rata", "MANTENIMENTO" to "Mantenimento", "ALTRO" to "Altro")
    val SituazionePagamenti = mapOf("IN_REGOLA" to "In regola", "DA_PAGARE" to "Rate da pagare", "SCADUTO" to "Pagamento scaduto")
    val AzioneCheck = mapOf("SOLLECITO" to "Sollecitare il check", "MESSAGGIO_DEDICATO" to "Messaggio dedicato", "MESSAGGIO_STANDARD" to "Messaggio motivazionale")
    val Priorita = mapOf("ALTA" to "Alta", "MEDIA" to "Media", "BASSA" to "Bassa")
    val CategorieEntrata = mapOf("YOUTUBE_ADS" to "YouTube Ads", "CONSULENZA" to "Consulenza", "ALTRO_RICAVO" to "Altro ricavo")
    val CategorieUscita = mapOf("IMPOSTE" to "Imposte", "CASSA_PREVIDENZA" to "Cassa previdenza", "ORDINE" to "Ordine", "COMMERCIALISTA" to "Commercialista", "SOFTWARE" to "Software", "ALTRA_SPESA" to "Altra spesa")
    val Categoria = CategorieEntrata + CategorieUscita
    val StatoVideo = mapOf("CARICAMENTO" to "In caricamento", "ELABORAZIONE" to "In elaborazione", "PRONTO" to "Pronto", "ERRORE" to "Errore")
}
