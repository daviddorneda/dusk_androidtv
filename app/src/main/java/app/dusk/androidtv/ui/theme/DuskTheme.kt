package app.dusk.androidtv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens de color de Dusk (STYLE.md, secciones 2.1 y 2.2).
 * Paleta neutra: el coral de marca NO se usa para estado de la interfaz.
 */
@Immutable
data class DuskColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primaryActionLabel: Color,
    val brand: Color,
    val error: Color,
    val isDark: Boolean,
)

val DuskDarkColors = DuskColors(
    background = Color(0xFF090A0F),
    surface = Color(0xFF161824),
    textPrimary = Color(0xFFF2F2F7),
    textSecondary = Color(0xFF8E95A8),
    primaryActionLabel = Color(0xFF090A0F),
    brand = Color(0xFFFF6B4A),
    error = Color(0xFFFF453A),
    isDark = true,
)

val DuskLightColors = DuskColors(
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF636366),
    primaryActionLabel = Color(0xFFFFFFFF),
    brand = Color(0xFFFF6B4A),
    error = Color(0xFFFF3B30),
    isDark = false,
)

/**
 * tvOS trabaja en un lienzo fijo de 1920x1080 puntos. Android TV usa dp según la densidad
 * del televisor. Para que las medidas del original (pt) se vean igual en cualquier TV,
 * se escalan por (ancho real en dp / 1920).
 */
@Immutable
class TvMetrics(val scale: Float) {
    fun dp(pt: Float): Dp = (pt * scale).dp
    fun dp(pt: Int): Dp = dp(pt.toFloat())
    fun sp(pt: Float): TextUnit = (pt * scale).sp
    fun sp(pt: Int): TextUnit = sp(pt.toFloat())
}

/** Escala tipográfica `DuskFont.TV` de STYLE.md (3.1), en puntos de tvOS. */
@Immutable
class DuskTypography(m: TvMetrics, color: Color, secondary: Color) {
    private val primaryColor = color
    private val secondaryColor = secondary
    private val metrics = m

    private fun style(size: Int, weight: FontWeight, useSecondary: Boolean = false) = TextStyle(
        fontSize = metrics.sp(size),
        fontWeight = weight,
        color = if (useSecondary) secondaryColor else primaryColor,
    )

    val heroTitle = style(56, FontWeight.Bold)
    val glyphLarge = style(52, FontWeight.Normal, true)
    val pageTitle = style(48, FontWeight.Bold)
    val playerOverlayTitle = style(44, FontWeight.Bold)
    val sectionHeader = style(33, FontWeight.SemiBold)
    val playerTitle = style(33, FontWeight.SemiBold)
    val heroSubtitle = style(31, FontWeight.SemiBold)
    val glyphMedium = style(30, FontWeight.Normal, true)
    val pageSubtitle = style(29, FontWeight.Normal, true)
    val rowTitle = style(29, FontWeight.Medium)
    val body = style(27, FontWeight.Normal)
    val rowValue = style(27, FontWeight.Normal, true)
    val buttonLabel = style(27, FontWeight.SemiBold)
    val playerTitleCompact = style(27, FontWeight.SemiBold)
    val cardTitle = style(25, FontWeight.SemiBold)
    val metadata = style(25, FontWeight.Medium, true)
    val groupHeader = style(25, FontWeight.SemiBold, true)
    val cardSubtitle = style(23, FontWeight.Normal, true)
    val caption = style(23, FontWeight.Normal, true)
    val glyphSmall = style(23, FontWeight.Normal, true)
    val badge = style(21, FontWeight.Bold)

    /** Código del PIN de plex.tv/link: 54 pt, negrita, monoespaciada, tracking 8. */
    val linkCode = TextStyle(
        fontSize = metrics.sp(54),
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = metrics.sp(8),
        color = primaryColor,
    )
}

/** Formas (STYLE.md, 3.2), en puntos de tvOS. */
object DuskShapes {
    const val POSTER_RADIUS_PT = 16
    const val CARD_RADIUS_PT = 28
    const val BORDER_PT = 1
}

private val LocalDuskColors = staticCompositionLocalOf { DuskDarkColors }
private val LocalTvMetrics = staticCompositionLocalOf { TvMetrics(0.5f) }
private val LocalDuskTypography = staticCompositionLocalOf {
    DuskTypography(TvMetrics(0.5f), DuskDarkColors.textPrimary, DuskDarkColors.textSecondary)
}

object DuskTheme {
    val colors: DuskColors
        @Composable get() = LocalDuskColors.current
    val tv: TvMetrics
        @Composable get() = LocalTvMetrics.current
    val type: DuskTypography
        @Composable get() = LocalDuskTypography.current
}

@Composable
fun DuskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val metrics = TvMetrics(if (widthDp > 0) widthDp / 1920f else 0.5f)
    val colors = if (darkTheme) DuskDarkColors else DuskLightColors
    val typography = DuskTypography(metrics, colors.textPrimary, colors.textSecondary)
    CompositionLocalProvider(
        LocalDuskColors provides colors,
        LocalTvMetrics provides metrics,
        LocalDuskTypography provides typography,
        content = content,
    )
}
