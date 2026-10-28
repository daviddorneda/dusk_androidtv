package app.dusk.androidtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/** Estilos de `DuskHeroBottomFadeStyle` en tvOS: (posición, opacidad de la máscara). */
enum class HeroBottomFade(val stops: List<Pair<Float, Float>>) {
    /** Fundido completo de los héroes de detalle. */
    Standard(
        listOf(0f to 1f, 0.20f to 1f, 0.38f to 0.92f, 0.54f to 0.72f, 0.68f to 0.40f, 0.80f to 0.10f, 0.88f to 0f, 1f to 0f),
    ),

    /** Héroe que ocupa toda la pantalla (Home): casi todo a plena fuerza y viñeta en el último 14 %. */
    FullBleed(
        listOf(0f to 1f, 0.86f to 1f, 0.92f to 0.84f, 0.965f to 0.40f, 0.99f to 0f, 1f to 0f),
    ),
}

/** Enmascara el fondo con un degradado vertical de opacidad, para fundirlo con la página. */
fun Modifier.duskHeroBottomFade(style: HeroBottomFade): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                *style.stops.map { (position, alpha) -> position to Color.Black.copy(alpha = alpha) }.toTypedArray(),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Estilos de `DuskHeroOverlayStyle` en tvOS: oscurecimiento vertical del fondo. */
enum class HeroOverlay(val verticalStops: List<Pair<Float, Float>>) {
    /** Detalle (también `.soft`). */
    Standard(listOf(0f to 0.18f, 0.62f to 0.56f, 1f to 0.86f)),

    /** Home. */
    Cinematic(listOf(0f to 0.10f, 0.55f to 0.20f, 0.82f to 0.62f, 1f to 0.88f)),
}

/**
 * `DuskHeroBackdropOverlay`: oscurecimiento vertical según el estilo más un degradado
 * horizontal (negro 0.86 → 0.50 → 0.12 → 0) que protege el texto de la izquierda.
 */
@Composable
fun HeroBackdropOverlay(style: HeroOverlay) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                *style.verticalStops.map { (position, alpha) -> position to Color.Black.copy(alpha = alpha) }.toTypedArray(),
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.86f),
                0.34f to Color.Black.copy(alpha = 0.50f),
                0.68f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Black.copy(alpha = 0f),
            ),
        ),
    )
}
