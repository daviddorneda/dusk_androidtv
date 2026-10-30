package app.dusk.androidtv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.dusk.androidtv.ui.library.MenuCard
import app.dusk.androidtv.R
import app.dusk.androidtv.ui.theme.DuskTheme

/** Métricas de `TVSettingsMetrics`, en puntos de tvOS. */
object SettingsMetrics {
    const val CONTENT_INSET_PT = 30
    const val ROW_BAND_OUTSET_PT = 22
    const val ROW_MIN_HEIGHT_PT = 64
    const val CARD_VERTICAL_PADDING_PT = 4
    const val SECTION_SPACING_PT = 40
}

/**
 * Sección de Ajustes (`TVSettingsSection`): cabecera en mayúsculas, tarjeta con la superficie de
 * la app (esquinas de 28 pt) y pie opcional.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val inset = tv.dp(SettingsMetrics.CONTENT_INSET_PT)
    // La tarjeta sangra a los lados: el contenido va dentro de `CONTENT_INSET` y la banda de foco
    // llega a ~8 pt del borde de la tarjeta.
    val cardPadding = tv.dp(SettingsMetrics.CONTENT_INSET_PT - SettingsMetrics.ROW_BAND_OUTSET_PT)

    Column(modifier = modifier.fillMaxWidth()) {
        BasicText(
            text = title.uppercase(),
            style = type.groupHeader.copy(letterSpacing = tv.sp(0.6f)),
            modifier = Modifier.padding(start = inset, bottom = tv.dp(12)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tv.dp(28)))
                .background(colors.surface)
                .padding(horizontal = cardPadding, vertical = tv.dp(SettingsMetrics.CARD_VERTICAL_PADDING_PT)),
        ) {
            content()
        }
        if (!footer.isNullOrEmpty()) {
            BasicText(
                text = footer,
                style = type.caption,
                modifier = Modifier.padding(top = tv.dp(14), start = inset, end = inset),
            )
        }
    }
}

/**
 * Fila de acción (`TVSettingsActionRow`): título (y detalle/chevron a la derecha) con una banda
 * de foco redondeada (18 pt) al 20 % del color de texto. Con `tint` rojo es "Sign Out".
 */
@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color? = null,
    detail: String? = null,
    showsChevron: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    var focused by remember { mutableStateOf(false) }
    val band by animateColorAsState(
        targetValue = colors.textPrimary.copy(alpha = if (focused) 0.20f else 0f),
        animationSpec = tween(180),
        label = "settingsRowBand",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .padding(vertical = tv.dp(4))
            .clip(RoundedCornerShape(tv.dp(18)))
            .background(band)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = tv.dp(SettingsMetrics.ROW_MIN_HEIGHT_PT - 8))
            .padding(horizontal = tv.dp(SettingsMetrics.ROW_BAND_OUTSET_PT), vertical = tv.dp(if (subtitle == null) 0 else 14)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tv.dp(4))) {
            BasicText(text = title, style = type.rowTitle.copy(color = tint ?: colors.textPrimary))
            if (subtitle != null) BasicText(text = subtitle, style = type.caption)
        }
        when {
            detail != null -> BasicText(
                text = detail,
                style = type.rowValue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            showsChevron -> DuskIcon(R.drawable.ic_chevron_right, tv.dp(26), colors.textSecondary)
        }
    }
}

/** Fila informativa (no enfocable): título y valor a la derecha. */
@Composable
fun SettingsInfoRow(title: String, detail: String, detailColor: Color? = null, subtitle: String? = null) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tv.dp(SettingsMetrics.ROW_MIN_HEIGHT_PT))
            .padding(horizontal = tv.dp(SettingsMetrics.ROW_BAND_OUTSET_PT)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tv.dp(4))) {
            BasicText(text = title, style = type.rowTitle)
            if (subtitle != null) BasicText(text = subtitle, style = type.caption)
        }
        BasicText(
            text = detail,
            style = type.rowValue.copy(color = detailColor ?: colors.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Separador fino entre filas (`tvRowDivider`). */
@Composable
fun SettingsRowDivider() {
    val tv = DuskTheme.tv
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tv.dp(SettingsMetrics.CONTENT_INSET_PT - 8))
            .height(tv.dp(1))
            .background(DuskTheme.colors.textPrimary.copy(alpha = 0.08f)),
    )
}

/**
 * Fila con interruptor (`TVSettingsToggleRow`): título a la izquierda, interruptor a la derecha y la
 * misma banda de foco que las demás filas. Pulsar la fila entera lo cambia.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }
    val band by animateColorAsState(
        targetValue = colors.textPrimary.copy(alpha = if (focused) 0.20f else 0f),
        animationSpec = tween(180),
        label = "settingsToggleBand",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .padding(vertical = tv.dp(4))
            .clip(RoundedCornerShape(tv.dp(18)))
            .background(band)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .heightIn(min = tv.dp(SettingsMetrics.ROW_MIN_HEIGHT_PT - 8))
            .padding(horizontal = tv.dp(SettingsMetrics.ROW_BAND_OUTSET_PT)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = title, style = DuskTheme.type.rowTitle, modifier = Modifier.weight(1f))
        DuskSwitch(checked = checked)
    }
}

/**
 * Fila de menú (`TVSettingsMenuRow`): título y valor elegido; al pulsarla se abre la lista de
 * opciones. El original empuja una pantalla propia con las opciones; aquí es una lista desplegable.
 */
@Composable
fun <T> SettingsMenuRow(
    title: String,
    selectedTitle: String,
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
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var rowHeight by remember { mutableStateOf(0) }
    val band by animateColorAsState(
        targetValue = colors.textPrimary.copy(alpha = if (focused) 0.20f else 0f),
        animationSpec = tween(180),
        label = "settingsMenuBand",
    )

    Box(modifier = modifier.fillMaxWidth().onSizeChanged { rowHeight = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .padding(vertical = tv.dp(4))
                .clip(RoundedCornerShape(tv.dp(18)))
                .background(band)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .heightIn(min = tv.dp(SettingsMetrics.ROW_MIN_HEIGHT_PT - 8))
                .padding(horizontal = tv.dp(SettingsMetrics.ROW_BAND_OUTSET_PT)),
            horizontalArrangement = Arrangement.spacedBy(tv.dp(20)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = title, style = type.rowTitle, maxLines = 1, modifier = Modifier.weight(1f))
            BasicText(text = selectedTitle, style = type.rowValue, maxLines = 1)
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, rowHeight),
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
