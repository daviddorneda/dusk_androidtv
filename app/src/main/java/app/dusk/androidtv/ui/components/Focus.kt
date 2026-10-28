package app.dusk.androidtv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.dusk.androidtv.ui.theme.DuskTheme

/**
 * Escala de foco de tvOS (`duskTVOSFocusedScale`): 1.05 con `easeOut` de 0.18 s. Debe ir ANTES
 * de `onFocusChanged` y `clickable`. Se puede aplicar a un contenedor y poner el resplandor
 * solo en una parte (`duskTvFocusGlow`), como hace el original con las fichas de reparto.
 */
@Composable
fun Modifier.duskTvFocusScale(focused: Boolean, focusedScale: Float = 1.05f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "duskFocusScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Resplandor blanco de tvOS: la sombra de SwiftUI `.shadow(color: white 0.34, radius: 16, y: 6)`,
 * un halo difuso con la silueta del elemento, desplazado hacia abajo. Se dibuja con una sombra
 * de pintura (desenfoque gaussiano), no con la elevación de Material, que da un borde duro y
 * poco natural. `backing` es el color opaco del elemento: queda tapado por su propio contenido.
 */
@Composable
fun Modifier.duskTvFocusGlow(focused: Boolean, shape: Shape, backing: Color): Modifier {
    val tv = DuskTheme.tv
    val progress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "duskFocusGlow",
    )
    val density = LocalDensity.current.density
    return this.drawBehind {
        if (progress <= 0.01f) return@drawBehind
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path()
        when (outline) {
            is Outline.Rectangle -> path.addRect(outline.rect)
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Generic -> path.addPath(outline.path)
        }
        val radiusPx = 16f * tv.scale * density
        val offsetYPx = 6f * tv.scale * density
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = backing.toArgb()
            setShadowLayer(radiusPx, 0f, offsetYPx, Color.White.copy(alpha = 0.34f * progress).toArgb())
        }
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
    }
}

/** Escala + resplandor sobre el mismo elemento (`duskTVOSFocusedScale` con `glow`). */
@Composable
fun Modifier.duskTvFocusEffect(
    focused: Boolean,
    shape: Shape,
    glow: Boolean = true,
    focusedScale: Float = 1.05f,
    backing: Color = DuskTheme.colors.surface,
): Modifier {
    val scaled = this.duskTvFocusScale(focused, focusedScale)
    return if (glow) scaled.duskTvFocusGlow(focused, shape, backing) else scaled
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
