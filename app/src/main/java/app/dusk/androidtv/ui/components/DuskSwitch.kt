package app.dusk.androidtv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.dusk.androidtv.ui.theme.DuskTheme

/** Interruptor visual estilo tvOS. El toque lo gestiona la fila que lo contiene. */
@Composable
fun DuskSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val tv = DuskTheme.tv
    val thumbX by animateDpAsState(if (checked) tv.dp(38) else tv.dp(4), label = "switchThumb")
    Box(
        modifier = modifier
            .size(width = tv.dp(82), height = tv.dp(48))
            .clip(CircleShape)
            .background(if (checked) Color(0xFF30D158) else Color.White.copy(alpha = 0.24f)),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = tv.dp(4))
                .size(tv.dp(40))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
