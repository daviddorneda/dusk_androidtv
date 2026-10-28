package app.dusk.androidtv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.HomeShelf
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.data.plex.PlexMediaType
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.HeroBackdropOverlay
import app.dusk.androidtv.ui.components.HeroBottomFade
import app.dusk.androidtv.ui.components.HeroOverlay
import app.dusk.androidtv.ui.components.NoAutoScrollSpec
import app.dusk.androidtv.ui.components.PosterCard
import app.dusk.androidtv.ui.components.RowBringIntoViewSpec
import app.dusk.androidtv.ui.components.duskHeroBottomFade
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

private const val HERO_ROTATION_MS = 8_000L
private const val POSTER_WIDTH_PT = 232
private const val POSTER_HEIGHT_PT = 348
private const val SHELF_TOP_MARGIN_PT = 84
private const val SHELF_BOTTOM_MARGIN_PT = 40

private fun FocusRequester.tryFocus(): Boolean = try {
    requestFocus()
    true
} catch (e: IllegalStateException) {
    false
}

/**
 * Lleva la fila (`itemIndex`) a la vista con el desplazamiento mínimo y una animación suave:
 * deja `topMargin` libre por arriba (el hueco del primer estante) y `bottomMargin` por abajo.
 * Desde el héroe esto sube la página justo hasta el "pliegue", como en el original.
 */
private suspend fun revealShelf(state: LazyListState, itemIndex: Int, topMargin: Float, bottomMargin: Float) {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == itemIndex }
    if (item == null) {
        state.animateScrollToItem(itemIndex)
        return
    }
    val viewportStart = info.viewportStartOffset.toFloat()
    val viewportEnd = info.viewportEndOffset.toFloat()
    val top = item.offset.toFloat()
    val bottom = top + item.size
    val distance = when {
        top < viewportStart + topMargin -> top - (viewportStart + topMargin)
        bottom > viewportEnd - bottomMargin ->
            minOf(bottom - (viewportEnd - bottomMargin), top - (viewportStart + topMargin))
        else -> 0f
    }
    if (distance != 0f) state.animateScrollBy(distance, tween(350, easing = FastOutSlowInEasing))
}

/**
 * Home de tvOS (`HomeTVView`): héroe cinemático a pantalla completa con lo que estás viendo
 * y, debajo, las filas de pósters. Todos los servidores conectados se muestran fusionados.
 * TODO: Live TV, Watchlist, estantes personalizados y navegación al detalle (fase 4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    listState: LazyListState,
    heroFocus: FocusRequester,
    tabBarFocus: FocusRequester,
    firstShelfFocus: FocusRequester,
    onPlay: (PlexItem) -> Unit,
    onOpen: (PlexItem) -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val content by DuskGraph.content.home.collectAsStateWithLifecycle()
    val connections by DuskGraph.content.connections.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Las medidas del diseño están en puntos de tvOS; la imagen se pide en píxeles reales.
    val imageUrl: (PlexItem, String?, Int, Int) -> String? = { item, path, widthPt, heightPt ->
        PlexImages.url(
            connection = connections[item.serverId],
            path = path,
            width = ceil(widthPt * tv.scale * density.density).toInt(),
            height = ceil(heightPt * tv.scale * density.density).toInt(),
        )
    }

    val hasHero = content.heroItems.isNotEmpty()
    val firstShelfItemIndex = if (hasHero) 2 else 1
    val topMarginPx = with(density) { tv.dp(SHELF_TOP_MARGIN_PT).toPx() }
    val bottomMarginPx = with(density) { tv.dp(SHELF_BOTTOM_MARGIN_PT).toPx() }
    var focusedShelf by remember { mutableIntStateOf(-1) }

    // El desplazamiento vertical lo decide la fila enfocada (no el foco de Compose): una sola
    // animación suave por cambio de fila y nada al mover el foco de lado dentro de la misma fila.
    LaunchedEffect(focusedShelf, firstShelfItemIndex) {
        if (focusedShelf < 0) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        revealShelf(listState, firstShelfItemIndex + focusedShelf, topMarginPx, bottomMarginPx)
    }

    // Arriba/abajo entre filas. Si la fila vecina aún no está compuesta (LazyColumn solo compone lo
    // visible) se avanza un poco y se reintenta.
    val moveVertically: (FocusDirection, Int) -> Unit = { direction, shelfIndex ->
        scope.launch {
            val step = listState.layoutInfo.viewportSize.height * 0.35f
            if (direction == FocusDirection.Down) {
                if (!focusManager.moveFocus(FocusDirection.Down) && shelfIndex + 1 < content.shelves.size) {
                    listState.animateScrollBy(step, tween(250, easing = FastOutSlowInEasing))
                    focusManager.moveFocus(FocusDirection.Down)
                }
            } else if (shelfIndex > 0) {
                if (!focusManager.moveFocus(FocusDirection.Up)) {
                    listState.animateScrollBy(-step, tween(250, easing = FastOutSlowInEasing))
                    focusManager.moveFocus(FocusDirection.Up)
                }
            } else if (hasHero) {
                val scroll = launch { listState.animateScrollToItem(0) }
                for (attempt in 0 until 25) {
                    if (heroFocus.tryFocus()) break
                    delay(30)
                }
                scroll.join()
            } else {
                tabBarFocus.tryFocus()
            }
        }
    }

    // Abajo desde el héroe: la página sube hasta el pliegue y el foco cae en la primera tarjeta.
    val moveFromHero: () -> Unit = {
        scope.launch {
            if (content.shelves.isEmpty()) return@launch
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            val scroll = launch { listState.animateScrollBy(viewportHeight, tween(350, easing = FastOutSlowInEasing)) }
            for (attempt in 0 until 25) {
                if (firstShelfFocus.tryFocus()) break
                delay(30)
            }
            scroll.join()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when {
            content.isEmpty && content.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DuskSpinner(size = tv.dp(56), color = colors.textSecondary)
            }
            content.isEmpty -> Box(Modifier.fillMaxSize().padding(horizontal = tv.dp(64)), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "Nothing to show yet. Watch something on Plex and it will appear here.",
                    style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.widthIn(max = tv.dp(720)),
                )
            }
            else -> CompositionLocalProvider(
                LocalBringIntoViewSpec provides NoAutoScrollSpec,
                LocalOverscrollConfiguration provides null,
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (hasHero) {
                        item(key = "hero") {
                            HomeHero(
                                items = content.heroItems,
                                imageUrl = imageUrl,
                                heroFocus = heroFocus,
                                tabBarFocus = tabBarFocus,
                                onMoveDown = moveFromHero,
                                onPlayFocusChange = { if (it) focusedShelf = -1 },
                                hasMore = content.shelves.isNotEmpty(),
                                onPlay = onPlay,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    item(key = "top-gap") { Spacer(Modifier.height(tv.dp(if (hasHero) SHELF_TOP_MARGIN_PT else 170))) }
                    itemsIndexed(content.shelves, key = { index, shelf -> "$index|${shelf.id}" }) { index, shelf ->
                        HomeShelfRow(
                            shelf = shelf,
                            shelfIndex = index,
                            imageUrl = imageUrl,
                            firstCardFocus = if (index == 0) firstShelfFocus else null,
                            onVertical = moveVertically,
                            onCardFocused = { focusedShelf = index },
                            onOpen = onOpen,
                        )
                        Spacer(Modifier.height(tv.dp(36)))
                    }
                    item(key = "bottom-gap") { Spacer(Modifier.height(tv.dp(88))) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeShelfRow(
    shelf: HomeShelf,
    shelfIndex: Int,
    imageUrl: (PlexItem, String?, Int, Int) -> String?,
    firstCardFocus: FocusRequester?,
    onVertical: (FocusDirection, Int) -> Unit,
    onCardFocused: () -> Unit,
    onOpen: (PlexItem) -> Unit,
) {
    val tv = DuskTheme.tv
    Column(verticalArrangement = Arrangement.spacedBy(tv.dp(22))) {
        BasicText(
            text = shelf.title,
            style = DuskTheme.type.sectionHeader,
            modifier = Modifier.padding(horizontal = tv.dp(52)),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides RowBringIntoViewSpec) {
            LazyRow(
                // Margen vertical para que el póster elevado y su sombra no queden recortados.
                contentPadding = PaddingValues(horizontal = tv.dp(52), vertical = tv.dp(28)),
                horizontalArrangement = Arrangement.spacedBy(tv.dp(34)),
            ) {
                itemsIndexed(shelf.items, key = { _, item -> item.storageKey }) { cardIndex, item ->
                    PosterCard(
                        imageUrl = imageUrl(item, item.posterPath, POSTER_WIDTH_PT, POSTER_HEIGHT_PT),
                        title = item.posterCardTitle,
                        subtitle = item.standardPosterSubtitle,
                        progress = item.posterProgress,
                        isWatched = item.posterIsWatched,
                        width = tv.dp(POSTER_WIDTH_PT),
                        onClick = { onOpen(item) },
                        focusRequester = if (cardIndex == 0) firstCardFocus else null,
                        onFocused = onCardFocused,
                        keyModifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { onVertical(FocusDirection.Down, shelfIndex); true }
                                Key.DirectionUp -> { onVertical(FocusDirection.Up, shelfIndex); true }
                                else -> false
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Héroe

@Composable
private fun HomeHero(
    items: List<PlexItem>,
    imageUrl: (PlexItem, String?, Int, Int) -> String?,
    heroFocus: FocusRequester,
    tabBarFocus: FocusRequester,
    onMoveDown: () -> Unit,
    onPlayFocusChange: (Boolean) -> Unit,
    hasMore: Boolean,
    onPlay: (PlexItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    val safePage = page.coerceIn(0, items.lastIndex)
    var playFocused by remember { mutableStateOf(false) }

    // Como en tvOS, el héroe solo rota mientras el botón de reproducir tiene el foco.
    LaunchedEffect(safePage, items.size, playFocused) {
        if (playFocused && items.size > 1) {
            delay(HERO_ROTATION_MS)
            page = (safePage + 1) % items.size
        }
    }

    val item = items[safePage]

    Box(modifier.background(colors.background)) {
        // Fondo con el velo `.cinematic` y el fundido `.fullBleed` del original (View+Platform).
        Box(Modifier.fillMaxSize().duskHeroBottomFade(HeroBottomFade.FullBleed)) {
            Crossfade(targetState = item, animationSpec = tween(600), label = "heroBackdrop") { current ->
                AsyncImage(
                    model = imageUrl(current, current.heroBackgroundPath, 1920, 1080),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            HeroBackdropOverlay(HeroOverlay.Cinematic)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = tv.dp(56), end = tv.dp(56), bottom = tv.dp(124))
                .widthIn(max = tv.dp(920)),
            verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
        ) {
            Box(contentAlignment = Alignment.BottomStart) {
                Crossfade(targetState = item, animationSpec = tween(400), label = "heroText") { current ->
                    HeroTextBlock(item = current, imageUrl = imageUrl)
                }
            }
            Box(Modifier.padding(top = tv.dp(10), bottom = tv.dp(8))) {
                DuskGlassButton(
                    text = item.heroPrimaryActionTitle,
                    iconRes = R.drawable.ic_play,
                    onClick = { onPlay(item) },
                    focusRequester = heroFocus,
                    onFocusChange = {
                        playFocused = it
                        onPlayFocusChange(it)
                    },
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> if (safePage > 0) { page = safePage - 1; true } else false
                            Key.DirectionRight -> if (safePage < items.lastIndex) { page = safePage + 1; true } else false
                            // Arriba va a la barra de pestañas (a la pestaña activa).
                            Key.DirectionUp -> tabBarFocus.tryFocus()
                            Key.DirectionDown -> { onMoveDown(); true }
                            else -> false
                        }
                    },
                )
            }
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = tv.dp(56), bottom = tv.dp(78)),
                horizontalArrangement = Arrangement.spacedBy(tv.dp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, _ -> HeroPagerPill(active = index == safePage) }
            }
        }

        if (hasMore) {
            HeroMoreHint(
                visible = playFocused,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = tv.dp(60)),
            )
        }
    }
}

/**
 * "More ⌄" (`HomeTVView`): solo una pista, NO se puede enfocar. Aparece mientras el botón de
 * reproducir tiene el foco y la flecha rebota despacio para invitar a bajar a las filas.
 */
@Composable
private fun HeroMoreHint(visible: Boolean, modifier: Modifier = Modifier) {
    val tv = DuskTheme.tv
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "moreHintAlpha")
    val transition = rememberInfiniteTransition(label = "moreHintBounce")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "moreHintOffset",
    )
    if (alpha <= 0f) return
    Column(
        modifier = modifier.graphicsAlpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = "More",
            style = DuskTheme.type.badge.copy(color = Color.White.copy(alpha = 0.92f)),
        )
        DuskIcon(
            R.drawable.ic_expand_more,
            tv.dp(32),
            Color.White.copy(alpha = 0.92f),
            modifier = Modifier.offset(y = tv.dp(bounce)),
        )
    }
}

private fun Modifier.graphicsAlpha(alpha: Float): Modifier = this.then(
    Modifier.graphicsLayer { this.alpha = alpha },
)

@Composable
private fun HeroPagerPill(active: Boolean) {
    val tv = DuskTheme.tv
    val width by animateDpAsState(if (active) tv.dp(40) else tv.dp(10), label = "pagerPillWidth")
    Box(
        Modifier
            .size(width = width, height = tv.dp(10))
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 0.95f else 0.38f)),
    )
}

@Composable
private fun HeroTextBlock(item: PlexItem, imageUrl: (PlexItem, String?, Int, Int) -> String?) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    var logoFailed by remember(item.storageKey) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(
            tv.dp(if (item.type == PlexMediaType.Movie) 14 else 10),
        ),
    ) {
        val logo = item.clearLogo
        if (logo != null && !logoFailed) {
            AsyncImage(
                model = imageUrl(item, logo, 640, 150),
                contentDescription = item.continueWatchingDisplayTitle,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.size(width = tv.dp(640), height = tv.dp(150)),
            )
        } else {
            BasicText(
                text = item.continueWatchingDisplayTitle,
                style = type.heroTitle.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
                maxLines = 3,
            )
        }
        item.heroEpisodeTitle?.let {
            BasicText(text = it, style = type.heroSubtitle.copy(color = Color.White), maxLines = 2)
        }
        val metadata = item.heroMetadata
        if (metadata.isNotEmpty()) {
            BasicText(
                text = metadata.joinToString(" · "),
                style = type.metadata.copy(color = Color.White.copy(alpha = 0.78f)),
                maxLines = 2,
            )
        }
        item.summary?.let {
            BasicText(
                text = it,
                style = type.body.copy(color = Color.White.copy(alpha = 0.76f), lineHeight = (type.body.fontSize.value * 1.2f).sp),
                maxLines = 3,
                modifier = Modifier.padding(top = tv.dp(6)),
            )
        }
    }
}
