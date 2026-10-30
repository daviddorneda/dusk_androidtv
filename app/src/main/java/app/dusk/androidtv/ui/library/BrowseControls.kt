package app.dusk.androidtv.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.dusk.androidtv.R
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.duskTvFocusScale
import app.dusk.androidtv.ui.theme.DuskTheme

private const val CONTROL_RADIUS_PT = 18

/**
 * Chip de exploración (`browseControlLabel`): superficie con borde fino de 1 pt; activo se invierte
 * a relleno de texto primario. Foco: escala 1.05 y un poco más de claridad.
 */
@Composable
fun BrowseChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOn: Boolean = false,
    focusRequester: FocusRequester? = null,
    content: @Composable (contentColor: Color, secondaryColor: Color) -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(CONTROL_RADIUS_PT))
    val fill = when {
        isOn -> colors.textPrimary
        focused -> Color.White.copy(alpha = 0.16f).compositeOver(colors.surface)
        else -> colors.surface
    }
    val contentColor = if (isOn) colors.background else colors.textPrimary
    val secondary = if (isOn) colors.background else colors.textSecondary

    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duskTvFocusScale(focused)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(fill)
            .border(tv.dp(1), if (isOn) Color.Transparent else colors.textSecondary.copy(alpha = 0.18f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = tv.dp(14), vertical = tv.dp(10)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(contentColor, secondary)
    }
}

/** Chip que abre un menú desplegable de opciones (Género, Orden). */
@Composable
fun <T> MenuChip(
    @DrawableRes iconRes: Int,
    label: String,
    isActive: Boolean,
    options: List<T>,
    isSelected: (T) -> Boolean,
    optionTitle: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    var expanded by remember { mutableStateOf(false) }
    var chipHeight by remember { mutableStateOf(0) }

    Box(modifier = modifier.onSizeChanged { chipHeight = it.height }) {
        BrowseChip(onClick = { expanded = true }, focusRequester = focusRequester) { content, secondary ->
            DuskIcon(iconRes, tv.dp(30), if (isActive) content else secondary)
            BasicText(text = label, style = type.buttonLabel.copy(color = content), maxLines = 1)
            DuskIcon(R.drawable.ic_expand_more, tv.dp(26), secondary)
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, chipHeight + 12),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                MenuCard(
                    options = options,
                    isSelected = isSelected,
                    optionTitle = optionTitle,
                    onSelect = {
                        expanded = false
                        onSelect(it)
                    },
                )
            }
        }
    }
}

@Composable
internal fun <T> MenuCard(
    options: List<T>,
    isSelected: (T) -> Boolean,
    optionTitle: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst(isSelected).coerceAtLeast(0)

    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .width(tv.dp(560))
            .heightIn(max = tv.dp(640))
            .clip(RoundedCornerShape(tv.dp(28)))
            .background(colors.surface)
            .border(tv.dp(1), colors.textSecondary.copy(alpha = 0.18f), RoundedCornerShape(tv.dp(28)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tv.dp(8), vertical = tv.dp(8)),
    ) {
        options.forEachIndexed { index, option ->
            MenuOptionRow(
                title = optionTitle(option),
                selected = isSelected(option),
                focusRequester = if (index == selectedIndex) selectedFocus else null,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun MenuOptionRow(title: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .padding(vertical = tv.dp(3))
            .clip(RoundedCornerShape(tv.dp(18)))
            .background(if (focused) colors.textPrimary.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = tv.dp(58))
            .padding(horizontal = tv.dp(22)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style = DuskTheme.type.rowTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) DuskIcon(R.drawable.ic_check, tv.dp(26), colors.textPrimary)
    }
}
