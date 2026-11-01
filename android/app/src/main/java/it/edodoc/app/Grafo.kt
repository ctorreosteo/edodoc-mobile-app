package it.edodoc.app

import android.content.Context
import it.edodoc.app.core.net.ApiClient
import it.edodoc.app.core.net.Api
import it.edodoc.app.core.push.Push
import it.edodoc.app.core.session.KeystoreSessionStore

/** Le dipendenze dell'app: poche, create una volta sola all'avvio. */
object Grafo {
    lateinit var api: ApiClient
        private set
    lateinit var edo: Api
        private set
    lateinit var push: Push
        private set

    fun avvia(context: Context) {
        api = ApiClient(BuildConfig.API_BASE_URL, KeystoreSessionStore(context))
        edo = Api(api)
        push = Push(context.applicationContext, edo)
    }

    fun urlCopertina(id: String) = "${BuildConfig.API_BASE_URL}/media/copertine/$id"
}
