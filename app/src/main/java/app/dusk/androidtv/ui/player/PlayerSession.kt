package app.dusk.androidtv.ui.player

import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.PlaybackItem
import app.dusk.androidtv.data.plex.PlexStreamInfo
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data object Ready : PlayerPhase
    data class Failed(val message: String, val needsSignIn: Boolean = false) : PlayerPhase
}

data class PlayerUiState(val phase: PlayerPhase = PlayerPhase.Loading, val item: PlaybackItem? = null)

/** Una pista de audio del panel: el título viene de Plex (`AudioTrack`) y `id` es el del motor. */
data class AudioChoice(val id: Int, val title: String, val detail: String?, val selected: Boolean)

/**
 * Una fila de subtítulos: "Off", una pista del fichero (con `engineId`) o un fichero externo
 * (`stream.isExternal`, que se añade al motor al elegirlo).
 */
data class SubtitleChoice(
    val key: String,
    val title: String,
    val detail: String?,
    val selected: Boolean,
    val engineId: Int?,
    val stream: PlexStreamInfo?,
)

data class TrackChoices(
    val audio: List<AudioChoice> = emptyList(),
    val subtitles: List<SubtitleChoice> = emptyList(),
) {
    val selectedSubtitleTitle: String get() = subtitles.firstOrNull { it.selected }?.title ?: "Off"
}

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

    // Subtítulo externo elegido y qué pista del motor le tocó al añadirlo.
    private val externalSelected = MutableStateFlow<String?>(null)
    private val externalEngineIds = mutableMapOf<String, Int>()
    private var initialTracksApplied = false

    /**
     * Pistas que muestra el panel. Los nombres vienen de Plex y se emparejan con las del motor por
     * orden (Plex y el motor listan las pistas del fichero en el mismo orden); si no cuadran, se usan
     * los nombres del motor.
     */
    val choices: StateFlow<TrackChoices> = combine(engine.snapshot, state, externalSelected) { snapshot, ui, external ->
        buildChoices(snapshot, ui.item, external)
    }.stateIn(scope, SharingStarted.Eagerly, TrackChoices())
    private var item: PlaybackItem? = null
    private var timelineJob: Job? = null
    private var scrobbled = false
    private var closed = false

    init {
        start()
        // Las pistas llegan después de empezar: en cuanto estén, se aplican las que Plex marca como elegidas.
        scope.launch { engine.snapshot.collect { if (it.audioTracks.isNotEmpty()) applyInitialTracks(it) } }
        // Un error del motor con la reproducción ya en marcha se muestra como "Couldn't Play".
        scope.launch {
            engine.snapshot.map { it.phase }.distinctUntilChanged().collect { phase ->
                when (phase) {
                    VlcEngine.Phase.Error -> _state.value = _state.value.copy(
                        phase = PlayerPhase.Failed(engine.snapshot.value.error ?: "The player could not open this file."),
                    )
                    VlcEngine.Phase.Playing -> {
                        applyInitialTracks(engine.snapshot.value)
                        report("playing")
                    }
                    VlcEngine.Phase.Paused -> report("paused")
                    else -> Unit
                }
            }
        }
    }

    private fun buildChoices(snapshot: VlcEngine.Snapshot, item: PlaybackItem?, external: String?): TrackChoices {
        val plexAudio = item?.audioStreams.orEmpty()
        val audio = snapshot.audioTracks.mapIndexed { index, track ->
            val stream = plexAudio.getOrNull(index)?.takeIf { plexAudio.size == snapshot.audioTracks.size }
            AudioChoice(
                id = track.id,
                title = stream?.compactTitle ?: track.name,
                detail = stream?.audioDetail,
                selected = snapshot.selectedAudioId == track.id,
            )
        }

        val plexSubtitles = item?.subtitleStreams.orEmpty()
        val embedded = plexSubtitles.filterNot { it.isExternal }
        val subtitles = mutableListOf(
            SubtitleChoice(
                key = "off",
                title = "Off",
                detail = null,
                selected = snapshot.selectedSubtitleId < 0,
                engineId = -1,
                stream = null,
            ),
        )
        if (plexSubtitles.isEmpty()) {
            snapshot.subtitleTracks.forEach { track ->
                subtitles += SubtitleChoice("engine-${track.id}", track.name, null, snapshot.selectedSubtitleId == track.id, track.id, null)
            }
        } else {
            plexSubtitles.forEach { stream ->
                if (stream.isExternal) {
                    val engineId = externalEngineIds[stream.id]
                    subtitles += SubtitleChoice(
                        key = "plex-${stream.id}",
                        title = stream.displayTitle,
                        detail = stream.subtitleDetail,
                        selected = external == stream.id && snapshot.selectedSubtitleId >= 0,
                        engineId = engineId,
                        stream = stream,
                    )
                } else {
                    val engineTrack = snapshot.subtitleTracks.getOrNull(embedded.indexOf(stream))
                    subtitles += SubtitleChoice(
                        key = "plex-${stream.id}",
                        title = stream.displayTitle,
                        detail = stream.subtitleDetail,
                        selected = engineTrack != null && external == null && snapshot.selectedSubtitleId == engineTrack.id,
                        engineId = engineTrack?.id,
                        stream = stream,
                    )
                }
            }
        }
        return TrackChoices(audio, subtitles)
    }

    fun selectAudio(choice: AudioChoice) = engine.selectAudio(choice.id)

    fun selectSubtitle(choice: SubtitleChoice) {
        val stream = choice.stream
        when {
            stream == null || !stream.isExternal -> {
                externalSelected.value = null
                choice.engineId?.let { engine.selectSubtitle(it) }
            }
            else -> scope.launch { selectExternalSubtitle(stream) }
        }
    }

    /** Un subtítulo externo se añade al motor la primera vez; después basta con volver a seleccionarlo. */
    private suspend fun selectExternalSubtitle(stream: PlexStreamInfo) {
        val connection = connection ?: return
        val key = stream.externalKey ?: return
        externalSelected.value = stream.id
        val knownEngineId = externalEngineIds[stream.id]
        if (knownEngineId != null) {
            engine.selectSubtitle(knownEngineId)
            return
        }
        val before = engine.snapshot.value.subtitleTracks.map { it.id }.toSet()
        engine.addExternalSubtitle(DuskGraph.serverApi.directPlayUrl(connection, key), select = true)
        // La pista nueva aparece en la lista del motor al cabo de un momento.
        val added = withTimeoutOrNull(3_000) {
            engine.snapshot.first { snapshot -> snapshot.subtitleTracks.any { it.id !in before } }
        }
        added?.subtitleTracks?.firstOrNull { it.id !in before }?.let { externalEngineIds[stream.id] = it.id }
    }

    /** Aplica las pistas que Plex marca como elegidas, una sola vez, cuando el motor ya las conoce. */
    private fun applyInitialTracks(snapshot: VlcEngine.Snapshot) {
        val current = item ?: return
        if (initialTracksApplied || snapshot.audioTracks.isEmpty()) return
        initialTracksApplied = true

        val audioIndex = current.audioStreams.indexOfFirst { it.isSelected }
        if (audioIndex >= 0 && current.audioStreams.size == snapshot.audioTracks.size) {
            val target = snapshot.audioTracks[audioIndex].id
            if (target != snapshot.selectedAudioId) engine.selectAudio(target)
        }

        val selectedSubtitle = current.subtitleStreams.firstOrNull { it.isSelected }
        if (selectedSubtitle != null) {
            if (selectedSubtitle.isExternal) {
                scope.launch { selectExternalSubtitle(selectedSubtitle) }
            } else {
                val embedded = current.subtitleStreams.filterNot { it.isExternal }
                snapshot.subtitleTracks.getOrNull(embedded.indexOf(selectedSubtitle))?.let { engine.selectSubtitle(it.id) }
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
