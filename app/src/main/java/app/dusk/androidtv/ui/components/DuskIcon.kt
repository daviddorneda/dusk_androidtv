package app.dusk.androidtv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/** Icono vectorial teñido (sustituye a los SF Symbols del original). */
@Composable
fun DuskIcon(@DrawableRes id: Int, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
