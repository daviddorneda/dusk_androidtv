package app.dusk.androidtv.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.PlaybackItem
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

// Constantes del HUD de tvOS (`PlayerTVHUDLayout`).
private const val AUTO_HIDE_MS = 4_500L
private const val SEEK_STEP_MS = 10_000L
private const val SEEK_COMMIT_DELAY_MS = 260L
private const val HOLD_INITIAL_DELAY_MS = 550L
private const val HOLD_REPEAT_MS = 160L
private val SEEK_HOLD_RAMP = listOf(1, 1, 1, 3, 3, 6, 6, 12)

private enum class HudMode { Hidden, Transport, Panel }

private enum class PanelTab(val title: String, @DrawableRes val icon: Int) {
    Info("Info", R.drawable.ic_info),
    Audio("Audio", R.drawable.ic_audio),
    Subtitles("Subtitles", R.drawable.ic_subtitles),
}

/** Acciones de la fila sobre la barra, en el orden en que se recorren con Izquierda/Derecha. */
private val ACTIONS = listOf(PanelTab.Subtitles, PanelTab.Audio, PanelTab.Info)

/**
 * Reproductor de tvOS (`PlayerSessionView` + `PlayerControlsTVOverlay`). El mando lo gestiona un único
 * propietario (esta pantalla): ni la barra ni la fila de acciones son enfocables, igual que en el
 * original; solo el panel usa el foco de verdad.
 *
 * - Oculto: Aceptar o Arriba/Abajo muestran la barra; Izquierda/Derecha acumulan un salto; Reproducir/
 *   Pausar alterna; Atrás sale.
 * - Barra: Aceptar alterna reproducir/pausar; Izquierda/Derecha saltan; Arriba sube a la fila de acciones;
 *   Abajo abre el panel. Fila de acciones: Izquierda/Derecha eligen, Aceptar abre el panel en esa
 *   pestaña, Abajo vuelve a la barra. Atrás retrocede un nivel.
 * - Los saltos se acumulan y se aplica uno solo tras 260 ms; mantener pulsado acelera (550 ms de espera,
 *   luego cada 160 ms con multiplicadores 1,1,1,3,3,6,6,12). La barra se oculta sola a los 4,5 s.
 * TODO: salto de intro, Up Next, velocidad, siguiente episodio, capítulos.
 */
@Composable
fun PlayerScreen(route: Route.Player, onExit: () -> Unit) {
    val session = remember(route) { PlayerSession(route) }
    DisposableEffect(session) { onDispose { session.close() } }

    val state by session.state.collectAsStateWithLifecycle()
    val snapshot by session.engine.snapshot.collectAsStateWithLifecycle()
    val choices by session.choices.collectAsStateWithLifecycle()
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
    var inActions by remember { mutableStateOf(false) }
    var actionIndex by remember { mutableIntStateOf(0) }
    var panelTab by remember { mutableStateOf(PanelTab.Info) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var pendingSeekMs by remember { mutableLongStateOf(0L) }
    var lastRepeatApplied by remember { mutableLongStateOf(0L) }
    var commitJob by remember { mutableStateOf<Job?>(null) }
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == VlcEngine.Phase.Playing) hasStarted = true
    }
    // Fin natural de la reproducción: se cierra (Up Next llegará más adelante).
    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == VlcEngine.Phase.Ended) onExit()
    }
    // El mando vuelve al propietario único cuando se cierra el panel.
    LaunchedEffect(mode) {
        if (mode != HudMode.Panel) {
            delay(50)
            runCatching { rootFocus.requestFocus() }
        }
    }

    fun touch() {
        lastInteraction = System.nanoTime()
    }

    fun commitSeek() {
        val target = snapshot.positionMs + pendingSeekMs
        pendingSeekMs = 0
        session.engine.seekTo(target)
    }

    fun cancelSeek() {
        commitJob?.cancel()
        pendingSeekMs = 0
    }

    fun nudgeSeek(direction: Int, multiplier: Int) {
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

    /** Un salto por pulsación; si se mantiene, tras la espera inicial acelera por tramos de 160 ms. */
    fun seekKey(direction: Int, event: KeyEvent) {
        val native = event.nativeKeyEvent
        if (native.repeatCount == 0) {
            lastRepeatApplied = 0
            nudgeSeek(direction, 1)
            return
        }
        val held = native.eventTime - native.downTime
        if (held < HOLD_INITIAL_DELAY_MS || held - lastRepeatApplied < HOLD_REPEAT_MS) return
        lastRepeatApplied = held
        val index = ((held - HOLD_INITIAL_DELAY_MS) / HOLD_REPEAT_MS).toInt().coerceIn(0, SEEK_HOLD_RAMP.lastIndex)
        nudgeSeek(direction, SEEK_HOLD_RAMP[index])
    }

    fun openPanel(tab: PanelTab) {
        panelTab = tab
        mode = HudMode.Panel
        inActions = false
    }

    fun handle(event: KeyEvent): Boolean {
        if (mode == HudMode.Panel) return false // el panel usa el foco de Compose
        if (event.type != KeyEventType.KeyDown) return false
        val repeat = event.nativeKeyEvent.repeatCount
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (repeat == 0) {
                    when {
                        mode == HudMode.Hidden -> { mode = HudMode.Transport; touch() }
                        inActions -> openPanel(ACTIONS[actionIndex])
                        else -> { session.engine.togglePlayPause(); touch() }
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
            Key.DirectionLeft, Key.MediaRewind -> {
                if (mode == HudMode.Transport && inActions) {
                    actionIndex = (actionIndex - 1).coerceAtLeast(0)
                    touch()
                } else {
                    seekKey(-1, event)
                }
                true
            }
            Key.DirectionRight, Key.MediaFastForward -> {
                if (mode == HudMode.Transport && inActions) {
                    actionIndex = (actionIndex + 1).coerceAtMost(ACTIONS.lastIndex)
                    touch()
                } else {
                    seekKey(1, event)
                }
                true
            }
            Key.DirectionUp -> {
                if (mode == HudMode.Hidden) mode = HudMode.Transport else inActions = true
                touch()
                true
            }
            Key.DirectionDown -> {
                when {
                    mode == HudMode.Hidden -> mode = HudMode.Transport
                    inActions -> inActions = false
                    else -> openPanel(panelTab)
                }
                touch()
                true
            }
            else -> false
        }
    }

    // Se oculta solo a los 4,5 s si se está reproduciendo y no hay un salto pendiente.
    LaunchedEffect(mode, lastInteraction, snapshot.phase, pendingSeekMs) {
        if (mode == HudMode.Transport && snapshot.phase == VlcEngine.Phase.Playing && pendingSeekMs == 0L) {
            delay(AUTO_HIDE_MS)
            mode = HudMode.Hidden
            inActions = false
        }
    }

    // Atrás retrocede un nivel: salto pendiente -> panel -> fila de acciones -> barra -> salir.
    BackHandler {
        when {
            pendingSeekMs != 0L -> cancelSeek()
            mode == HudMode.Panel -> { mode = HudMode.Transport; touch() }
            inActions -> { inActions = false; touch() }
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
                Hud(
                    title = item?.displayTitle.orEmpty(),
                    subtitle = item?.displaySubtitle,
                    positionMs = previewPosition,
                    durationMs = snapshot.durationMs,
                    isScrubbing = pendingSeekMs != 0L,
                    inActions = inActions,
                    actionIndex = actionIndex,
                )
            }

            if (pendingSeekMs != 0L) SeekBadge(pendingSeekMs)

            if (mode == HudMode.Panel && item != null) {
                PlayerPanel(
                    tab = panelTab,
                    onTab = { panelTab = it },
                    item = item,
                    choices = choices,
                    onSelectAudio = session::selectAudio,
                    onSelectSubtitle = session::selectSubtitle,
                )
            }
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

/**
 * HUD con las medidas de `PlayerTVHUDLayout`: velo inferior, título, fila de acciones (62 pt, 18 pt de
 * separación) y la barra (8 pt, cabeza de 20 pt, 28 pt al saltar) con los tiempos debajo.
 */
@Composable
private fun Hud(
    title: String,
    subtitle: String?,
    positionMs: Long,
    durationMs: Long,
    isScrubbing: Boolean,
    inActions: Boolean,
    actionIndex: Int,
) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val headDiameter = tv.dp(if (isScrubbing) 28 else 20)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.5f),
                    1f to Color.Black.copy(alpha = 0.82f),
                ),
            )
            .padding(start = tv.dp(60), end = tv.dp(60), top = tv.dp(150), bottom = tv.dp(48)),
        verticalArrangement = Arrangement.spacedBy(tv.dp(20)),
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

        Row(horizontalArrangement = Arrangement.spacedBy(tv.dp(18))) {
            ACTIONS.forEachIndexed { index, action ->
                ActionButton(icon = action.icon, selected = inActions && index == actionIndex)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tv.dp(10))) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(tv.dp(28))) {
                val trackWidth = maxWidth
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(tv.dp(8))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(tv.dp(1), Color.White.copy(alpha = 0.18f), CircleShape),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(tv.dp(8))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.96f)),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = trackWidth * fraction - headDiameter / 2)
                        .size(headDiameter)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(text = formatClock(positionMs), style = readoutStyle(tv.sp(25), 0.9f))
                BasicText(
                    text = if (durationMs > 0) "-" + formatClock((durationMs - positionMs).coerceAtLeast(0)) else "--:--",
                    style = readoutStyle(tv.sp(25), 0.6f),
                )
            }
        }
    }
}

/** Botón de la fila de acciones: círculo de 62 pt; el seleccionado se vuelve blanco y crece. */
@Composable
private fun ActionButton(@DrawableRes icon: Int, selected: Boolean) {
    val tv = DuskTheme.tv
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, label = "actionScale")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(tv.dp(62))
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        DuskIcon(icon, tv.dp(30), if (selected) Color(0xFF090A0F) else Color.White)
    }
}

private fun readoutStyle(size: androidx.compose.ui.unit.TextUnit, alpha: Float) = TextStyle(
    fontSize = size,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.Monospace,
    color = Color.White.copy(alpha = alpha),
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

// ---------------------------------------------------------------- Panel (`PlayerTVInfoPanel`)

/**
 * Panel inferior con pestañas Info, Audio y Subtítulos. Usa el foco de Compose: al mover el foco entre
 * pestañas cambia el contenido. Ocupa el 62 % de la altura.
 */
@Composable
private fun PlayerPanel(
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    item: PlaybackItem,
    choices: TrackChoices,
    onSelectAudio: (AudioChoice) -> Unit,
    onSelectSubtitle: (SubtitleChoice) -> Unit,
) {
    val tv = DuskTheme.tv
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val tabFocus = remember { PanelTab.entries.associateWith { FocusRequester() } }
    val firstRowFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { tabFocus.getValue(tab).requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = tv.dp(60), end = tv.dp(60), bottom = tv.dp(48))
                .fillMaxWidth()
                .height((screenHeightDp * 0.62f).dp)
                .clip(RoundedCornerShape(tv.dp(28)))
                .background(Color(0xFF0B0C11).copy(alpha = 0.92f))
                .border(tv.dp(1), Color.White.copy(alpha = 0.10f), RoundedCornerShape(tv.dp(28))),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = tv.dp(44), vertical = tv.dp(22)),
                horizontalArrangement = Arrangement.spacedBy(tv.dp(14)),
            ) {
                PanelTab.entries.forEach { entry ->
                    PanelTabButton(
                        tab = entry,
                        selected = entry == tab,
                        focusRequester = tabFocus.getValue(entry),
                        onFocused = { onTab(entry) },
                        onDown = { runCatching { firstRowFocus.requestFocus() }.isSuccess },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(tv.dp(1)).background(Color.White.copy(alpha = 0.10f)))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tv.dp(44), vertical = tv.dp(22)),
                verticalArrangement = Arrangement.spacedBy(tv.dp(8)),
            ) {
                when (tab) {
                    PanelTab.Info -> InfoContent(item, choices)
                    PanelTab.Audio -> {
                        if (choices.audio.isEmpty()) PanelEmpty("No audio tracks found.")
                        choices.audio.forEachIndexed { index, choice ->
                            PanelRow(
                                title = choice.title,
                                subtitle = choice.detail,
                                selected = choice.selected,
                                focusRequester = if (index == 0) firstRowFocus else null,
                                onClick = { onSelectAudio(choice) },
                            )
                        }
                    }
                    PanelTab.Subtitles -> choices.subtitles.forEachIndexed { index, choice ->
                        PanelRow(
                            title = choice.title,
                            subtitle = choice.detail,
                            selected = choice.selected,
                            focusRequester = if (index == 0) firstRowFocus else null,
                            onClick = { onSelectSubtitle(choice) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelTabButton(
    tab: PanelTab,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onDown: () -> Boolean,
) {
    val tv = DuskTheme.tv
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val content = if (focused) Color(0xFF090A0F) else Color.White.copy(alpha = if (selected) 1f else 0.7f)

    Row(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) onDown() else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFocused,
            )
            .padding(horizontal = tv.dp(26), vertical = tv.dp(14)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuskIcon(tab.icon, tv.dp(28), content)
        BasicText(text = tab.title, style = DuskTheme.type.cardTitle.copy(color = content))
    }
}

/** Fila del panel (`panelRow`): título, detalle y una marca en la pista elegida. */
@Composable
private fun PanelRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val tv = DuskTheme.tv
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(tv.dp(14)))
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = tv.dp(72))
            .padding(horizontal = tv.dp(22), vertical = tv.dp(12)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tv.dp(2))) {
            BasicText(
                text = title,
                style = DuskTheme.type.rowTitle.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = DuskTheme.type.caption.copy(color = Color.White.copy(alpha = 0.6f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) DuskIcon(R.drawable.ic_check, tv.dp(28), Color.White)
    }
}

@Composable
private fun PanelEmpty(message: String) {
    BasicText(
        text = message,
        style = DuskTheme.type.pageSubtitle.copy(color = Color.White.copy(alpha = 0.6f)),
        modifier = Modifier.padding(vertical = DuskTheme.tv.dp(12)),
    )
}

/** Pestaña Info: título, resumen y los datos de la reproducción (motor, modo, vídeo, audio, resolución, subtítulos). */
@Composable
private fun InfoContent(item: PlaybackItem, choices: TrackChoices) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    BasicText(text = item.displayTitle, style = type.sectionHeader.copy(color = Color.White), maxLines = 2)
    item.displaySubtitle?.let {
        BasicText(text = it, style = type.rowValue.copy(color = Color.White.copy(alpha = 0.6f)), maxLines = 1)
    }
    item.summary?.let {
        BasicText(
            text = it,
            style = type.body.copy(color = Color.White.copy(alpha = 0.86f)),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = tv.dp(8)),
        )
    }
    val audio = listOfNotNull(
        item.audioCodec,
        item.audioChannels?.let { channelLabel(it) },
    ).joinToString(" ").takeIf { it.isNotEmpty() }
    val rows = listOfNotNull(
        "Engine" to "libVLC",
        "Mode" to "Direct Play",
        item.videoCodec?.uppercase()?.let { "Video" to it },
        audio?.let { "Audio" to it },
        item.resolution?.let { "Resolution" to it },
        "Subtitles" to choices.selectedSubtitleTitle,
    )
    Column(Modifier.padding(top = tv.dp(14)), verticalArrangement = Arrangement.spacedBy(tv.dp(10))) {
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(tv.dp(24))) {
                BasicText(
                    text = label.uppercase(),
                    style = type.groupHeader.copy(color = Color.White.copy(alpha = 0.5f)),
                    modifier = Modifier.width(tv.dp(220)),
                )
                BasicText(
                    text = value,
                    style = type.rowValue.copy(color = Color.White.copy(alpha = 0.9f)),
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 1 -> "1.0", 2 -> "2.0", 6 -> "5.1", 8 -> "7.1". */
private fun channelLabel(channels: Int): String = when (channels) {
    6 -> "5.1"
    7 -> "6.1"
    8 -> "7.1"
    else -> "$channels.0"
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
