package app.dusk.androidtv.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
) {
    val tv = DuskTheme.tv
    Row(
        modifier = modifier
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
) {
    val tv = DuskTheme.tv
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        isSelected -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val content = if (focused) Color(0xFF090A0F) else Color.White.copy(alpha = if (isSelected) 1f else 0.82f)

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
