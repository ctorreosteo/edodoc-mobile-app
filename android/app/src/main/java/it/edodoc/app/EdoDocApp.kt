package it.edodoc.app

import android.app.Application

class EdoDocApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Grafo.avvia(this)
        Grafo.push.creaCanali()
    }
}
