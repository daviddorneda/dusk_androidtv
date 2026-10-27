package app.dusk.androidtv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.dusk.androidtv.R
import app.dusk.androidtv.ui.theme.DuskTheme

enum class MainTab(val title: String, @DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_home),
    Movies("Movies", R.drawable.ic_movie),
    TvShows("TV Shows", R.drawable.ic_tv),
    Videos("Videos", R.drawable.ic_video),
    Search("Search", R.drawable.ic_search),
    Settings("Settings", R.drawable.ic_settings),
}

/**
 * Barra de pestañas flotante de tvOS 26: una píldora oscura y translúcida arriba al centro.
 * Como en tvOS, el contenido cambia al mover el foco entre pestañas.
 */
@Composable
fun DuskTabBar(
    tabs: List<MainTab>,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    selectedFocus: FocusRequester? = null,
    onMoveDown: (() -> Boolean)? = null,
) {
    val tv = DuskTheme.tv
    // Mientras el foco está fuera de la barra, solo la pestaña activa puede recibirlo: al subir desde
    // cualquier pantalla se llega SIEMPRE a la pestaña actual (y no a la más cercana, que cambiaría
    // de pestaña). Una vez dentro, todas son enfocables para moverse entre ellas.
    var barHasFocus by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .onFocusChanged { barHasFocus = it.hasFocus }
            .clip(CircleShape)
            .background(Color(0xFF14161E).copy(alpha = 0.78f))
            .border(tv.dp(1), Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(tv.dp(8)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            TabBarItem(
                tab = tab,
                isSelected = tab == selected,
                onSelect = { onSelect(tab) },
                focusRequester = if (tab == selected) selectedFocus else null,
                onMoveDown = onMoveDown,
                canReceiveFocus = barHasFocus || tab == selected,
            )
        }
    }
}

@Composable
private fun TabBarItem(
    tab: MainTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester?,
    onMoveDown: (() -> Boolean)?,
    canReceiveFocus: Boolean,
) {
    val tv = DuskTheme.tv
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "tabBackground",
    )
    val content by animateColorAsState(
        targetValue = if (focused) Color(0xFF090A0F) else Color.White.copy(alpha = if (isSelected) 1f else 0.82f),
        animationSpec = tween(220),
        label = "tabContent",
    )

    Row(
        modifier = Modifier
            .focusProperties { canFocus = canReceiveFocus }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onMoveDown?.invoke() ?: false
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = tv.dp(24), vertical = tv.dp(14)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuskIcon(tab.icon, tv.dp(28), content)
        BasicText(text = tab.title, style = DuskTheme.type.cardTitle.copy(color = content))
    }
}
