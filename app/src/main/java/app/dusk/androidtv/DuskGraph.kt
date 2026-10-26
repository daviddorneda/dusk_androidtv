package app.dusk.androidtv

import android.content.Context
import app.dusk.androidtv.data.plex.PlexClient
import app.dusk.androidtv.data.plex.SessionStore

/** Servicios compartidos de larga vida (equivalente a lo que `DuskApp` inyecta en el entorno). */
object DuskGraph {
    lateinit var session: SessionStore
        private set
    lateinit var plex: PlexClient
        private set

    fun init(context: Context) {
        if (::session.isInitialized) return
        val app = context.applicationContext
        session = SessionStore(app)
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "0.2.0"
        plex = PlexClient(session.clientIdentifier, version)
    }
}
