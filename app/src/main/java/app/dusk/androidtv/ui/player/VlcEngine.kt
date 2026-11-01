package app.dusk.androidtv.ui.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Motor de reproducción sobre libVLC (equivale a `VLCKitEngine` del original: el mismo motor, así que
 * los mismos códecs, contenedores y subtítulos). Publica un estado simple que el reproductor observa.
 * TODO: pistas de audio y subtítulos, velocidad, y un segundo motor (Media3) para HDR y Dolby Vision.
 */
class VlcEngine(context: Context) {
    enum class Phase { Idle, Opening, Playing, Paused, Ended, Error }

    data class Snapshot(
        val phase: Phase = Phase.Idle,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val isBuffering: Boolean = false,
        val error: String? = null,
    ) {
        val isPlaying: Boolean get() = phase == Phase.Playing
    }

    private val libVlc = LibVLC(context.applicationContext, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
    private val player = MediaPlayer(libVlc)
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()
    private var released = false

    init {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> _snapshot.update { it.copy(phase = Phase.Opening) }
                MediaPlayer.Event.Buffering -> _snapshot.update { it.copy(isBuffering = event.buffering < 100f) }
                MediaPlayer.Event.Playing -> _snapshot.update { it.copy(phase = Phase.Playing, isBuffering = false) }
                MediaPlayer.Event.Paused -> _snapshot.update { it.copy(phase = Phase.Paused) }
                MediaPlayer.Event.TimeChanged -> _snapshot.update { it.copy(positionMs = event.timeChanged) }
                MediaPlayer.Event.LengthChanged -> _snapshot.update { it.copy(durationMs = event.lengthChanged) }
                MediaPlayer.Event.EndReached -> _snapshot.update { it.copy(phase = Phase.Ended) }
                MediaPlayer.Event.EncounteredError -> _snapshot.update {
                    it.copy(phase = Phase.Error, isBuffering = false, error = "The player could not open this file.")
                }
            }
        }
    }

    fun attach(layout: VLCVideoLayout) {
        if (released) return
        player.attachViews(layout, null, false, false)
    }

    fun detach() {
        if (released) return
        player.detachViews()
    }

    /** Abre la URL y empieza a reproducir; `startMs` reanuda desde ese punto. */
    fun load(url: String, startMs: Long) {
        if (released) return
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=2000")
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        player.media = media
        media.release()
        _snapshot.update { it.copy(phase = Phase.Opening, positionMs = startMs, error = null) }
        player.play()
    }

    fun play() {
        if (!released) player.play()
    }

    fun pause() {
        if (!released) player.pause()
    }

    fun togglePlayPause() {
        if (released) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val duration = _snapshot.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        player.time = target
        _snapshot.update { it.copy(positionMs = target) }
    }

    fun release() {
        if (released) return
        released = true
        player.setEventListener(null)
        try {
            player.stop()
            player.detachViews()
        } catch (e: Exception) {
            // El motor ya estaba parado.
        }
        player.release()
        libVlc.release()
    }
}
