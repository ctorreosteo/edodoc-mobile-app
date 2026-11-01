@file:UseSerializers(DataSerializer::class, DataOraSerializer::class, ImportoSerializer::class)

package it.edodoc.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// Specchio dei DTO di edodoc-backend. Gli enum restano stringhe: un valore nuovo sul server non deve far cadere un'app già installata.

@Serializable data class Risposta<T>(val success: Boolean = false, val message: String? = null, val data: T? = null, val error: String? = null)

// Accesso
@Serializable data class Login(val username: String, val password: String, val mobile: Boolean = true)
@Serializable data class Utente(val id: Long, val username: String, val nome: String? = null, val cognome: String? = null, val ruolo: String, val pazienteId: Long? = null) {
    val isDottore get() = ruolo == "ADMIN"
}
@Serializable data class Sessione(val token: String, val refreshToken: String? = null, val utente: Utente)
@Serializable data class Rinnova(val refreshToken: String)
@Serializable data class Logout(val refreshToken: String, val pushToken: String? = null)
@Serializable data class RegistraDispositivo(val pushToken: String, val piattaforma: String = "ANDROID")
@Serializable data class CambiaPassword(val attuale: String, val nuova: String)
@Serializable data class ImpostaPassword(val token: String, val password: String)

// Check
@Serializable data class Risposte(
    val pastiOttimali: Int, val pastiLiberi: String? = null, val energia: Int, val allenamento: String? = null,
    val pesoKg: BigDecimal? = null, val andamentoPeso: String? = null, val percezione: String? = null,
    val percezioneMigliorata: Boolean? = null, val impegno: Int,
)
@Serializable data class CheckDettaglio(
    val id: Long, val pazienteId: Long, val pazienteNome: String, val settimana: LocalDate, val compilatoIl: LocalDateTime? = null,
    val fonte: String? = null, val pastiOttimali: Int? = null, val pastiLiberi: String? = null, val energia: Int? = null,
    val allenamento: String? = null, val pesoKg: BigDecimal? = null, val andamentoPeso: String? = null, val percezione: String? = null,
    val percezioneMigliorata: Boolean? = null, val impegno: Int? = null, val pesoFermo: Boolean = false, val percezioneNegativa: Boolean = false,
    val energiaBassa: Boolean = false, val lettoIl: LocalDateTime? = null, val feedback: String? = null, val feedbackIl: LocalDateTime? = null,
    val feedbackLettoIl: LocalDateTime? = null, val feedbackAudioId: String? = null, val feedbackAudioSecondi: Int? = null,
    val feedbackEmailIl: LocalDateTime? = null,
) {
    val haFeedback get() = feedbackIl != null
    val daLeggere get() = haFeedback && feedbackLettoIl == null
    val haSegnali get() = pesoFermo || percezioneNegativa || energiaBassa
}
@Serializable data class StatoCheck(val settimana: LocalDate, val compilato: Boolean, val check: CheckDettaglio? = null)
@Serializable data class FeedbackInvio(val testo: String, val inviaEmail: Boolean? = null)
@Serializable data class RigaSettimana(
    val pazienteId: Long, val pazienteNome: String, val cellulare: String? = null, val statoPaziente: String? = null, val situazione: String? = null,
    val nota: String? = null, val azione: String? = null, val check: CheckDettaglio? = null, val lunediFattoIl: LocalDateTime? = null,
    val mercolediFattoIl: LocalDateTime? = null,
)
@Serializable data class SettimanaCheck(
    val settimana: LocalDate, val attesi: Int, val compilati: Int, val conSegnali: Int, val daContattareLunedi: Int,
    val daContattareMercoledi: Int, val righe: List<RigaSettimana>,
)
@Serializable data class Contatto(val pazienteId: Long, val settimana: LocalDate, val giorno: String, val fatto: Boolean)

// Chat
@Serializable data class Invio(val testo: String, val clientId: String? = null)
@Serializable data class RigaMessaggio(
    val id: Long, val mittente: String, val testo: String, val clientId: String? = null, val inviatoIl: LocalDateTime, val lettoIl: LocalDateTime? = null,
)
@Serializable data class Conversazione(val pazienteId: Long, val pazienteNome: String, val ultimoMessaggio: RigaMessaggio? = null, val nonLetti: Long = 0)
@Serializable data class RiepilogoApp(val settimana: LocalDate, val checkCompilato: Boolean, val feedbackDaLeggere: Long, val messaggiDaLeggere: Long)

// Percorso e visite
@Serializable data class AppPercorso(
    val id: Long, val tipo: String, val dataInizio: LocalDate? = null, val dataFine: LocalDate? = null, val visiteTotali: Int,
    val visiteFissate: Int, val visiteEffettuate: Int, val stato: String,
)
@Serializable data class AppVisita(
    val id: Long, val numeroVisita: Int? = null, val visiteTotali: Int? = null, val tipo: String, val modalita: String, val stato: String,
    val inizio: LocalDateTime, val fine: LocalDateTime? = null, val meetUrl: String? = null,
)
@Serializable data class AppRiepilogoPercorso(val percorso: AppPercorso? = null, val visite: List<AppVisita> = emptyList())
@Serializable data class VisitaSalva(
    val pazienteId: Long, val tipo: String, val modalita: String, val inizio: LocalDateTime, val durataMinuti: Int? = null,
    val conMeet: Boolean? = null, val invitaPaziente: Boolean? = null, val note: String? = null,
)
@Serializable data class CambiaStato(val stato: String)
@Serializable data class VisitaRiga(
    val id: Long, val pazienteId: Long, val pazienteNome: String, val pazienteEmail: String? = null, val pazienteCellulare: String? = null,
    val percorsoId: Long? = null, val percorsoTipo: String? = null, val numeroVisita: Int? = null, val visiteTotali: Int? = null,
    val tipo: String, val modalita: String, val stato: String, val inizio: LocalDateTime, val fine: LocalDateTime? = null, val conMeet: Boolean = false,
    val meetUrl: String? = null, val invitaPaziente: Boolean = false, val note: String? = null, val titoloCalendario: String? = null,
    val googleSyncStato: String? = null, val googleSyncErrore: String? = null,
)
@Serializable data class PercorsoCrea(val pazienteId: Long, val tipo: String, val pianoPagamento: String, val dataInizio: LocalDate, val note: String? = null)
@Serializable data class PercorsoSintesi(
    val id: Long, val pazienteId: Long, val tipo: String, val pianoPagamento: String? = null, val dataInizio: LocalDate? = null, val dataFine: LocalDate? = null,
    val visiteTotali: Int, val visiteFissate: Int, val visiteEffettuate: Int, val importoTotale: BigDecimal? = null, val stato: String, val note: String? = null,
)
@Serializable data class Acquisto(
    val percorso: PercorsoSintesi, val pazienteNome: String, val pagato: BigDecimal, val daPagare: BigDecimal, val scaduto: BigDecimal,
    val prossimaScadenza: LocalDate? = null, val pagamenti: List<PagamentoRiga> = emptyList(),
)
@Serializable data class Acquisti(val percorsi: List<Acquisto>, val fuoriPercorso: List<PagamentoRiga>)
@Serializable data class Listino(val unico: BigDecimal, val rataPercorso: BigDecimal, val acconto: BigDecimal, val mantenimento: BigDecimal, val rataMantenimento: BigDecimal)

// Pagamenti
@Serializable data class PagamentoSalva(val pazienteId: Long, val tipo: String? = null, val descrizione: String, val importo: BigDecimal, val scadenza: LocalDate, val note: String? = null)
@Serializable data class PagamentoRegistra(val dataPagamento: LocalDate, val metodo: String, val contabile: Boolean? = null, val note: String? = null)
@Serializable data class PagamentoRiga(
    val id: Long, val pazienteId: Long, val pazienteNome: String, val percorsoId: Long? = null, val percorsoTipo: String? = null, val tipo: String? = null,
    val descrizione: String, val importo: BigDecimal, val scadenza: LocalDate, val stato: String, val scaduto: Boolean = false, val dataPagamento: LocalDate? = null,
    val metodo: String? = null, val contabile: Boolean = false, val note: String? = null, val fatturato: Boolean = false,
)
@Serializable data class RiepilogoPagamenti(val pagato: BigDecimal, val daPagare: BigDecimal, val scaduto: BigDecimal)
@Serializable data class PagamentiLista(val riepilogo: RiepilogoPagamenti, val righe: List<PagamentoRiga>)
@Serializable data class AppPagamento(
    val id: Long, val descrizione: String, val tipo: String? = null, val importo: BigDecimal, val scadenza: LocalDate, val stato: String, val scaduto: Boolean = false,
    val dataPagamento: LocalDate? = null, val metodo: String? = null, val fatturaId: Long? = null,
)
@Serializable data class AppPagamenti(val riepilogo: RiepilogoPagamenti, val righe: List<AppPagamento>)

// Fatture
@Serializable data class AppFattura(val id: Long, val numero: Int, val dataEmissione: LocalDate, val descrizione: String? = null, val importoTotale: BigDecimal, val nomeFile: String? = null)
@Serializable data class FatturaEmetti(
    val pagamentoId: Long? = null, val pazienteId: Long? = null, val importo: BigDecimal? = null, val data: LocalDate? = null,
    val descrizione: String? = null, val tsOpposizione: Boolean? = null,
)
@Serializable data class FatturaAnteprima(
    val numero: Int, val data: LocalDate, val intestatario: String? = null, val indirizzo: String? = null, val codiceFiscale: String? = null,
    val descrizione: String? = null, val imponibile: BigDecimal, val contributo: BigDecimal, val bollo: BigDecimal, val importoTotale: BigDecimal,
)
@Serializable data class FatturaAnnulla(val motivo: String)
@Serializable data class FatturaRiga(
    val id: Long, val numero: Int, val dataEmissione: LocalDate, val stato: String, val pazienteId: Long, val pagamentoId: Long? = null, val intestatario: String? = null,
    val indirizzo: String? = null, val codiceFiscale: String? = null, val descrizione: String? = null, val imponibile: BigDecimal, val contributo: BigDecimal, val bollo: BigDecimal,
    val importoTotale: BigDecimal, val motivoAnnullamento: String? = null, val emailPaziente: String? = null, val emailDestinatario: String? = null,
    val emailInviataIl: LocalDateTime? = null, val tsStato: String? = null, val tsInseritaIl: LocalDateTime? = null, val tsOpposizione: Boolean = false, val nomeFile: String? = null,
)
@Serializable data class RigaTessera(val fatturaId: Long, val numero: Int, val data: LocalDate, val codiceFiscale: String? = null, val importo: BigDecimal)

// Email
@Serializable data class EmailRichiesta(val tipo: String, val pazienteId: Long? = null, val pagamentoId: Long? = null, val fatturaId: Long? = null)
@Serializable data class EmailBozza(
    val tipo: String, val pazienteId: Long? = null, val pazienteNome: String? = null, val pagamentoId: Long? = null, val fatturaId: Long? = null, val mittente: String? = null,
    val destinatario: String? = null, val oggetto: String? = null, val testo: String? = null, val allegato: String? = null, val invioAttivo: Boolean = false,
)
@Serializable data class EmailInvia(
    val tipo: String, val pazienteId: Long? = null, val pagamentoId: Long? = null, val fatturaId: Long? = null, val destinatario: String, val oggetto: String, val corpo: String,
)

// Corsi
@Serializable data class AppCorso(val id: Long, val titolo: String, val descrizione: String? = null, val copertinaId: String? = null, val lezioni: Int, val secondiVideo: Int)
@Serializable data class AppLezioneRiga(val id: Long, val titolo: String, val descrizione: String? = null, val tipo: String, val durataSecondi: Int? = null)
@Serializable data class AppModulo(val id: Long, val titolo: String, val descrizione: String? = null, val copertinaId: String? = null, val lezioni: List<AppLezioneRiga>)
@Serializable data class AppCorsoDettaglio(val corso: AppCorso, val moduli: List<AppModulo>)
@Serializable data class Riproduzione(val iframeUrl: String? = null, val hlsUrl: String? = null)
@Serializable data class AppLezione(
    val id: Long, val titolo: String, val descrizione: String? = null, val tipo: String, val durataSecondi: Int? = null, val contenuto: String? = null, val riproduzione: Riproduzione? = null,
)
@Serializable data class SalvaCorso(val titolo: String, val descrizione: String? = null)
@Serializable data class SalvaLezione(val tipo: String, val titolo: String, val descrizione: String? = null, val contenuto: String? = null)
@Serializable data class AvviaUpload(val dimensioneBytes: Long)
@Serializable data class UploadVideo(val uploadUrl: String)
@Serializable data class RigaCorso(
    val id: Long, val titolo: String, val descrizione: String? = null, val copertinaId: String? = null, val pubblicato: Boolean, val moduli: Long, val lezioni: Long,
    val lezioniPubblicate: Long, val secondiVideo: Long,
)
@Serializable data class RigaModulo(val id: Long, val titolo: String, val descrizione: String? = null, val copertinaId: String? = null, val ordine: Int)
@Serializable data class RigaLezione(
    val id: Long, val moduloId: Long, val titolo: String, val descrizione: String? = null, val tipo: String, val ordine: Int, val pubblicata: Boolean, val completa: Boolean,
    val contenuto: String? = null, val videoStato: String? = null, val videoErrore: String? = null, val durataSecondi: Int? = null,
)
@Serializable data class ModuloConLezioni(val modulo: RigaModulo, val lezioni: List<RigaLezione>)
@Serializable data class Struttura(val corso: RigaCorso, val moduli: List<ModuloConLezioni>)
@Serializable data class StatoVideo(val stato: String? = null, val avanzamento: Int? = null, val lezione: RigaLezione? = null)
@Serializable data class Ordine(val ids: List<Long>)

// Pazienti
@Serializable data class PazienteSalva(
    val nome: String, val cognome: String, val email: String? = null, val cellulare: String? = null, val codiceFiscale: String? = null, val dataNascita: LocalDate? = null,
    val indirizzo: String? = null, val cap: String? = null, val citta: String? = null, val provincia: String? = null, val stato: String? = null, val fonte: String? = null,
    val situazione: String? = null, val nota: String? = null, val pesoInizialeKg: BigDecimal? = null, val pesoObiettivoKg: BigDecimal? = null,
)
@Serializable data class PazienteRiga(
    val id: Long, val nome: String, val cognome: String, val email: String? = null, val cellulare: String? = null, val codiceFiscale: String? = null, val dataNascita: LocalDate? = null,
    val indirizzo: String? = null, val cap: String? = null, val citta: String? = null, val provincia: String? = null, val stato: String? = null, val fonte: String? = null,
    val situazione: String? = null, val nota: String? = null, val pesoInizialeKg: BigDecimal? = null, val pesoObiettivoKg: BigDecimal? = null, val createdAt: LocalDateTime? = null,
    val percorso: PercorsoSintesi? = null, val prossimaVisita: LocalDateTime? = null, val situazionePagamenti: String? = null, val residuo: BigDecimal? = null,
    val checkSettimana: Boolean = false, val accessoApp: Boolean = false,
) {
    val nomeCompleto get() = "$nome $cognome"
}
@Serializable data class InvitoApp(val link: String, val emailInviata: Boolean)

// Task
@Serializable data class TaskSalva(val tipoCodice: String, val titolo: String? = null, val descrizione: String? = null, val scadenza: LocalDate? = null, val priorita: String? = null, val pazienteId: Long? = null)
@Serializable data class TaskTipo(val id: Long, val codice: String, val etichetta: String, val titoloDefault: String? = null, val giorniDefault: Int = 0, val priorita: String? = null, val icona: String? = null)
@Serializable data class TaskRiga(
    val id: Long, val titolo: String, val descrizione: String? = null, val tipo: TaskTipo? = null, val stato: String, val priorita: String? = null, val scadenza: LocalDate? = null,
    val pazienteId: Long? = null, val pazienteNome: String? = null, val automatico: Boolean = false, val completatoIl: LocalDateTime? = null, val inRitardo: Boolean = false,
)

// Finance
@Serializable data class FinanceMese(
    val mese: Int, val pazienti: BigDecimal, val youtube: BigDecimal, val altro: BigDecimal, val lordo: BigDecimal, val impostaSostitutiva: BigDecimal,
    val contributoInFattura: BigDecimal, val cassaPrevidenziale: BigDecimal, val netto: BigDecimal, val uscite: BigDecimal,
)
@Serializable data class FinanceLivello(val nome: String, val descrizione: String? = null, val totale: BigDecimal, val margineObiettivo: BigDecimal, val margineSoglia: BigDecimal)
@Serializable data class FinanceRiepilogo(
    val anno: Int, val mesi: List<FinanceMese>, val totali: FinanceMese, val livelli: List<FinanceLivello>, val soglia: BigDecimal, val obiettivo: BigDecimal,
    val valoreNuovoCliente: BigDecimal, val candidatureAperte: Boolean, val saldoCassa: BigDecimal,
)
@Serializable data class MovimentoSalva(val data: LocalDate, val tipo: String, val categoria: String, val descrizione: String, val importo: BigDecimal, val note: String? = null)
@Serializable data class Movimento(val id: Long, val data: LocalDate, val tipo: String, val categoria: String, val descrizione: String, val importo: BigDecimal, val note: String? = null)
@Serializable data class ScadenzaSalva(val titolo: String, val categoria: String, val dataScadenza: LocalDate, val importoStimato: BigDecimal? = null, val note: String? = null)
@Serializable data class ScadenzaPaga(val data: LocalDate, val importo: BigDecimal)
@Serializable data class Scadenza(
    val id: Long, val titolo: String, val categoria: String, val dataScadenza: LocalDate, val importoStimato: BigDecimal? = null, val stato: String, val pagataIl: LocalDate? = null, val note: String? = null,
)

// Dashboard
@Serializable data class Rinnovo(val pazienteId: Long, val pazienteNome: String, val percorsoId: Long, val visiteFissate: Int, val visiteTotali: Int, val dataFine: LocalDate? = null)
@Serializable data class IncassoMese(val mese: Int, val importo: BigDecimal)
@Serializable data class AndamentoCheck(val settimana: LocalDate, val compilati: Int, val segnali: Int, val energiaMedia: Double? = null, val impegnoMedio: Double? = null)
@Serializable data class Dashboard(
    val pazientiAttivi: Long, val pazientiMantenimento: Long, val lead: Long, val listaAttesa: Long, val settimana: LocalDate, val checkAttesi: Int, val checkCompilati: Int,
    val checkConSegnali: Int, val daContattareOggi: Int, val visiteSettimana: Long, val visiteOggi: List<VisitaRiga>, val taskAperti: Long, val taskInRitardo: Long,
    val taskUrgenti: List<TaskRiga>, val incassatoMese: BigDecimal, val incassatoAnno: BigDecimal, val daIncassare: BigDecimal, val scaduto: BigDecimal, val margineSoglia: BigDecimal,
    val candidatureAperte: Boolean, val rinnovi: List<Rinnovo>, val incassiMensili: List<IncassoMese>, val andamentoCheck: List<AndamentoCheck>, val prossimeScadenze: List<Scadenza>,
)

// Impostazioni
@Serializable data class Voce(val chiave: String, val valore: String? = null, val descrizione: String? = null)
@Serializable data class Aggiorna(val chiave: String, val valore: String?)
@Serializable data class Integrazione(val codice: String, val nome: String, val attiva: Boolean, val configurata: Boolean, val nota: String? = null)
@Serializable data class Panoramica(val voci: List<Voce>, val integrazioni: List<Integrazione>)
