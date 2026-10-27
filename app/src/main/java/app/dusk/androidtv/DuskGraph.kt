package app.dusk.androidtv

import android.content.Context
import app.dusk.androidtv.data.plex.ContentRepository
import app.dusk.androidtv.data.plex.HomeSessionManager
import app.dusk.androidtv.data.plex.PlexClient
import app.dusk.androidtv.data.plex.ServerApi
import app.dusk.androidtv.data.plex.ServerManager
import app.dusk.androidtv.data.plex.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Servicios compartidos de larga vida (equivalente a lo que `DuskApp` inyecta en el entorno). */
object DuskGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var session: SessionStore
        private set
    lateinit var plex: PlexClient
        private set
    lateinit var home: HomeSessionManager
        private set
    lateinit var servers: ServerManager
        private set
    lateinit var content: ContentRepository
        private set

    fun init(context: Context) {
        if (::session.isInitialized) return
        val app = context.applicationContext
        session = SessionStore(app)
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "0.4.0"
        plex = PlexClient(session.clientIdentifier, version)
        home = HomeSessionManager(plex, session)
        servers = ServerManager(plex, session, scope)
        content = ContentRepository(ServerApi(plex), servers, scope)
    }

    /** Cierra sesión: desconecta servidores, olvida el perfil y borra el token. */
    fun signOut() {
        servers.clear()
        home.reset()
        session.setToken(null)
    }
}
