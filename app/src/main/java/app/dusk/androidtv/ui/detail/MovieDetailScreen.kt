package app.dusk.androidtv.ui.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.MediaDetails
import app.dusk.androidtv.data.plex.PlexImages
import app.dusk.androidtv.data.plex.PlexRole
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskGlassIconButton
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.DuskTextButton
import app.dusk.androidtv.ui.components.HeroBackdropOverlay
import app.dusk.androidtv.ui.components.HeroBottomFade
import app.dusk.androidtv.ui.components.HeroOverlay
import app.dusk.androidtv.ui.components.duskHeroBottomFade
import app.dusk.androidtv.ui.components.NoAutoScrollSpec
import app.dusk.androidtv.ui.components.RowBringIntoViewSpec
import app.dusk.androidtv.ui.components.duskTvFocusGlow
import app.dusk.androidtv.ui.components.duskTvFocusScale
import app.dusk.androidtv.ui.nav.Route
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.ceil

private const val DETAIL_HORIZONTAL_PADDING_PT = 48

/**
 * Detalle de película de tvOS (`MovieDetailView`): héroe con fondo y logo, línea de metadatos,
 * acciones, sinopsis, reparto y datos del medio.
 * TODO: tráiler y extras, valoraciones, descarga de subtítulos, valorar, listas, Watchlist,
 * colecciones, versiones, y reproducir (fase 5).
 */
@Composable
fun MovieDetailScreen(route: Route.MovieDetail) {
    val viewModel: MovieDetailViewModel = viewModel(
        key = "movie-${route.serverId}-${route.ratingKey}",
        factory = MovieDetailViewModel.factory(route),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = DuskTheme.colors
    val tv = DuskTheme.tv

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val details = state.details
        when {
            details != null -> MovieDetailContent(
                details = details,
                imageUrl = { path, widthPt, heightPt ->
                    PlexImages.url(viewModel.connection, path, widthPt, heightPt)
                },
                onToggleWatched = viewModel::toggleWatched,
            )
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DuskSpinner(size = tv.dp(56), color = colors.textSecondary)
            }
            else -> DetailError(message = state.error ?: "Could not load this movie.", onRetry = viewModel::load)
        }
    }
}

@Composable
private fun DetailError(message: String, onRetry: () -> Unit) {
    val tv = DuskTheme.tv
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(horizontal = tv.dp(64)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(tv.dp(24))) {
            BasicText(
                text = message,
                style = DuskTheme.type.pageSubtitle.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(720)),
            )
            DuskGlassButton(text = "Try Again", onClick = onRetry, focusRequester = focus, modifier = Modifier.widthIn(min = tv.dp(320)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieDetailContent(
    details: MediaDetails,
    imageUrl: (String?, Int, Int) -> String?,
    onToggleWatched: () -> Unit,
) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    val padding = tv.dp(DETAIL_HORIZONTAL_PADDING_PT)
    val screenHeightPt = LocalConfiguration.current.screenHeightDp / tv.scale
    val heroBasePt = minOf(maxOf(screenHeightPt * 0.72f, 520f), 760f)
    val densityObj = LocalDensity.current
    val density = densityObj.density
    val playFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewportHeight by remember { mutableIntStateOf(0) }
    var castTop by remember { mutableFloatStateOf(0f) }
    var castBottom by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(details.ratingKey) {
        kotlinx.coroutines.delay(150)
        runCatching { playFocus.requestFocus() }
    }

    // El desplazamiento lo decide dónde está el foco, no el foco de Compose (que movía la página
    // sola al llegar a "Play"): arriba del todo con el héroe, y lo justo para ver el reparto.
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
        // ---- Héroe
        Box(Modifier.fillMaxWidth().height(tv.dp(heroBasePt))) {
            Box(Modifier.fillMaxSize().duskHeroBottomFade(HeroBottomFade.Standard)) {
                AsyncImage(
                    model = imageUrl(
                        details.art,
                        ceil(1920 * tv.scale * density).toInt(),
                        ceil(heroBasePt * tv.scale * density).toInt(),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
                    DetailTitle(details = details, imageUrl = imageUrl)
                    Column(verticalArrangement = Arrangement.spacedBy(tv.dp(6))) {
                        details.tagline?.let {
                            BasicText(text = it, style = type.metadata.copy(color = Color.White.copy(alpha = 0.78f)))
                        }
                        details.genreText?.let {
                            BasicText(text = it, style = type.caption.copy(color = Color.White.copy(alpha = 0.72f)))
                        }
                        details.directorText?.let {
                            BasicText(text = "Directed by $it", style = type.caption.copy(color = Color.White.copy(alpha = 0.72f)))
                        }
                        details.studio?.let {
                            BasicText(text = it, style = type.caption.copy(color = Color.White.copy(alpha = 0.72f)))
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tv.dp(22)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DuskGlassButton(
                        text = details.formattedResume?.let { "Resume from $it" } ?: "Play",
                        iconRes = R.drawable.ic_play,
                        onClick = { /* Reproductor: fase 5 */ },
                        focusRequester = playFocus,
                        onFocusChange = { if (it) scrollToTop() },
                        modifier = Modifier.widthIn(min = tv.dp(220)),
                    )
                    DuskGlassIconButton(
                        iconRes = if (details.isWatched) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                        onClick = onToggleWatched,
                        onFocusChange = { if (it) scrollToTop() },
                    )
                }
            }
        }

        // ---- Sinopsis
        details.summary?.let { summary ->
            Column(
                modifier = Modifier.padding(start = padding, end = padding, top = tv.dp(40)),
                verticalArrangement = Arrangement.spacedBy(tv.dp(8)),
            ) {
                BasicText(text = "Synopsis", style = type.sectionHeader)
                BasicText(
                    text = summary,
                    style = type.body.copy(
                        color = DuskTheme.colors.textPrimary.copy(alpha = 0.76f),
                        lineHeight = (type.body.fontSize.value * 1.25f).sp,
                    ),
                )
            }
        }

        // ---- Reparto
        if (details.roles.isNotEmpty()) {
            CastSection(
                roles = details.roles.take(20),
                imageUrl = imageUrl,
                onCardFocused = revealCast,
                modifier = Modifier
                    .padding(top = tv.dp(40))
                    .onGloballyPositioned { coordinates ->
                        castTop = coordinates.positionInParent().y
                        castBottom = castTop + coordinates.size.height
                    },
            )
        }

        // ---- Datos del medio
        details.mediaInfo?.let { info ->
            Column(
                modifier = Modifier.padding(start = padding, end = padding, top = tv.dp(40), bottom = tv.dp(56)),
                verticalArrangement = Arrangement.spacedBy(tv.dp(8)),
            ) {
                BasicText(text = "Media", style = type.sectionHeader)
                BasicText(
                    text = info,
                    style = type.metadata.copy(
                        color = DuskTheme.colors.textPrimary.copy(alpha = 0.76f),
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
    }
}

/** Logo de título (clear-logo, 124 pt de alto) o, si no hay, el título en texto. */
@Composable
private fun DetailTitle(details: MediaDetails, imageUrl: (String?, Int, Int) -> String?) {
    val tv = DuskTheme.tv
    val density = LocalDensity.current.density
    var logoFailed by remember(details.ratingKey) { mutableStateOf(false) }
    val logo = details.clearLogo
    if (logo != null && !logoFailed) {
        AsyncImage(
            model = imageUrl(
                logo,
                ceil(560 * tv.scale * density).toInt(),
                ceil(124 * tv.scale * density).toInt(),
            ),
            contentDescription = details.title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier.size(width = tv.dp(560), height = tv.dp(124)),
        )
    } else {
        BasicText(
            text = details.title,
            style = DuskTheme.type.heroTitle.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CastSection(
    roles: List<PlexRole>,
    imageUrl: (String?, Int, Int) -> String?,
    onCardFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = DuskTheme.tv
    val padding = tv.dp(DETAIL_HORIZONTAL_PADDING_PT)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(tv.dp(10))) {
        BasicText(
            text = "Cast",
            style = DuskTheme.type.sectionHeader,
            modifier = Modifier.padding(horizontal = padding),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides RowBringIntoViewSpec) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = padding, vertical = tv.dp(28)),
                horizontalArrangement = Arrangement.spacedBy(tv.dp(24)),
            ) {
                items(roles, key = { "${it.name}|${it.roleName}" }) { role ->
                    ActorCreditCard(role = role, imageUrl = imageUrl, onFocused = onCardFocused)
                }
            }
        }
    }
}

/**
 * `ActorCreditCard`: avatar de 144 pt con esquinas de 16 pt, nombre y papel debajo. Como en el
 * original, la escala 1.05 afecta a toda la ficha, pero el resplandor blanco rodea solo el avatar.
 */
@Composable
private fun ActorCreditCard(role: PlexRole, imageUrl: (String?, Int, Int) -> String?, onFocused: () -> Unit) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(tv.dp(16))
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(tv.dp(156))
            .duskTvFocusScale(focused)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Ficha de la persona: más adelante */ },
            ),
        verticalArrangement = Arrangement.spacedBy(tv.dp(14)),
    ) {
        Box(
            modifier = Modifier
                .size(tv.dp(144))
                .duskTvFocusGlow(focused, shape, colors.surface)
                .clip(shape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            DuskIcon(R.drawable.ic_person, tv.dp(44), colors.textSecondary)
            if (role.thumb != null) {
                AsyncImage(
                    model = imageUrl(
                        role.thumb,
                        ceil(144 * tv.scale * density).toInt(),
                        ceil(144 * tv.scale * density).toInt(),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier.width(tv.dp(144)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(2)),
        ) {
            BasicText(
                text = role.name,
                style = type.cardSubtitle.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!role.roleName.isNullOrEmpty()) {
                BasicText(
                    text = role.roleName,
                    style = type.cardSubtitle.copy(
                        color = colors.textPrimary.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
