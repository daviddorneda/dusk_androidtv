package app.dusk.androidtv.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.data.plex.PlexMediaType
import app.dusk.androidtv.data.plex.SearchGroup
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.NoAutoScrollSpec
import app.dusk.androidtv.ui.components.PosterCard
import app.dusk.androidtv.ui.components.RowBringIntoViewSpec
import app.dusk.androidtv.ui.components.duskTvFocusScale
import app.dusk.androidtv.ui.theme.DuskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

private const val POSTER_WIDTH_PT = 232
private const val VIDEO_WIDTH_PT = 400
private const val HORIZONTAL_PADDING_PT = 80
private const val PROMPT = "Movies, Shows, Actors..."
private val LETTERS = ('a'..'z').map { it.toString() }
private val SYMBOLS = ('0'..'9').map { it.toString() } + listOf(".", "-", "'", "&", "!")

// Posiciones de los elementos de la lista: 0 consulta, 1 teclado, 2 separador, 3.. grupos de resultados.
private const val FIRST_GROUP_ITEM_INDEX = 3

private fun FocusRequester.tryFocus(): Boolean = try {
    requestFocus()
    true
} catch (e: IllegalStateException) {
    false
}

private suspend fun revealItem(state: LazyListState, itemIndex: Int, topMargin: Float, bottomMargin: Float) {
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
    if (distance != 0f) state.animateScrollBy(distance, tween(300, easing = FastOutSlowInEasing))
}

/**
 * Pestaña Search de tvOS (`SearchRootContent`): el campo con el teclado en una fila (`123`, `SPACE`,
 * a–z y borrar), y debajo un carrusel de pósters por tipo de resultado. El original usa el buscador
 * del sistema; aquí el teclado es propio, con la misma disposición.
 * TODO: Seerr (pedidos), menú de listas por póster, ficha de personas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    firstKeyFocus: FocusRequester,
    tabBarFocus: FocusRequester,
    onScrolledChange: (Boolean) -> Unit,
    onOpen: (PlexItem) -> Unit,
    restoreFocus: Boolean,
    onRestoreHandled: () -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connections by DuskGraph.content.connections.collectAsStateWithLifecycle()
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Al volver desde un detalle se reabre la lista en el grupo del último póster y el foco regresa a él.
    val restoreKey = remember { if (restoreFocus) viewModel.lastFocusedKey else null }
    val restoreRequester = remember { FocusRequester() }
    val restoreGroup = remember { viewModel.lastFocusedGroup }
    val restoreCard = remember { viewModel.lastFocusedCard }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (restoreKey != null) FIRST_GROUP_ITEM_INDEX + restoreGroup else 0,
    )
    val firstCardFocus = remember { FocusRequester() }
    var focusedGroup by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        if (restoreKey != null) {
            for (attempt in 0 until 15) {
                delay(100)
                if (restoreRequester.tryFocus()) break
            }
        }
        onRestoreHandled()
    }

    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 }
    }
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }

    val topMarginPx = with(density) { tv.dp(60).toPx() }
    val bottomMarginPx = with(density) { tv.dp(90).toPx() }
    LaunchedEffect(focusedGroup) {
        if (focusedGroup < 0) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        revealItem(listState, FIRST_GROUP_ITEM_INDEX + focusedGroup, topMarginPx, bottomMarginPx)
    }

    // Arriba/abajo entre grupos; arriba desde el primero vuelve al teclado.
    val moveVertically: (FocusDirection, Int) -> Unit = { direction, groupIndex ->
        scope.launch {
            val step = listState.layoutInfo.viewportSize.height * 0.3f
            if (direction == FocusDirection.Down) {
                if (!focusManager.moveFocus(FocusDirection.Down) && groupIndex + 1 < state.results.size) {
                    listState.animateScrollBy(step, tween(250, easing = FastOutSlowInEasing))
                    focusManager.moveFocus(FocusDirection.Down)
                }
            } else if (groupIndex > 0) {
                if (!focusManager.moveFocus(FocusDirection.Up)) {
                    listState.animateScrollBy(-step, tween(250, easing = FastOutSlowInEasing))
                    focusManager.moveFocus(FocusDirection.Up)
                }
            } else {
                listState.animateScrollToItem(0)
                delay(60)
                firstKeyFocus.tryFocus()
            }
        }
    }

    val keyboardKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> tabBarFocus.tryFocus()
            Key.DirectionDown -> if (state.results.isNotEmpty()) {
                scope.launch {
                    revealItem(listState, FIRST_GROUP_ITEM_INDEX, topMarginPx, bottomMarginPx)
                    firstCardFocus.tryFocus()
                }
                true
            } else {
                false
            }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides NoAutoScrollSpec,
            LocalOverscrollConfiguration provides null,
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "query") {
                    QueryRow(query = state.query, modifier = Modifier.padding(top = tv.dp(128)))
                }
                item(key = "keyboard") {
                    SearchKeyboard(
                        onText = viewModel::append,
                        onBackspace = viewModel::backspace,
                        firstKeyFocus = firstKeyFocus,
                        keyEvents = keyboardKeys,
                    )
                }
                item(key = "divider") {
                    Box(
                        Modifier
                            .padding(top = tv.dp(24), start = tv.dp(HORIZONTAL_PADDING_PT), end = tv.dp(HORIZONTAL_PADDING_PT))
                            .fillMaxWidth()
                            .height(tv.dp(1))
                            .background(colors.textPrimary.copy(alpha = 0.18f)),
                    )
                }

                val noResults = state.results.isEmpty()
                if (noResults) {
                    item(key = "state") {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = tv.dp(520)).padding(horizontal = tv.dp(64)),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                connections.isEmpty() -> SearchEmptyState(
                                    R.drawable.ic_search,
                                    "No server available",
                                    "Search needs a connected Plex server.",
                                )
                                state.isSearching -> DuskSpinner(size = tv.dp(56), color = colors.textSecondary)
                                state.error != null -> Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(tv.dp(24)),
                                ) {
                                    BasicText(
                                        text = state.error ?: "",
                                        style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                                        modifier = Modifier.widthIn(max = tv.dp(720)),
                                    )
                                    DuskGlassButton(text = "Try Again", onClick = viewModel::retry, modifier = Modifier.widthIn(min = tv.dp(320)))
                                }
                                state.hasSearched -> SearchEmptyState(
                                    R.drawable.ic_movie,
                                    "No results found",
                                    "Try a different title, show, or actor.",
                                )
                                else -> SearchEmptyState(
                                    R.drawable.ic_search,
                                    "Search your Plex library",
                                    "Find movies, shows, episodes, and people.",
                                )
                            }
                        }
                    }
                }

                itemsIndexed(state.results, key = { index, group -> "group-$index-${group.id}" }) { groupIndex, group ->
                    SearchGroupRow(
                        group = group,
                        groupIndex = groupIndex,
                        connectionFor = { serverId -> connections[serverId] },
                        firstCardFocus = if (groupIndex == 0) firstCardFocus else null,
                        restoreKey = restoreKey,
                        restoreGroup = restoreGroup,
                        restoreCard = restoreCard,
                        restoreRequester = restoreRequester,
                        onVertical = moveVertically,
                        onCardFocused = { cardIndex, key ->
                            focusedGroup = groupIndex
                            viewModel.lastFocusedKey = key
                            viewModel.lastFocusedGroup = groupIndex
                            viewModel.lastFocusedCard = cardIndex
                        },
                        onOpen = onOpen,
                    )
                    Spacer(Modifier.height(tv.dp(36)))
                }
                item(key = "bottom") { Spacer(Modifier.height(tv.dp(88))) }
            }
        }
    }
}

/** Lupa y consulta (o el texto de ayuda) en gris, grandes y en negrita, como el campo de tvOS. */
@Composable
private fun QueryRow(query: String, modifier: Modifier = Modifier) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    Row(
        modifier = modifier.padding(horizontal = tv.dp(HORIZONTAL_PADDING_PT)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuskIcon(R.drawable.ic_search, tv.dp(52), colors.textSecondary)
        BasicText(
            text = query.ifEmpty { PROMPT },
            style = TextStyle(
                fontSize = tv.sp(44),
                fontWeight = FontWeight.Bold,
                color = if (query.isEmpty()) colors.textSecondary.copy(alpha = 0.6f) else colors.textSecondary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Teclado en una sola fila (`123`, `SPACE`, a–z, borrar). `123` cambia a números y signos y la tecla
 * pasa a llamarse `ABC`.
 */
@Composable
private fun SearchKeyboard(
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    firstKeyFocus: FocusRequester,
    keyEvents: Modifier,
) {
    val tv = DuskTheme.tv
    var symbols by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(top = tv.dp(28), start = tv.dp(HORIZONTAL_PADDING_PT), end = tv.dp(HORIZONTAL_PADDING_PT))
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchKey(
            label = if (symbols) "ABC" else "123",
            filled = true,
            onClick = { symbols = !symbols },
            modifier = keyEvents,
            focusRequester = firstKeyFocus,
        )
        SearchKey(label = "SPACE", filled = true, onClick = { onText(" ") }, modifier = keyEvents)
        (if (symbols) SYMBOLS else LETTERS).forEach { character ->
            SearchKey(label = character, filled = false, onClick = { onText(character) }, modifier = keyEvents)
        }
        SearchKey(iconRes = R.drawable.ic_backspace, filled = false, onClick = onBackspace, modifier = keyEvents)
    }
}

/**
 * Tecla del teclado. Las letras van sueltas en gris claro; `123` y `SPACE` son pastillas grises con
 * texto oscuro. Al recibir el foco la tecla se vuelve blanca con texto oscuro y crece.
 */
@Composable
private fun SearchKey(
    modifier: Modifier = Modifier,
    label: String? = null,
    @DrawableRes iconRes: Int? = null,
    filled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val tv = DuskTheme.tv
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(10))
    val background = when {
        focused -> Color.White
        filled -> Color(0xFF8E8E93).copy(alpha = 0.85f)
        else -> Color.Transparent
    }
    val content = when {
        focused -> Color(0xFF090A0F)
        filled -> Color(0xFF1C1C1E)
        else -> Color.White.copy(alpha = 0.78f)
    }

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duskTvFocusScale(focused, focusedScale = 1.18f)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = tv.dp(if (filled) 14 else 10), vertical = tv.dp(6)),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            BasicText(
                text = label,
                style = TextStyle(
                    fontSize = tv.sp(if (filled) 20 else 32),
                    fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                    color = content,
                ),
            )
        } else if (iconRes != null) {
            DuskIcon(iconRes, tv.dp(36), content)
        }
    }
}

@Composable
private fun SearchEmptyState(@DrawableRes iconRes: Int, title: String, message: String) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tv.dp(14)),
    ) {
        DuskIcon(iconRes, tv.dp(72), colors.textSecondary)
        BasicText(text = title, style = DuskTheme.type.sectionHeader)
        BasicText(
            text = message,
            style = DuskTheme.type.pageSubtitle.copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = tv.dp(720)),
        )
    }
}

/** Un carrusel de pósters por tipo de resultado (`MediaCarousel` + `PosterNavigationCard`). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchGroupRow(
    group: SearchGroup,
    groupIndex: Int,
    connectionFor: (String?) -> app.dusk.androidtv.data.plex.ServerConnection?,
    firstCardFocus: FocusRequester?,
    restoreKey: String?,
    restoreGroup: Int,
    restoreCard: Int,
    restoreRequester: FocusRequester,
    onVertical: (FocusDirection, Int) -> Unit,
    onCardFocused: (Int, String) -> Unit,
    onOpen: (PlexItem) -> Unit,
) {
    val tv = DuskTheme.tv
    val density = LocalDensity.current.density
    val isVideoGroup = group.isAllClips
    val widthPt = if (isVideoGroup) VIDEO_WIDTH_PT else POSTER_WIDTH_PT
    val aspect = if (isVideoGroup) 16f / 9f else 2f / 3f
    val restoresHere = restoreKey != null && restoreGroup == groupIndex
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = if (restoresHere) restoreCard else 0)

    Column(verticalArrangement = Arrangement.spacedBy(tv.dp(22))) {
        BasicText(
            text = group.title,
            style = DuskTheme.type.sectionHeader,
            modifier = Modifier.padding(horizontal = tv.dp(52)),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides RowBringIntoViewSpec) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = tv.dp(52), vertical = tv.dp(28)),
                horizontalArrangement = Arrangement.spacedBy(tv.dp(34)),
            ) {
                itemsIndexed(group.items, key = { _, item -> item.storageKey }) { cardIndex, item ->
                    PosterCard(
                        imageUrl = PlexImages.url(
                            connectionFor(item.serverId),
                            item.posterPath,
                            ceil(widthPt * tv.scale * density).toInt(),
                            ceil(widthPt / aspect * tv.scale * density).toInt(),
                        ),
                        title = if (item.type == PlexMediaType.Season) item.posterCardTitle else item.title,
                        subtitle = item.standardPosterSubtitle,
                        progress = item.posterProgress,
                        isWatched = item.posterIsWatched,
                        width = tv.dp(widthPt),
                        imageAspectRatio = aspect,
                        onClick = { onOpen(item) },
                        focusRequester = when {
                            restoresHere && item.storageKey == restoreKey -> restoreRequester
                            groupIndex == 0 && cardIndex == 0 -> firstCardFocus
                            else -> null
                        },
                        onFocused = { onCardFocused(cardIndex, item.storageKey) },
                        keyModifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { onVertical(FocusDirection.Down, groupIndex); true }
                                Key.DirectionUp -> { onVertical(FocusDirection.Up, groupIndex); true }
                                else -> false
                            }
                        },
                    )
                }
            }
        }
    }
}
