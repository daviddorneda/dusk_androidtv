package app.dusk.androidtv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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

/** Escala del póster enfocado. El estilo `.card` de tvOS crece alrededor de un 10 %. */
const val DUSK_CARD_FOCUS_SCALE = 1.1f

/**
 * Aproximación del estilo de botón `.card` de tvOS que usa el original en los pósters
 * (`duskTVOSSystemPosterButton`): el póster se eleva con una sombra suave y oscura debajo y
 * un brillo especular. NO lleva el resplandor blanco, que el original reserva para filas
 * propias (selector de usuarios, teclado del PIN). Debe ir ANTES de `onFocusChanged`.
 */
@Composable
fun Modifier.duskTvCardFocusEffect(
    focused: Boolean,
    shape: Shape,
    focusedScale: Float = DUSK_CARD_FOCUS_SCALE,
): Modifier {
    val tv = DuskTheme.tv
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "duskCardScale",
    )
    val lift by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(220),
        label = "duskCardLift",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(
            elevation = tv.dp(34) * lift,
            shape = shape,
            clip = false,
            ambientColor = Color.Black,
            spotColor = Color.Black,
        )
}
