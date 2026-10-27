package app.dusk.androidtv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.HomeShelf
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.data.plex.PlexMediaType
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.PosterCard
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.ceil

private const val HERO_ROTATION_MS = 8_000L

/**
 * Home de tvOS (`HomeTVView`): héroe cinemático a pantalla completa con lo que estás viendo
 * y, debajo, las filas de pósters. Todos los servidores conectados se muestran fusionados.
 * TODO: Live TV, Watchlist, estantes personalizados y navegación al detalle (fase 4).
 */
@Composable
fun HomeScreen(
    listState: LazyListState,
    heroFocus: FocusRequester,
    tabBarFocus: FocusRequester,
    onPlay: (PlexItem) -> Unit,
    onOpen: (PlexItem) -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val content by DuskGraph.content.home.collectAsStateWithLifecycle()
    val connections by DuskGraph.content.connections.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density

    // Las medidas del diseño están en puntos de tvOS; la imagen se pide en píxeles reales.
    val imageUrl: (PlexItem, String?, Int, Int) -> String? = { item, path, widthPt, heightPt ->
        PlexImages.url(
            connection = connections[item.serverId],
            path = path,
            width = ceil(widthPt * tv.scale * density).toInt(),
            height = ceil(heightPt * tv.scale * density).toInt(),
        )
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
            else -> {
                val hasHero = content.heroItems.isNotEmpty()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (hasHero) {
                        item(key = "hero") {
                            HomeHero(
                                items = content.heroItems,
                                imageUrl = imageUrl,
                                heroFocus = heroFocus,
                                tabBarFocus = tabBarFocus,
                                hasMore = content.shelves.isNotEmpty(),
                                onPlay = onPlay,
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    item(key = "top-gap") { Spacer(Modifier.height(tv.dp(if (hasHero) 84 else 170))) }
                    itemsIndexed(content.shelves, key = { index, shelf -> "$index|${shelf.id}" }) { _, shelf ->
                        HomeShelfRow(shelf = shelf, imageUrl = imageUrl, onOpen = onOpen)
                        Spacer(Modifier.height(tv.dp(36)))
                    }
                    item(key = "bottom-gap") { Spacer(Modifier.height(tv.dp(88))) }
                }
            }
        }
    }
}

@Composable
private fun HomeShelfRow(
    shelf: HomeShelf,
    imageUrl: (PlexItem, String?, Int, Int) -> String?,
    onOpen: (PlexItem) -> Unit,
) {
    val tv = DuskTheme.tv
    Column(verticalArrangement = Arrangement.spacedBy(tv.dp(22))) {
        BasicText(
            text = shelf.title,
            style = DuskTheme.type.sectionHeader,
            modifier = Modifier.padding(horizontal = tv.dp(52)),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = tv.dp(52), vertical = tv.dp(20)),
            horizontalArrangement = Arrangement.spacedBy(tv.dp(34)),
        ) {
            items(shelf.items, key = { it.storageKey }) { item ->
                PosterCard(
                    imageUrl = imageUrl(item, item.posterPath, POSTER_WIDTH_PT, POSTER_HEIGHT_PT),
                    title = item.posterCardTitle,
                    subtitle = item.standardPosterSubtitle,
                    progress = item.posterProgress,
                    isWatched = item.posterIsWatched,
                    width = tv.dp(POSTER_WIDTH_PT),
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

private const val POSTER_WIDTH_PT = 232
private const val POSTER_HEIGHT_PT = 348

// ---------------------------------------------------------------- Héroe

@Composable
private fun HomeHero(
    items: List<PlexItem>,
    imageUrl: (PlexItem, String?, Int, Int) -> String?,
    heroFocus: FocusRequester,
    tabBarFocus: FocusRequester,
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
        Crossfade(targetState = item, animationSpec = tween(600), label = "heroBackdrop") { current ->
            AsyncImage(
                model = imageUrl(current, current.heroBackgroundPath, 1920, 1080),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Velo cinemático: oscurece la izquierda (texto) y funde el pie con el fondo de la página.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.70f),
                    0.55f to Color.Black.copy(alpha = 0.25f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.20f),
                    0.80f to Color.Black.copy(alpha = 0.78f),
                    1f to colors.background,
                ),
            ),
        )

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
                    onFocusChange = { playFocused = it },
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> if (safePage > 0) { page = safePage - 1; true } else false
                            Key.DirectionRight -> if (safePage < items.lastIndex) { page = safePage + 1; true } else false
                            // Arriba va a la pestaña activa, no a la más cercana (que cambiaría de pestaña).
                            Key.DirectionUp -> runCatching { tabBarFocus.requestFocus() }.isSuccess
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
            BasicText(
                text = "More",
                style = DuskTheme.type.caption.copy(color = Color.White.copy(alpha = 0.7f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = tv.dp(66)),
            )
        }
    }
}

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
