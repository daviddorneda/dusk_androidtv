package app.dusk.androidtv.ui.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.LibraryKind
import app.dusk.androidtv.data.plex.LibrarySortOption
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.NoAutoScrollSpec
import app.dusk.androidtv.ui.components.PosterCard
import app.dusk.androidtv.ui.theme.DuskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

// Métricas de la rejilla en tvOS (`DuskPosterMetrics`), en puntos.
private const val GRID_HORIZONTAL_PADDING_PT = 48
private const val GRID_SPACING_PT = 40
private const val GRID_ROW_SPACING_PT = 44
private const val GRID_PREFERRED_WIDTH_PT = 196
private const val VIDEO_GRID_PREFERRED_WIDTH_PT = 300
private const val TOP_SPACER_PT = 130

private fun FocusRequester.tryFocus(): Boolean = try {
    requestFocus()
    true
} catch (e: IllegalStateException) {
    false
}

/** `AdaptivePosterGridLayout.make`: nº de columnas y ancho de póster para el ancho disponible. */
private fun gridLayout(containerWidthPt: Float, preferredWidthPt: Float): Pair<Int, Float> {
    val available = maxOf(containerWidthPt - GRID_HORIZONTAL_PADDING_PT * 2, preferredWidthPt)
    val raw = ((available + GRID_SPACING_PT) / (preferredWidthPt + GRID_SPACING_PT)).toInt()
    val columns = maxOf(raw, 2)
    val posterWidth = floor((available - (columns - 1) * GRID_SPACING_PT) / columns)
    return columns to posterWidth
}

private suspend fun revealRow(state: LazyListState, itemIndex: Int, topMargin: Float, bottomMargin: Float) {
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
 * Pestañas Movies / TV Shows / Videos: rejilla de la biblioteca con los chips de exploración
 * (Género, "Unwatched" y Orden), como `LibraryItemsView` en tvOS.
 * TODO: índice A–Z, hoja de filtros, colecciones y recomendaciones por biblioteca.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridScreen(
    kind: LibraryKind,
    firstChipFocus: FocusRequester,
    tabBarFocus: FocusRequester,
    onScrolledChange: (Boolean) -> Unit,
    onOpen: (PlexItem) -> Unit,
) {
    val viewModel: LibraryGridViewModel = viewModel(
        key = "library-${kind.name}",
        factory = LibraryGridViewModel.factory(kind),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val density = LocalDensity.current
    val connections by DuskGraph.content.connections.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val isVideo = kind == LibraryKind.Video
    val screenWidthPt = LocalConfiguration.current.screenWidthDp / tv.scale
    val (columns, posterWidthPt) = gridLayout(
        screenWidthPt,
        if (isVideo) VIDEO_GRID_PREFERRED_WIDTH_PT.toFloat() else GRID_PREFERRED_WIDTH_PT.toFloat(),
    )
    val aspect = if (isVideo) 16f / 9f else 2f / 3f
    val posterHeightPt = posterWidthPt / aspect
    val rows = remember(state.items, columns) { state.items.chunked(columns) }

    val firstCardFocus = remember { FocusRequester() }
    var focusedRow by remember { mutableIntStateOf(-1) }
    val firstRowItemIndex = 2 // 0: hueco superior, 1: chips

    // La barra de pestañas se oculta al bajar por la rejilla.
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 }
    }
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }

    // Paginación: al acercarse al final se pide la siguiente página.
    LaunchedEffect(focusedRow, rows.size, state.hasMore) {
        if (state.hasMore && focusedRow >= 0 && focusedRow >= rows.size - 3) viewModel.loadMore()
    }

    // Desplazamiento vertical suave decidido por la fila enfocada (no por el foco de Compose).
    val topMarginPx = with(density) { tv.dp(60).toPx() }
    val bottomMarginPx = with(density) { tv.dp(90).toPx() }
    LaunchedEffect(focusedRow) {
        if (focusedRow < 0) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        revealRow(listState, firstRowItemIndex + focusedRow, topMarginPx, bottomMarginPx)
    }

    val moveVertically: (FocusDirection, Int) -> Unit = { direction, rowIndex ->
        scope.launch {
            val step = listState.layoutInfo.viewportSize.height * 0.3f
            if (direction == FocusDirection.Down) {
                if (!focusManager.moveFocus(FocusDirection.Down) && rowIndex + 1 < rows.size) {
                    listState.animateScrollBy(step, tween(250, easing = FastOutSlowInEasing))
                    focusManager.moveFocus(FocusDirection.Down)
                }
            } else if (rowIndex > 0) {
                if (!focusManager.moveFocus(FocusDirection.Up)) {
                    listState.animateScrollBy(-step, tween(250, easing = FastOutSlowInEasing))
                    focusManager.moveFocus(FocusDirection.Up)
                }
            } else {
                // Arriba desde la primera fila: a los chips.
                listState.animateScrollToItem(0)
                delay(60)
                firstChipFocus.tryFocus()
            }
        }
    }

    val chipKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> if (rows.isNotEmpty()) firstCardFocus.tryFocus() else false
            Key.DirectionUp -> tabBarFocus.tryFocus()
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DuskSpinner(size = tv.dp(56), color = colors.textSecondary)
            }
            state.error != null && state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(horizontal = tv.dp(64)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(tv.dp(24))) {
                    BasicText(
                        text = state.error ?: "",
                        style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.widthIn(max = tv.dp(720)),
                    )
                    DuskGlassButton(text = "Try Again", onClick = viewModel::reload, modifier = Modifier.widthIn(min = tv.dp(320)))
                }
            }
            else -> CompositionLocalProvider(
                LocalBringIntoViewSpec provides NoAutoScrollSpec,
                LocalOverscrollConfiguration provides null,
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "top") { Spacer(Modifier.height(tv.dp(TOP_SPACER_PT))) }
                    item(key = "controls") {
                        BrowseControlsRow(
                            state = state,
                            kind = kind,
                            viewModel = viewModel,
                            firstChipFocus = firstChipFocus,
                            chipKeys = chipKeys,
                            onChipFocused = { scope.launch { listState.animateScrollToItem(0) } },
                        )
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(top = tv.dp(80)), contentAlignment = Alignment.Center) {
                                BasicText(
                                    text = "Nothing matches these filters.",
                                    style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                                )
                            }
                        }
                    }
                    itemsIndexed(rows, key = { index, row -> "row-$index-${row.first().storageKey}" }) { rowIndex, row ->
                        Row(
                            modifier = Modifier.padding(
                                start = tv.dp(GRID_HORIZONTAL_PADDING_PT),
                                end = tv.dp(GRID_HORIZONTAL_PADDING_PT),
                                top = tv.dp(if (rowIndex == 0) 32 else 0),
                                bottom = tv.dp(if (rowIndex == rows.lastIndex) 32 else GRID_ROW_SPACING_PT),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(tv.dp(GRID_SPACING_PT)),
                        ) {
                            row.forEachIndexed { columnIndex, item ->
                                val connection = connections[item.serverId]
                                PosterCard(
                                    imageUrl = PlexImages.url(
                                        connection,
                                        item.posterPath,
                                        ceil(posterWidthPt * tv.scale * density.density).toInt(),
                                        ceil(posterHeightPt * tv.scale * density.density).toInt(),
                                    ),
                                    title = item.posterCardTitle,
                                    subtitle = item.standardPosterSubtitle,
                                    progress = item.posterProgress,
                                    isWatched = item.posterIsWatched,
                                    width = tv.dp(posterWidthPt),
                                    imageAspectRatio = aspect,
                                    onClick = { onOpen(item) },
                                    focusRequester = if (rowIndex == 0 && columnIndex == 0) firstCardFocus else null,
                                    onFocused = { focusedRow = rowIndex },
                                    keyModifier = Modifier.onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.DirectionDown -> { moveVertically(FocusDirection.Down, rowIndex); true }
                                            Key.DirectionUp -> { moveVertically(FocusDirection.Up, rowIndex); true }
                                            else -> false
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (state.isLoadingMore) {
                        item(key = "loading-more") {
                            Box(Modifier.fillMaxWidth().padding(vertical = tv.dp(20)), contentAlignment = Alignment.Center) {
                                DuskSpinner(size = tv.dp(36), color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseControlsRow(
    state: LibraryGridState,
    kind: LibraryKind,
    viewModel: LibraryGridViewModel,
    firstChipFocus: FocusRequester,
    chipKeys: Modifier,
    onChipFocused: () -> Unit,
) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    val sortOptions = remember(kind) { LibrarySortOption.optionsFor(kind) }
    val showsGenre = state.genres.size > 1
    // El primer chip visible recibe el foco al bajar desde la barra.
    val genreFocus = if (showsGenre) firstChipFocus else null
    val unwatchedFocus = if (showsGenre) null else firstChipFocus

    Row(
        modifier = Modifier.padding(horizontal = tv.dp(48)).padding(top = tv.dp(8), bottom = tv.dp(4)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsGenre) {
            MenuChip(
                iconRes = R.drawable.ic_filter_list,
                label = state.genre ?: "All Genres",
                isActive = state.genre != null,
                options = listOf<String?>(null) + state.genres,
                isSelected = { it == state.genre },
                optionTitle = { it ?: "All Genres" },
                onSelect = viewModel::setGenre,
                focusRequester = genreFocus,
                modifier = chipKeys,
            )
        }
        BrowseChip(
            onClick = { viewModel.setUnwatchedOnly(!state.unwatchedOnly) },
            isOn = state.unwatchedOnly,
            focusRequester = unwatchedFocus,
            modifier = chipKeys,
        ) { content, secondary ->
            DuskIcon(
                if (state.unwatchedOnly) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked,
                tv.dp(30),
                if (state.unwatchedOnly) content else secondary,
            )
            BasicText(text = "Unwatched", style = type.buttonLabel.copy(color = content), maxLines = 1)
        }
        MenuChip(
            iconRes = R.drawable.ic_sort,
            label = state.sort.title,
            isActive = state.sort != LibrarySortOption.TitleAscending,
            options = sortOptions,
            isSelected = { it == state.sort },
            optionTitle = { it.title },
            onSelect = viewModel::setSort,
            modifier = chipKeys,
        )
    }
}
