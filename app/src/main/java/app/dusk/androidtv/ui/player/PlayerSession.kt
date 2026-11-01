package app.dusk.androidtv.ui.player

import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.PlaybackItem
import app.dusk.androidtv.data.plex.PlexHttpException
import app.dusk.androidtv.ui.nav.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data object Ready : PlayerPhase
    data class Failed(val message: String, val needsSignIn: Boolean = false) : PlayerPhase
}

data class PlayerUiState(val phase: PlayerPhase = PlayerPhase.Loading, val item: PlaybackItem? = null)

/**
 * Sesión de reproducción (`PlaybackCoordinator` + `PlayerViewModel`, parte básica): carga el elemento,
 * abre la reproducción directa, avisa a Plex del progreso cada 10 s y, al cerrar, envía el estado
 * "stopped" y marca como visto si pasó del 90 %. Vive solo mientras el reproductor está en pantalla.
 * TODO: versiones, escalera de entrega (transcodificación de respaldo), marcadores, Up Next.
 */
class PlayerSession(private val route: Route.Player) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionId = UUID.randomUUID().toString()

    val engine = VlcEngine(DuskGraph.appContext)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val connection get() = DuskGraph.content.connectionFor(route.serverId)
    private var item: PlaybackItem? = null
    private var timelineJob: Job? = null
    private var scrobbled = false
    private var closed = false

    init {
        start()
        // Un error del motor con la reproducción ya en marcha se muestra como "Couldn't Play".
        scope.launch {
            engine.snapshot.map { it.phase }.distinctUntilChanged().collect { phase ->
                when (phase) {
                    VlcEngine.Phase.Error -> _state.value = _state.value.copy(
                        phase = PlayerPhase.Failed(engine.snapshot.value.error ?: "The player could not open this file."),
                    )
                    VlcEngine.Phase.Playing -> report("playing")
                    VlcEngine.Phase.Paused -> report("paused")
                    else -> Unit
                }
            }
        }
    }

    private fun start() {
        scope.launch {
            try {
                val connection = connection
                    ?: throw IllegalStateException("This server is not available right now.")
                val loaded = DuskGraph.serverApi.playbackItem(connection, route.ratingKey)
                item = loaded
                _state.value = PlayerUiState(PlayerPhase.Loading, loaded)
                // El detalle recién pedido manda si trae una posición; `0` fuerza empezar desde el principio.
                val startMs = route.resumeOffsetMs ?: loaded.viewOffsetMs
                engine.load(DuskGraph.serverApi.directPlayUrl(connection, loaded.partKey), startMs)
                _state.value = PlayerUiState(PlayerPhase.Ready, loaded)
                startTimeline()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlexHttpException) {
                _state.value = _state.value.copy(
                    phase = PlayerPhase.Failed(
                        if (e.code == 401) "Your Plex session has expired. Sign in again to keep watching." else e.message ?: "Couldn't load this item.",
                        needsSignIn = e.code == 401,
                    ),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = PlayerPhase.Failed(e.message ?: "Couldn't load this item."))
            }
        }
    }

    /** Progreso cada 10 s (`startTimelineReporting`). */
    private fun startTimeline() {
        timelineJob?.cancel()
        timelineJob = scope.launch {
            while (isActive) {
                delay(TIMELINE_INTERVAL_MS)
                val snapshot = engine.snapshot.value
                report(
                    when {
                        snapshot.isBuffering || snapshot.phase == VlcEngine.Phase.Opening -> "buffering"
                        snapshot.phase == VlcEngine.Phase.Paused -> "paused"
                        else -> "playing"
                    },
                )
            }
        }
    }

    private suspend fun report(state: String) {
        val connection = connection ?: return
        val current = item ?: return
        val snapshot = engine.snapshot.value
        val duration = snapshot.durationMs.takeIf { it > 0 } ?: current.durationMs
        try {
            DuskGraph.serverApi.timeline(connection, current.ratingKey, state, snapshot.positionMs, duration, sessionId)
            scrobbleIfFinished(snapshot.positionMs, duration)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo de red al avisar no interrumpe la reproducción.
        }
    }

    /** Se marca como visto una sola vez, al pasar del 90 % de la duración. */
    private suspend fun scrobbleIfFinished(positionMs: Long, durationMs: Long) {
        if (scrobbled || durationMs <= 0 || positionMs <= durationMs * 0.9) return
        val connection = connection ?: return
        val current = item ?: return
        scrobbled = true
        try {
            DuskGraph.serverApi.setWatched(connection, current.ratingKey, true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            scrobbled = false
        }
    }

    /**
     * Cierra la sesión: para el motor y avisa de "stopped" y del marcado como visto. Se hace en el
     * ámbito de la app para que el aviso salga aunque la pantalla ya se haya cerrado.
     */
    fun close() {
        if (closed) return
        closed = true
        val snapshot = engine.snapshot.value
        val connection = connection
        val current = item
        timelineJob?.cancel()
        engine.release()
        scope.cancel()
        if (connection != null && current != null) {
            DuskGraph.scope.launch {
                val duration = snapshot.durationMs.takeIf { it > 0 } ?: current.durationMs
                try {
                    DuskGraph.serverApi.timeline(connection, current.ratingKey, "stopped", snapshot.positionMs, duration, sessionId)
                    if (!scrobbled && duration > 0 && snapshot.positionMs > duration * 0.9) {
                        DuskGraph.serverApi.setWatched(connection, current.ratingKey, true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // El servidor no está disponible: el progreso queda en el último aviso.
                }
                DuskGraph.notifyPlaybackFinished()
            }
        } else {
            DuskGraph.notifyPlaybackFinished()
        }
    }

    private companion object {
        const val TIMELINE_INTERVAL_MS = 10_000L
    }
}
