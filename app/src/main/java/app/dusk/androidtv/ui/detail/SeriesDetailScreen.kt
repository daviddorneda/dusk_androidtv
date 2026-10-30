package app.dusk.androidtv.ui.detail

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.MediaText
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskGlassIconButton
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.HeroBackdropOverlay
import app.dusk.androidtv.ui.components.HeroBottomFade
import app.dusk.androidtv.ui.components.HeroOverlay
import app.dusk.androidtv.ui.components.NoAutoScrollSpec
import app.dusk.androidtv.ui.components.RowBringIntoViewSpec
import app.dusk.androidtv.ui.components.duskHeroBottomFade
import app.dusk.androidtv.ui.components.duskTvFocusGlow
import app.dusk.androidtv.ui.components.duskTvFocusScale
import app.dusk.androidtv.ui.nav.Route
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

private const val HORIZONTAL_PADDING_PT = 48
private const val SEASON_HEADER_HEIGHT_PT = 76
private const val EPISODE_META_HEIGHT_PT = 92
private const val EPISODE_CAST_MIN_HEIGHT_PT = 288

private val canBlurImages: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Página de serie de tvOS (`SeriesDetailView`): sirve para serie, temporada y episodio. Un banner
 * sigue al episodio enfocado; debajo, las pastillas de temporada, la fila de episodios, el resumen
 * de la temporada y el reparto del episodio. Elegir un episodio lo reproduce (fase 5).
 * TODO: tráiler y extras, valorar, listas, Watchlist, colecciones, Seerr y versiones.
 */
@Composable
fun SeriesDetailScreen(route: Route.SeriesDetail) {
    val viewModel: SeriesDetailViewModel = viewModel(
        key = "series-${route.serverId}-${route.showRatingKey}-${route.seasonRatingKey}-${route.episodeRatingKey}",
        factory = SeriesDetailViewModel.factory(route),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = DuskTheme.colors
    val tv = DuskTheme.tv

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.show != null && state.seasonDetails != null -> SeriesContent(
                state = state,
                viewModel = viewModel,
                imageUrl = { path, widthPx, heightPx -> PlexImages.url(viewModel.connection, path, widthPx, heightPx) },
            )
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DuskSpinner(size = tv.dp(56), color = colors.textSecondary)
            }
            else -> DetailError(message = state.error ?: "Could not load this show.", onRetry = viewModel::load)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesContent(
    state: SeriesDetailState,
    viewModel: SeriesDetailViewModel,
    imageUrl: (String?, Int, Int) -> String?,
) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    val colors = DuskTheme.colors
    val densityObj = LocalDensity.current
    val density = densityObj.density
    val padding = tv.dp(HORIZONTAL_PADDING_PT)
    val config = LocalConfiguration.current
    val screenHeightPt = config.screenHeightDp / tv.scale
    val screenWidthPt = config.screenWidthDp / tv.scale
    val heroBasePt = minOf(maxOf(screenHeightPt * 0.50f, 500f), 540f)
    val show = state.show!!
    val seasonDetails = state.seasonDetails!!
    val focused = state.focusedEpisode
    val focusedDetails = state.focusedEpisodeDetails?.takeIf { it.ratingKey == focused?.ratingKey }

    val playFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewportHeight by remember { mutableIntStateOf(0) }
    var castTop by remember { mutableFloatStateOf(0f) }
    var castBottom by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(show.ratingKey) {
        delay(150)
        runCatching { playFocus.requestFocus() }
    }

    // El desplazamiento lo decide dónde está el foco: arriba con el banner, la fila y las pastillas
    // (el banner nunca sale de la pantalla al recorrer la fila), y lo justo para ver el reparto.
    val scrollToTop: () -> Unit = {
        scope.launch { scrollState.animateScrollTo(0, tween(350, easing = FastOutSlowInEasing)) }
    }
    val revealCast: () -> Unit = {
        scope.launch {
            val topMargin = with(densityObj) { tv.dp(48).toPx() }
            val bottomMargin = with(densityObj) { tv.dp(40).toPx() }
            val current = scrollState.value.toFloat()
            val target = when {
                castTop < current + topMargin -> castTop - topMargin
                castBottom > current + viewportHeight - bottomMargin ->
                    minOf(castBottom - viewportHeight + bottomMargin, castTop - topMargin)
                else -> current
            }
            scrollState.animateScrollTo(
                target.coerceIn(0f, scrollState.maxValue.toFloat()).toInt(),
                tween(350, easing = FastOutSlowInEasing),
            )
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollSpec) {
        Column(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(scrollState),
        ) {
            // ---- Banner
            Box(Modifier.fillMaxWidth().height(tv.dp(heroBasePt))) {
                val stillPath = focusedDetails?.thumb ?: focused?.thumb
                val bannerPath = focusedDetails?.thumb ?: focusedDetails?.art ?: focused?.thumb ?: focused?.art
                    ?: seasonDetails.art ?: show.art
                // El fotograma de un episodio sin ver se desenfoca para no destripar nada. Donde el
                // sistema no puede desenfocar (antes de Android 12), se usa el fondo de la serie.
                val isUnwatchedStill = focused != null && !focused.isWatchedEpisode &&
                    bannerPath != null && bannerPath == stillPath
                val effectivePath = if (isUnwatchedStill && !canBlurImages) seasonDetails.art ?: show.art else bannerPath
                val blur = isUnwatchedStill && canBlurImages
                val bannerUrl = imageUrl(
                    effectivePath,
                    ceil(screenWidthPt * tv.scale * density).toInt(),
                    ceil(heroBasePt * tv.scale * density).toInt(),
                )

                Box(Modifier.fillMaxSize().duskHeroBottomFade(HeroBottomFade.Standard)) {
                    Crossfade(targetState = bannerUrl to blur, animationSpec = tween(400), label = "seriesBackdrop") { (url, blurred) ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (blurred) Modifier.blur(tv.dp(40), BlurredEdgeTreatment.Rectangle) else Modifier),
                        )
                    }
                    HeroBackdropOverlay(HeroOverlay.Standard)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = padding, end = padding, top = tv.dp(80), bottom = tv.dp(40)),
                    verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(tv.dp(8))) {
                        SeriesTitle(
                            title = show.title,
                            logoPath = show.clearLogo,
                            imageUrl = imageUrl,
                            maxLogoWidthPt = screenWidthPt * 0.45f,
                        )
                        BasicText(
                            text = focused?.title ?: "",
                            style = type.heroSubtitle.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Caja de altura fija: el banner no salta al recorrer los episodios.
                        Column(
                            modifier = Modifier.height(tv.dp(EPISODE_META_HEIGHT_PT)),
                            verticalArrangement = Arrangement.spacedBy(tv.dp(4)),
                        ) {
                            focused?.let { episode ->
                                episodeMetaLine(episode)?.let {
                                    BasicText(
                                        text = it,
                                        style = type.metadata.copy(color = Color.White.copy(alpha = 0.78f)),
                                        maxLines = 1,
                                    )
                                }
                                (episode.summary ?: focusedDetails?.summary)?.let { summary ->
                                    BasicText(
                                        text = summary,
                                        style = type.caption.copy(
                                            color = Color.White.copy(alpha = 0.78f),
                                            lineHeight = (type.caption.fontSize.value * 1.15f).sp,
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = tv.dp(720)),
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(tv.dp(22)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DuskGlassButton(
                            text = if (focused?.isPartiallyWatchedEpisode == true) "Resume" else "Play",
                            iconRes = R.drawable.ic_play,
                            onClick = { /* Reproductor: fase 5 */ },
                            focusRequester = playFocus,
                            onFocusChange = { if (it) scrollToTop() },
                            modifier = Modifier.widthIn(min = tv.dp(220)),
                        )
                        DuskGlassIconButton(
                            iconRes = if (state.isSeasonWatched) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            onClick = viewModel::toggleSeasonWatched,
                            onFocusChange = { if (it) scrollToTop() },
                        )
                    }
                }
            }

            // ---- Pastillas de temporada o título "Episodes" (misma altura para que nada salte)
            Box(
                modifier = Modifier
                    .padding(horizontal = padding)
                    .padding(top = tv.dp(16))
                    .fillMaxWidth()
                    .height(tv.dp(SEASON_HEADER_HEIGHT_PT)),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.seasonPills.isEmpty()) {
                    BasicText(text = "Episodes", style = type.sectionHeader)
                } else {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides RowBringIntoViewSpec) {
                        LazyRow(
                            contentPadding = PaddingValues(vertical = tv.dp(14)),
                            horizontalArrangement = Arrangement.spacedBy(tv.dp(18)),
                        ) {
                            items(state.seasonPills, key = { it.ratingKey }) { season ->
                                SeasonPill(
                                    season = season,
                                    isSelected = season.ratingKey == state.selectedSeasonKey,
                                    onClick = { viewModel.selectSeason(season.ratingKey) },
                                    onFocused = scrollToTop,
                                )
                            }
                        }
                    }
                }
            }

            // ---- Fila de episodios
            val contentWidthPt = maxOf(screenWidthPt - HORIZONTAL_PADDING_PT * 2, 280f)
            val artworkWidthPt = minOf(maxOf(contentWidthPt * 0.44f, 240f), 360f)
            val anchorIndex = state.episodes.indexOfFirst { it.ratingKey == state.anchorEpisodeKey }.coerceAtLeast(0)
            key(state.selectedSeasonKey) {
                val rowState = rememberLazyListState(initialFirstVisibleItemIndex = anchorIndex)
                CompositionLocalProvider(LocalBringIntoViewSpec provides RowBringIntoViewSpec) {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier
                            .padding(top = tv.dp(8), bottom = tv.dp(24))
                            .alpha(if (state.isSwitchingSeason) 0.5f else 1f),
                        contentPadding = PaddingValues(horizontal = padding, vertical = tv.dp(28)),
                        horizontalArrangement = Arrangement.spacedBy(tv.dp(28)),
                    ) {
                        items(state.episodes, key = { it.ratingKey }) { episode ->
                            EpisodeCard(
                                episode = episode,
                                imageUrl = imageUrl(
                                    episode.thumb ?: episode.grandparentThumb,
                                    ceil(artworkWidthPt * tv.scale * density).toInt(),
                                    ceil(artworkWidthPt * 9f / 16f * tv.scale * density).toInt(),
                                ),
                                artworkWidthPt = artworkWidthPt,
                                onFocused = {
                                    scrollToTop()
                                    viewModel.onEpisodeFocused(episode.ratingKey)
                                },
                            )
                        }
                    }
                }
            }

            // ---- Resumen de la temporada
            seasonDetails.summary?.let { summary ->
                BasicText(
                    text = summary,
                    style = type.body.copy(
                        color = colors.textPrimary.copy(alpha = 0.76f),
                        lineHeight = (type.body.fontSize.value * 1.25f).sp,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = padding, end = padding, top = tv.dp(32)),
                )
            }

            // ---- Reparto del episodio enfocado (altura mínima reservada para que nada salte)
            Box(
                modifier = Modifier
                    .padding(top = tv.dp(8), bottom = tv.dp(56))
                    .fillMaxWidth()
                    .heightIn(min = tv.dp(EPISODE_CAST_MIN_HEIGHT_PT))
                    .onGloballyPositioned { coordinates ->
                        castTop = coordinates.positionInParent().y
                        castBottom = castTop + coordinates.size.height
                    },
            ) {
                val roles = focusedDetails?.roles.orEmpty()
                if (roles.isNotEmpty()) {
                    CastSection(
                        roles = roles.take(20),
                        imageUrl = imageUrl,
                        onCardFocused = revealCast,
                        title = "Episode Cast",
                    )
                }
            }
        }
    }
}

/** Logo de la serie (128 pt de alto) o, si no hay, su nombre en una sola línea. */
@Composable
private fun SeriesTitle(title: String, logoPath: String?, imageUrl: (String?, Int, Int) -> String?, maxLogoWidthPt: Float) {
    val tv = DuskTheme.tv
    val density = LocalDensity.current.density
    var logoFailed by remember(title, logoPath) { mutableStateOf(false) }
    if (logoPath != null && !logoFailed) {
        AsyncImage(
            model = imageUrl(
                logoPath,
                ceil(maxLogoWidthPt * tv.scale * density).toInt(),
                ceil(128 * tv.scale * density).toInt(),
            ),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier.size(width = tv.dp(maxLogoWidthPt), height = tv.dp(128)),
        )
    } else {
        BasicText(
            text = title,
            style = DuskTheme.type.heroTitle.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Episode 3 · 42 min · Oct 5, 2024" (`episodeMetaLine`). */
private fun episodeMetaLine(episode: PlexItem): String? {
    val label = MediaText.seasonEpisodeLabel(season = null, episode = episode.index, separator = " ")
    val duration = shortDuration(episode.duration)
    val airDate = airDate(episode.originallyAvailableAt)
    return listOfNotNull(label, listOfNotNull(duration, airDate).joinToString(" · ").takeIf { it.isNotEmpty() })
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() }
}

private fun shortDuration(milliseconds: Int?): String? {
    if (milliseconds == null || milliseconds <= 0) return null
    val totalMinutes = milliseconds / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "$totalMinutes min"
}

private fun airDate(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    return try {
        LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        null
    }
}

/**
 * Pastilla de temporada (`SeriesSeasonPillLabel`): cápsula "glass"; la temporada elegida va más
 * brillante y en negrita, el resto atenuado, y el foco siempre con contraste completo.
 */
@Composable
private fun SeasonPill(season: PlexItem, isSelected: Boolean, onClick: () -> Unit, onFocused: () -> Unit) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "seasonPillScale")
    val container = if (focused) Color.White else Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.60f)
    val contentColor = when {
        focused -> Color(0xFF090A0F)
        colors.isDark -> Color(0xFFF2F2F7)
        else -> Color(0xFF1C1C1E)
    }
    val emphasis = if (isSelected || focused) 1f else 0.6f
    val unwatched = ((season.leafCount ?: 0) - (season.viewedLeafCount ?: 0)).takeIf { it > 0 }

    Row(
        modifier = Modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = tv.dp(30), vertical = tv.dp(16)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = season.title,
            style = DuskTheme.type.cardTitle.copy(
                color = contentColor.copy(alpha = emphasis),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
        when {
            season.posterIsWatched -> DuskIcon(R.drawable.ic_check, tv.dp(22), contentColor.copy(alpha = 0.7f))
            unwatched != null -> BasicText(
                text = unwatched.toString(),
                style = DuskTheme.type.glyphSmall.copy(color = contentColor.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Tarjeta de episodio (`SeriesEpisodeCard`): fotograma 16:9 con esquinas de 16 pt, barra de progreso,
 * título y "Season 1 · Episode 3". Como en el original, la escala 1.05 afecta a toda la tarjeta y el
 * resplandor blanco rodea solo el fotograma.
 */
@Composable
private fun EpisodeCard(episode: PlexItem, imageUrl: String?, artworkWidthPt: Float, onFocused: () -> Unit) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(16))
    val width = tv.dp(artworkWidthPt)
    val watched = episode.isWatchedEpisode
    val progress = episode.posterProgress
    // Un fotograma de episodio sin ver se desenfoca (donde el sistema puede).
    val blurStill = !watched && episode.thumb != null && canBlurImages

    Column(
        modifier = Modifier.width(width).duskTvFocusScale(focused),
        verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = tv.dp(artworkWidthPt * 9f / 16f))
                .duskTvFocusGlow(focused, shape, colors.surface)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clip(shape)
                .background(colors.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Reproducir: fase 5 */ },
                ),
            contentAlignment = Alignment.Center,
        ) {
            DuskIcon(R.drawable.ic_tv, tv.dp(52), colors.textSecondary)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurStill) Modifier.blur(tv.dp(14), BlurredEdgeTreatment.Rectangle) else Modifier),
                )
            }
            if (progress != null && progress > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = tv.dp(8), vertical = tv.dp(8)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(tv.dp(6))
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                                .background(Color.White),
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(tv.dp(4))) {
            BasicText(
                text = episode.title,
                style = type.cardTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width),
            )
            val subtitle = MediaText.seasonEpisodeLabel(episode.parentIndex, episode.index)
            if (subtitle != null || watched) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tv.dp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(width),
                ) {
                    if (subtitle != null) {
                        BasicText(
                            text = subtitle,
                            style = type.cardSubtitle.copy(color = colors.textPrimary.copy(alpha = 0.68f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (watched) DuskIcon(R.drawable.ic_check, tv.dp(22), colors.textPrimary.copy(alpha = 0.68f))
                }
            }
        }
    }
}
