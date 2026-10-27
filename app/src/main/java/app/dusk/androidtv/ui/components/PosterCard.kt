package app.dusk.androidtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.dusk.androidtv.R
import app.dusk.androidtv.ui.theme.DuskShapes
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage

/**
 * Tarjeta de póster de tvOS (`PosterNavigationCard` + `PosterCardText`): póster 2:3 con esquinas
 * de 16 pt, barra de progreso blanca sobre pista oscura, título (2 líneas) y subtítulo.
 */
@Composable
fun PosterCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    progress: Double?,
    isWatched: Boolean,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageAspectRatio: Float = 2f / 3f,
    focusRequester: FocusRequester? = null,
    keyModifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(DuskShapes.POSTER_RADIUS_PT))
    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = width / imageAspectRatio)
                .then(focusModifier)
                .then(keyModifier)
                .duskTvCardFocusEffect(focused, shape)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused?.invoke()
                }
                .clip(shape)
                .background(colors.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            DuskIcon(R.drawable.ic_movie, tv.dp(52), colors.textSecondary)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val sheen by animateFloatAsState(
                targetValue = if (focused) 1f else 0f,
                animationSpec = tween(220),
                label = "posterSheen",
            )
            if (sheen > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                0f to Color.White.copy(alpha = 0.18f * sheen),
                                0.45f to Color.Transparent,
                            ),
                        ),
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
                text = title,
                style = type.cardTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width),
            )
            if (!subtitle.isNullOrEmpty() || isWatched) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tv.dp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(width),
                ) {
                    if (!subtitle.isNullOrEmpty()) {
                        BasicText(
                            text = subtitle,
                            style = type.cardSubtitle.copy(color = colors.textPrimary.copy(alpha = 0.68f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (isWatched) {
                        DuskIcon(R.drawable.ic_check, tv.dp(22), colors.textPrimary.copy(alpha = 0.68f))
                    }
                }
            }
        }
    }
}
