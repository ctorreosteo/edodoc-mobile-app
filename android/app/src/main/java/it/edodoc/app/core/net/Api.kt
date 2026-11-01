package it.edodoc.app.core.net

import it.edodoc.app.core.net.ApiClient.Companion.VUOTO
import it.edodoc.app.core.net.ApiClient.Companion.json
import it.edodoc.app.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private val NIENTE: KSerializer<Unit?> = Unit.serializer().nullable

/** Ogni endpoint del backend, uno per riga. Il paziente passa da /app, il dottore dal resto. */
class Api(private val c: ApiClient) {
    private suspend inline fun <reified T> g(p: String): T = c.get(p, serialDi())
    private suspend inline fun <reified T> gl(p: String): List<T> = c.get(p, ListSerializer(serialDi<T>()))
    private suspend inline fun <reified T, reified B> po(p: String, b: B): T = c.post(p, json(b, serialDi()), serialDi())
    private suspend inline fun <reified T, reified B> pu(p: String, b: B): T = c.put(p, json(b, serialDi()), serialDi())
    private suspend fun fatto(p: String) { c.post(p, VUOTO, NIENTE) }
    private suspend fun via(p: String) { c.delete(p, NIENTE) }

    // Accesso
    suspend fun login(u: String, p: String): Sessione = c.postPubblico("/auth/login", json(Login(u.trim(), p), Login.serializer()), Sessione.serializer())
    suspend fun impostaPassword(token: String, password: String): Sessione =
        c.postPubblico("/auth/app/imposta-password", json(ImpostaPassword(token, password), ImpostaPassword.serializer()), Sessione.serializer())
    suspend fun registraDispositivo(token: String) { c.post("/auth/dispositivi", json(RegistraDispositivo(token), RegistraDispositivo.serializer()), NIENTE) }
    suspend fun cambiaPassword(attuale: String, nuova: String) { c.post("/auth/cambia-password", json(CambiaPassword(attuale, nuova), CambiaPassword.serializer()), NIENTE) }

    // Paziente
    suspend fun riepilogo(): RiepilogoApp = g("/app/riepilogo")
    suspend fun checkCorrente(): StatoCheck = g("/app/check/corrente")
    suspend fun storicoCheck(): List<CheckDettaglio> = gl("/app/check")
    suspend fun inviaCheck(r: Risposte): CheckDettaglio = po("/app/check", r)
    suspend fun feedbackLetto(id: Long) = fatto("/app/check/$id/feedback-letto")
    suspend fun corsi(): List<AppCorso> = gl("/app/corsi")
    suspend fun corso(id: Long): AppCorsoDettaglio = g("/app/corsi/$id")
    suspend fun lezione(id: Long): AppLezione = g("/app/lezioni/$id")
    suspend fun pagamenti(): AppPagamenti = g("/app/pagamenti")
    suspend fun fatture(): List<AppFattura> = gl("/app/fatture")
    suspend fun percorso(): AppRiepilogoPercorso = g("/app/percorso")
    suspend fun scaricaFattura(id: Long, f: File) = c.scarica("/app/fatture/$id/pdf", f)
    suspend fun scaricaVocale(id: Long, dottore: Boolean, f: File) =
        c.scarica(if (dottore) "/check/$id/feedback/audio" else "/app/check/$id/feedback/audio", f)

    // Chat: stessa forma per i due lati, cambia solo la radice
    private fun radice(pazienteId: Long?) = if (pazienteId == null) "/app/messaggi" else "/chat/$pazienteId/messaggi"
    suspend fun messaggi(pazienteId: Long?, dopoId: Long? = null, primaDiId: Long? = null): List<RigaMessaggio> {
        val q = listOfNotNull(dopoId?.let { "dopoId=$it" }, primaDiId?.let { "primaDiId=$it" }).joinToString("&")
        return gl(radice(pazienteId) + if (q.isEmpty()) "" else "?$q")
    }
    suspend fun scrivi(pazienteId: Long?, testo: String, clientId: String): RigaMessaggio = po(radice(pazienteId), Invio(testo, clientId))
    suspend fun letti(pazienteId: Long?) = fatto(if (pazienteId == null) "/app/messaggi/letti" else "/chat/$pazienteId/letti")
    suspend fun conversazioni(): List<Conversazione> = gl("/chat/conversazioni")

    // Dashboard, check
    suspend fun dashboard(): Dashboard = g("/dashboard")
    suspend fun settimana(data: LocalDate? = null): SettimanaCheck = g("/check/settimana" + (data?.let { "?data=$it" } ?: ""))
    suspend fun checkPaziente(id: Long): List<CheckDettaglio> = gl("/pazienti/$id/check")
    suspend fun inserisciCheck(paziente: Long, data: LocalDate?, r: Risposte): CheckDettaglio = po("/check/paziente/$paziente" + (data?.let { "?data=$it" } ?: ""), r)
    suspend fun checkLetto(id: Long) = fatto("/check/$id/letto")
    suspend fun contatto(b: Contatto) { c.post("/check/contatto", json(b, Contatto.serializer()), NIENTE) }
    suspend fun feedbackTesto(id: Long, testo: String, email: Boolean): Pair<CheckDettaglio?, String?> =
        c.putConMessaggio("/check/$id/feedback", json(FeedbackInvio(testo, email), FeedbackInvio.serializer()), CheckDettaglio.serializer())
    suspend fun feedbackVocale(id: Long, file: File, secondi: Int, testo: String?, email: Boolean): Pair<CheckDettaglio?, String?> {
        val corpo = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "feedback.m4a", file.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("secondi", secondi.toString())
            .addFormDataPart("inviaEmail", email.toString())
        if (!testo.isNullOrBlank()) corpo.addFormDataPart("testo", testo)
        return c.postConMessaggio("/check/$id/feedback/audio", corpo.build(), CheckDettaglio.serializer())
    }

    // Pazienti
    suspend fun pazienti(): List<PazienteRiga> = gl("/pazienti")
    suspend fun paziente(id: Long): PazienteRiga = g("/pazienti/$id")
    suspend fun creaPaziente(b: PazienteSalva): PazienteRiga = po("/pazienti", b)
    suspend fun aggiornaPaziente(id: Long, b: PazienteSalva): PazienteRiga = pu("/pazienti/$id", b)
    suspend fun archiviaPaziente(id: Long) = via("/pazienti/$id")
    suspend fun invito(id: Long): InvitoApp = c.post("/pazienti/$id/invito-app", VUOTO, InvitoApp.serializer())
    suspend fun percorsiPaziente(id: Long): List<PercorsoSintesi> = gl("/pazienti/$id/percorsi")
    suspend fun visitePaziente(id: Long): List<VisitaRiga> = gl("/pazienti/$id/visite")
    suspend fun pagamentiPaziente(id: Long): PagamentiLista = g("/pazienti/$id/pagamenti")
    suspend fun fatturePaziente(id: Long): List<FatturaRiga> = gl("/pazienti/$id/fatture")
    suspend fun taskPaziente(id: Long): List<TaskRiga> = gl("/pazienti/$id/task")

    // Visite
    suspend fun visite(da: LocalDate, a: LocalDate): List<VisitaRiga> = gl("/visite?da=$da&a=$a")
    suspend fun creaVisita(b: VisitaSalva): VisitaRiga = po("/visite", b)
    suspend fun aggiornaVisita(id: Long, b: VisitaSalva): VisitaRiga = pu("/visite/$id", b)
    suspend fun statoVisita(id: Long, stato: String): VisitaRiga = po("/visite/$id/stato", CambiaStato(stato))

    // Percorsi
    suspend fun acquisti(): Acquisti = g("/percorsi")
    suspend fun listino(): Listino = g("/percorsi/listino")
    suspend fun creaPercorso(b: PercorsoCrea): PercorsoSintesi = po("/percorsi", b)
    suspend fun annullaPercorso(id: Long) = via("/percorsi/$id")

    // Pagamenti
    suspend fun pagamentiPeriodo(da: LocalDate, a: LocalDate): PagamentiLista = g("/pagamenti?da=$da&a=$a")
    suspend fun creaPagamento(b: PagamentoSalva): PagamentoRiga = po("/pagamenti", b)
    suspend fun aggiornaPagamento(id: Long, b: PagamentoSalva): PagamentoRiga = pu("/pagamenti/$id", b)
    suspend fun registraPagamento(id: Long, b: PagamentoRegistra): PagamentoRiga = po("/pagamenti/$id/registra", b)
    suspend fun annullaRegistrazione(id: Long): PagamentoRiga = c.post("/pagamenti/$id/annulla-registrazione", VUOTO, PagamentoRiga.serializer())
    suspend fun annullaPagamento(id: Long) = via("/pagamenti/$id")

    // Fatture
    suspend fun fatturePeriodo(da: LocalDate, a: LocalDate): List<FatturaRiga> = gl("/fatture?da=$da&a=$a")
    suspend fun anteprimaFattura(b: FatturaEmetti): FatturaAnteprima = po("/fatture/anteprima", b)
    suspend fun emettiFattura(b: FatturaEmetti): FatturaRiga = po("/fatture", b)
    suspend fun annullaFattura(id: Long, motivo: String): FatturaRiga = po("/fatture/$id/annulla", FatturaAnnulla(motivo))
    suspend fun scaricaFatturaDottore(id: Long, f: File) = c.scarica("/fatture/$id/pdf", f)
    suspend fun tessera(mese: YearMonth): List<RigaTessera> = gl("/fatture/tessera-sanitaria?mese=$mese")
    suspend fun tesseraInserita(id: Long, inserita: Boolean) = fatto("/fatture/$id/tessera-sanitaria/inserita?inserita=$inserita")
    suspend fun tesseraTrasmetti(id: Long) = fatto("/fatture/$id/tessera-sanitaria/trasmetti")

    // Email
    suspend fun bozzaEmail(b: EmailRichiesta): EmailBozza = po("/email/bozza", b)
    suspend fun inviaEmail(b: EmailInvia) { c.post("/email/invia", json(b, EmailInvia.serializer()), NIENTE) }

    // Corsi
    suspend fun catalogo(): List<RigaCorso> = gl("/corsi")
    suspend fun struttura(id: Long): Struttura = g("/corsi/$id")
    suspend fun creaCorso(b: SalvaCorso): Struttura = po("/corsi", b)
    suspend fun aggiornaCorso(id: Long, b: SalvaCorso): Struttura = pu("/corsi/$id", b)
    suspend fun eliminaCorso(id: Long) = via("/corsi/$id")
    suspend fun pubblicaCorso(id: Long, si: Boolean): Struttura = c.post("/corsi/$id/pubblicazione?pubblicato=$si", VUOTO, Struttura.serializer())
    private fun immagine(byte: ByteArray, tipo: String): RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("file", "copertina", byte.toRequestBody(tipo.toMediaType())).build()
    suspend fun copertinaCorso(id: Long, byte: ByteArray, tipo: String) { c.post("/corsi/$id/copertina", immagine(byte, tipo), NIENTE) }
    suspend fun togliCopertinaCorso(id: Long) = via("/corsi/$id/copertina")
    suspend fun copertinaModulo(id: Long, byte: ByteArray, tipo: String) { c.post("/moduli/$id/copertina", immagine(byte, tipo), NIENTE) }
    suspend fun togliCopertinaModulo(id: Long) = via("/moduli/$id/copertina")
    suspend fun creaModulo(corso: Long, b: SalvaCorso): RigaModulo = po("/corsi/$corso/moduli", b)
    suspend fun aggiornaModulo(id: Long, b: SalvaCorso): RigaModulo = pu("/moduli/$id", b)
    suspend fun eliminaModulo(id: Long) = via("/moduli/$id")
    suspend fun ordineModuli(corso: Long, ids: List<Long>) { c.put("/corsi/$corso/moduli/ordine", json(Ordine(ids), Ordine.serializer()), NIENTE) }
    suspend fun creaLezione(modulo: Long, b: SalvaLezione): RigaLezione = po("/moduli/$modulo/lezioni", b)
    suspend fun aggiornaLezione(id: Long, b: SalvaLezione): RigaLezione = pu("/lezioni/$id", b)
    suspend fun eliminaLezione(id: Long) = via("/lezioni/$id")
    suspend fun ordineLezioni(modulo: Long, ids: List<Long>) { c.put("/moduli/$modulo/lezioni/ordine", json(Ordine(ids), Ordine.serializer()), NIENTE) }
    suspend fun pubblicaLezione(id: Long, si: Boolean): RigaLezione = c.post("/lezioni/$id/pubblicazione?pubblicata=$si", VUOTO, RigaLezione.serializer())
    suspend fun avviaUploadVideo(id: Long, byte: Long): UploadVideo = po("/lezioni/$id/video/upload", AvviaUpload(byte))
    suspend fun sincronizzaVideo(id: Long): StatoVideo = c.post("/lezioni/$id/video/sincronizza", VUOTO, StatoVideo.serializer())
    suspend fun riproduzione(id: Long): Riproduzione = g("/lezioni/$id/video/riproduzione")

    // Task
    suspend fun task(completati: Boolean): List<TaskRiga> = gl("/task?completati=$completati")
    suspend fun tipiTask(): List<TaskTipo> = gl("/task/tipi")
    suspend fun creaTask(b: TaskSalva): TaskRiga = po("/task", b)
    suspend fun aggiornaTask(id: Long, b: TaskSalva): TaskRiga = pu("/task/$id", b)
    suspend fun statoTask(id: Long, fatto: Boolean): TaskRiga = c.post("/task/$id/stato?fatto=$fatto", VUOTO, TaskRiga.serializer())
    suspend fun eliminaTask(id: Long) = via("/task/$id")

    // Finance
    suspend fun finance(anno: Int): FinanceRiepilogo = g("/finance/riepilogo?anno=$anno")
    suspend fun movimenti(anno: Int): List<Movimento> = gl("/finance/movimenti?anno=$anno")
    suspend fun creaMovimento(b: MovimentoSalva): Movimento = po("/finance/movimenti", b)
    suspend fun aggiornaMovimento(id: Long, b: MovimentoSalva): Movimento = pu("/finance/movimenti/$id", b)
    suspend fun eliminaMovimento(id: Long) = via("/finance/movimenti/$id")
    suspend fun scadenze(anno: Int): List<Scadenza> = gl("/finance/scadenze?anno=$anno")
    suspend fun creaScadenza(b: ScadenzaSalva): Scadenza = po("/finance/scadenze", b)
    suspend fun aggiornaScadenza(id: Long, b: ScadenzaSalva): Scadenza = pu("/finance/scadenze/$id", b)
    suspend fun pagaScadenza(id: Long, b: ScadenzaPaga): Scadenza = po("/finance/scadenze/$id/paga", b)
    suspend fun eliminaScadenza(id: Long) = via("/finance/scadenze/$id")

    // Impostazioni
    suspend fun impostazioni(): Panoramica = g("/impostazioni")
    suspend fun aggiornaImpostazione(chiave: String, valore: String?): Voce = pu("/impostazioni", Aggiorna(chiave, valore))
}
