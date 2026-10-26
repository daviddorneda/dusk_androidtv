package app.dusk.androidtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import app.dusk.androidtv.ui.theme.DuskTheme

/**
 * Efecto de foco de tvOS (`duskTVOSFocusedScale`): escala 1.05 en 0.18 s y resplandor blanco
 * (alfa 0.34, radio 16 pt, desplazado 6 pt). Debe ir ANTES de `onFocusChanged` y `clickable`.
 */
@Composable
fun Modifier.duskTvFocusEffect(
    focused: Boolean,
    shape: Shape,
    glow: Boolean = true,
    focusedScale: Float = 1.05f,
): Modifier {
    val tv = DuskTheme.tv
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(180),
        label = "duskFocusScale",
    )
    val glowColor = Color.White.copy(alpha = 0.34f)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (glow && focused) {
                Modifier.shadow(
                    elevation = tv.dp(16),
                    shape = shape,
                    clip = false,
                    ambientColor = glowColor,
                    spotColor = glowColor,
                )
            } else {
                Modifier
            },
        )
}
