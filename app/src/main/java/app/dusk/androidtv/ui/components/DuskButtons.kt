package app.dusk.androidtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import app.dusk.androidtv.ui.theme.DuskTheme

/**
 * Botón "glass" de tvOS (STYLE.md 3.3): plataforma oscura translúcida en reposo que sube
 * a blanco con etiqueta oscura al recibir el foco. Cápsula, sin tinte de color.
 */
@Composable
fun DuskGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "glassFocusScale")

    val container = if (focused) Color.White else Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.60f)
    val label = when {
        focused -> Color(0xFF090A0F)
        colors.isDark -> Color(0xFFF2F2F7)
        else -> Color(0xFF1C1C1E)
    }
    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Row(
        modifier = modifier
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = tv.dp(40), vertical = tv.dp(18)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(12), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) DuskSpinner(size = tv.dp(28), color = label)
        BasicText(
            text = text,
            style = DuskTheme.type.buttonLabel.copy(color = label, textAlign = TextAlign.Center),
        )
    }
}

/**
 * Botón de texto con foco en cápsula. Sin `tint`, el texto pasa de secundario a primario al
 * enfocar; con `tint` (p. ej. rojo para "Sign Out") mantiene ese color y rellena con la
 * superficie al enfocar, como `HomeUserTextButton` del original.
 */
@Composable
fun DuskTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    tint: Color? = null,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "textFocusScale")
    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    val focusedBackground = if (tint != null) colors.surface else colors.textPrimary.copy(alpha = 0.14f)
    val textColor = tint ?: if (focused) colors.textPrimary else colors.textSecondary

    Row(
        modifier = modifier
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) focusedBackground else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = tv.dp(32), vertical = tv.dp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = text, style = DuskTheme.type.buttonLabel.copy(color = textColor))
    }
}
