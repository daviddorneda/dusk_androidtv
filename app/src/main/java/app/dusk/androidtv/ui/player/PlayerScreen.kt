package app.dusk.androidtv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.nav.Route
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.ceil

private const val AUTO_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
private const val SEEK_COMMIT_DELAY_MS = 700L
private val SEEK_HOLD_RAMP = listOf(1, 1, 2, 3, 5)

private enum class HudMode { Hidden, Transport }

/**
 * Reproductor de tvOS (`PlayerSessionView` + `PlayerControlsTVOverlay`), parte básica. El mando
 * lo gestiona un único propietario (esta pantalla): ningún elemento del HUD es enfocable, igual que
 * en el original; el HUD solo dibuja lo que dice el estado.
 *
 * - Oculto: Aceptar o Arriba/Abajo muestran la barra; Izquierda/Derecha acumulan un salto (con una
 *   insignia en el centro); Reproducir/Pausar alterna; Atrás sale.
 * - Barra visible: Aceptar alterna reproducir/pausar; Izquierda/Derecha saltan; Atrás la oculta.
 *   Se oculta sola a los 5 s mientras se reproduce.
 * - Los saltos no llegan al motor de uno en uno: se acumulan y se aplica uno solo al soltar.
 * TODO: fila de acciones y panel (audio, subtítulos, info), salto de intro, Up Next, velocidad.
 */
@Composable
fun PlayerScreen(route: Route.Player, onExit: () -> Unit) {
    val session = remember(route) { PlayerSession(route) }
    DisposableEffect(session) { onDispose { session.close() } }

    val state by session.state.collectAsStateWithLifecycle()
    val snapshot by session.engine.snapshot.collectAsStateWithLifecycle()
    val tv = DuskTheme.tv
    val scope = rememberCoroutineScope()
    val rootFocus = remember { FocusRequester() }

    // El televisor no debe apagar la pantalla ni activar el salvapantallas mientras se reproduce.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var mode by remember { mutableStateOf(HudMode.Hidden) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var pendingSeekMs by remember { mutableLongStateOf(0L) }
    var commitJob by remember { mutableStateOf<Job?>(null) }
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == VlcEngine.Phase.Playing) hasStarted = true
    }
    // Fin natural de la reproducción: se cierra (Up Next llegará más adelante).
    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == VlcEngine.Phase.Ended) onExit()
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    fun touch() {
        lastInteraction = System.nanoTime()
    }

    fun commitSeek() {
        val target = (snapshot.positionMs + pendingSeekMs)
        pendingSeekMs = 0
        session.engine.seekTo(target)
    }

    fun cancelSeek() {
        commitJob?.cancel()
        pendingSeekMs = 0
    }

    fun nudgeSeek(direction: Int, repeatCount: Int) {
        val multiplier = SEEK_HOLD_RAMP[(repeatCount / 6).coerceIn(0, SEEK_HOLD_RAMP.lastIndex)]
        val duration = snapshot.durationMs
        var next = pendingSeekMs + direction * SEEK_STEP_MS * multiplier
        val target = snapshot.positionMs + next
        if (duration > 0 && target > duration) next = duration - snapshot.positionMs
        if (target < 0) next = -snapshot.positionMs
        pendingSeekMs = next
        touch()
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(SEEK_COMMIT_DELAY_MS)
            commitSeek()
        }
    }

    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val repeat = event.nativeKeyEvent.repeatCount
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (repeat == 0) {
                    if (mode == HudMode.Hidden) {
                        mode = HudMode.Transport
                        touch()
                    } else {
                        session.engine.togglePlayPause()
                        touch()
                    }
                }
                true
            }
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                if (repeat == 0) {
                    session.engine.togglePlayPause()
                    mode = HudMode.Transport
                    touch()
                }
                true
            }
            Key.DirectionLeft, Key.MediaRewind -> { nudgeSeek(-1, repeat); true }
            Key.DirectionRight, Key.MediaFastForward -> { nudgeSeek(1, repeat); true }
            Key.DirectionUp, Key.DirectionDown -> {
                mode = HudMode.Transport
                touch()
                true
            }
            else -> false
        }
    }

    // Se oculta solo a los 5 s si se está reproduciendo y no hay un salto pendiente.
    LaunchedEffect(mode, lastInteraction, snapshot.phase, pendingSeekMs) {
        if (mode == HudMode.Transport && snapshot.phase == VlcEngine.Phase.Playing && pendingSeekMs == 0L) {
            delay(AUTO_HIDE_MS)
            mode = HudMode.Hidden
        }
    }

    // Atrás: cancela un salto pendiente, luego oculta la barra y por último sale.
    BackHandler {
        when {
            pendingSeekMs != 0L -> cancelSeek()
            mode == HudMode.Transport && state.phase is PlayerPhase.Ready -> mode = HudMode.Hidden
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event -> if (state.phase is PlayerPhase.Ready) handle(event) else false }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> VLCVideoLayout(context).also { session.engine.attach(it) } },
            onRelease = { session.engine.detach() },
        )

        val item = state.item
        val failed = state.phase as? PlayerPhase.Failed
        val started = hasStarted && state.phase is PlayerPhase.Ready

        if (!started && failed == null) {
            PlayerLoadingView(
                title = item?.displayTitle,
                subtitle = item?.displaySubtitle,
                posterPath = item?.thumb,
                serverId = route.serverId,
            )
        }

        if (started) {
            val showBuffering = snapshot.isBuffering && snapshot.phase == VlcEngine.Phase.Playing
            if (showBuffering) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DuskSpinner(size = tv.dp(64), color = Color.White)
                }
            }

            AnimatedVisibility(
                visible = mode == HudMode.Transport,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val previewPosition = (snapshot.positionMs + pendingSeekMs).coerceAtLeast(0)
                PlayBar(
                    title = item?.displayTitle.orEmpty(),
                    subtitle = item?.displaySubtitle,
                    positionMs = previewPosition,
                    durationMs = snapshot.durationMs,
                    isPlaying = snapshot.phase == VlcEngine.Phase.Playing,
                )
            }

            if (pendingSeekMs != 0L) SeekBadge(pendingSeekMs)
        }

        if (failed != null) {
            PlayerErrorView(
                message = failed.message,
                actionLabel = if (failed.needsSignIn) "Sign In" else "OK",
                onAction = {
                    if (failed.needsSignIn) DuskGraph.signOut()
                    onExit()
                },
            )
        }
    }
}

@Composable
private fun PlayerLoadingView(title: String?, subtitle: String?, posterPath: String?, serverId: String) {
    val tv = DuskTheme.tv
    val density = LocalDensity.current.density
    val connection = DuskGraph.content.connectionFor(serverId)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(24)),
        ) {
            val url = PlexImages.url(
                connection,
                posterPath,
                ceil(240 * tv.scale * density).toInt(),
                ceil(360 * tv.scale * density).toInt(),
            )
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(tv.dp(360))
                        .widthIn(max = tv.dp(240))
                        .clip(RoundedCornerShape(tv.dp(16))),
                )
            }
            if (title != null) {
                BasicText(
                    text = title,
                    style = DuskTheme.type.sectionHeader.copy(color = Color.White, textAlign = TextAlign.Center),
                    maxLines = 2,
                    modifier = Modifier.widthIn(max = tv.dp(900)),
                )
            }
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = DuskTheme.type.caption.copy(color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = tv.dp(900)),
                )
            }
            DuskSpinner(size = tv.dp(48), color = Color.White)
        }
    }
}

@Composable
private fun PlayBar(title: String, subtitle: String?, positionMs: Long, durationMs: Long, isPlaying: Boolean) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.85f),
                ),
            )
            .padding(start = tv.dp(90), end = tv.dp(90), top = tv.dp(160), bottom = tv.dp(72)),
        verticalArrangement = Arrangement.spacedBy(tv.dp(22)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tv.dp(4))) {
            BasicText(
                text = title,
                style = type.playerTitle.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = type.caption.copy(color = Color.White.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(tv.dp(24)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuskIcon(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                tv.dp(36),
                Color.White,
            )
            BasicText(text = formatClock(positionMs), style = timeStyle(tv.sp(25)))
            Box(
                Modifier
                    .weight(1f)
                    .height(tv.dp(10))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White))
            }
            BasicText(
                text = if (durationMs > 0) "-" + formatClock((durationMs - positionMs).coerceAtLeast(0)) else "--:--",
                style = timeStyle(tv.sp(25)),
            )
        }
    }
}

private fun timeStyle(size: androidx.compose.ui.unit.TextUnit) = androidx.compose.ui.text.TextStyle(
    fontSize = size,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.Monospace,
    color = Color.White.copy(alpha = 0.85f),
)

/** Insignia central mientras se acumula un salto: "+30s" / "-20s". */
@Composable
private fun SeekBadge(pendingMs: Long) {
    val tv = DuskTheme.tv
    val seconds = abs(pendingMs) / 1000
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = (if (pendingMs > 0) "+" else "-") + "${seconds}s",
            style = DuskTheme.type.heroSubtitle.copy(color = Color.White),
            modifier = Modifier
                .clip(RoundedCornerShape(tv.dp(24)))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = tv.dp(34), vertical = tv.dp(16)),
        )
    }
}

@Composable
private fun PlayerErrorView(message: String, actionLabel: String, onAction: () -> Unit) {
    val tv = DuskTheme.tv
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(horizontal = tv.dp(64)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(20)),
        ) {
            BasicText(text = "Couldn’t Play", style = DuskTheme.type.sectionHeader.copy(color = Color.White))
            BasicText(
                text = message,
                style = DuskTheme.type.pageSubtitle.copy(color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(720)),
            )
            DuskGlassButton(
                text = actionLabel,
                onClick = onAction,
                focusRequester = focus,
                modifier = Modifier.widthIn(min = tv.dp(320)),
            )
        }
    }
}

/** "1:05:09" o "5:09". */
private fun formatClock(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
