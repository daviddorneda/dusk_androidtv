package app.dusk.androidtv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.dusk.androidtv.R
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Ajuste "Blur Unwatched Episodes" (`blursUnwatchedEpisodes` en el entorno del original). */
val LocalBlursUnwatchedEpisodes = staticCompositionLocalOf { false }

/**
 * Desenfoque gaussiano aproximado con tres pasadas de caja sobre una copia reducida. No depende de
 * `RenderEffect` (que solo existe desde Android 12), así que funciona igual en cualquier televisor.
 * Como en el original, el radio sigue el lado corto de la imagen (12 %, entre `minSigmaPx` y
 * `maxSigmaPx`): una tarjeta pequeña y un banner grande quedan igual de ilegibles.
 */
class SpoilerBlurTransformation(
    private val minSigmaPx: Float,
    private val maxSigmaPx: Float,
) : Transformation {
    override val cacheKey: String = "spoilerBlur-${minSigmaPx.toInt()}-${maxSigmaPx.toInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val width = input.width
        val height = input.height
        val sigma = (min(width, height) * 0.12f).coerceIn(minSigmaPx, maxSigmaPx)
        // Se trabaja a menor resolución: más rápido y el resultado sube suave al tamaño original.
        val factor = max(1f, sigma / 3f)
        val smallW = max((width / factor).toInt(), 8)
        val smallH = max((height / factor).toInt(), 8)
        val small = Bitmap.createScaledBitmap(input, smallW, smallH, true).copy(Bitmap.Config.ARGB_8888, true)
        val radius = max(1, (sigma / factor * 1.1f).toInt())
        val pixels = IntArray(smallW * smallH)
        small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
        repeat(3) {
            boxBlur(pixels, smallW, smallH, radius, horizontal = true)
            boxBlur(pixels, smallW, smallH, radius, horizontal = false)
        }
        small.setPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
        Bitmap.createScaledBitmap(small, width, height, true)
    }

    /** Pasada de caja con suma móvil; los bordes se prolongan con el último píxel. */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        val line = IntArray(length)
        for (index in 0 until lines) {
            for (i in 0 until length) {
                line[i] = if (horizontal) pixels[index * width + i] else pixels[i * width + index]
            }
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val c = line[i.coerceIn(0, length - 1)]
                a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (i in 0 until length) {
                val out = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) pixels[index * width + i] = out else pixels[i * width + index] = out
                val add = line[(i + radius + 1).coerceAtMost(length - 1)]
                val remove = line[(i - radius).coerceAtLeast(0)]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }
}

/**
 * Imagen que se difumina cuando es el fotograma de un episodio sin ver y el ajuste está activo
 * (`spoilerBlur`). Con `showsGlyph` pone un ojo tachado en el centro, del 24 % del lado corto
 * (entre 16 y 48 pt), como el original. Se aplica solo a la imagen: barras e insignias van encima.
 */
@Composable
fun SpoilerAsyncImage(
    model: String?,
    isUnwatchedEpisodeImage: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showsGlyph: Boolean = true,
) {
    val blurs = LocalBlursUnwatchedEpisodes.current && isUnwatchedEpisodeImage
    val tv = DuskTheme.tv
    val density = LocalDensity.current
    val context = LocalContext.current
    val ptPx = tv.scale * density.density
    val request = remember(model, blurs, ptPx) {
        ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (blurs) transformations(SpoilerBlurTransformation(minSigmaPx = 8f * ptPx, maxSigmaPx = 64f * ptPx))
            }
            .build()
    }
    var shortSidePx by remember { mutableIntStateOf(0) }

    Box(modifier.onSizeChanged { shortSidePx = min(it.width, it.height) }, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        if (blurs && showsGlyph && shortSidePx > 0) {
            val glyphPx = (shortSidePx * 0.24f).coerceIn(16f * ptPx, 48f * ptPx)
            DuskIcon(R.drawable.ic_visibility_off, with(density) { glyphPx.toDp() }, Color.White.copy(alpha = 0.9f))
        }
    }
}
