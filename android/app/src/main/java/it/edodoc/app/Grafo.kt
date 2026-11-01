package it.edodoc.app

import android.content.Context
import android.content.SharedPreferences
import it.edodoc.app.core.aggiornamento.Aggiornamento
import it.edodoc.app.core.net.ApiClient
import it.edodoc.app.core.net.Api
import it.edodoc.app.core.push.Push
import it.edodoc.app.core.session.KeystoreSessionStore

/** Gli account per Apple e Google vivono sul server demo, che contiene solo dati inventati. */
object Ambiente {
    private val ACCOUNT_PROVA = setOf("prova.dottore@edodoc.it", "prova.paziente@edodoc.it")

    fun isAccountProva(email: String) = email.trim().lowercase() in ACCOUNT_PROVA

    fun url(prova: Boolean) = if (prova && BuildConfig.API_DEMO_URL.isNotEmpty()) BuildConfig.API_DEMO_URL else BuildConfig.API_BASE_URL
}

/** Le dipendenze dell'app: poche, create una volta sola all'avvio. */
object Grafo {
    private const val CHIAVE_AMBIENTE_PROVA = "ambiente_prova"

    private lateinit var preferenze: SharedPreferences
    lateinit var api: ApiClient
        private set
    lateinit var edo: Api
        private set
    lateinit var push: Push
        private set
    lateinit var aggiornamento: Aggiornamento
        private set

    fun avvia(context: Context) {
        preferenze = context.getSharedPreferences("edodoc_ambiente", Context.MODE_PRIVATE)
        api = ApiClient(Ambiente.url(preferenze.getBoolean(CHIAVE_AMBIENTE_PROVA, false)), KeystoreSessionStore(context))
        edo = Api(api)
        push = Push(context.applicationContext, edo)
        aggiornamento = Aggiornamento(context.applicationContext)
    }

    fun scegliAmbiente(prova: Boolean) {
        preferenze.edit().putBoolean(CHIAVE_AMBIENTE_PROVA, prova).apply()
        api.baseUrl = Ambiente.url(prova)
    }

    fun urlCopertina(id: String) = "${api.baseUrl}/media/copertine/$id"
}
