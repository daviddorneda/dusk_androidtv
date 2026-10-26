package app.dusk.androidtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.DuskTextButton
import app.dusk.androidtv.ui.theme.DuskTheme

/** Pantalla de carga mientras se resuelve el Plex Home de la cuenta. */
@Composable
fun LoadingScreen() {
    val tv = DuskTheme.tv
    Box(
        modifier = Modifier.fillMaxSize().background(DuskTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        DuskSpinner(size = tv.dp(56), color = DuskTheme.colors.textSecondary)
    }
}

/** Error al cargar la cuenta, con reintento y cierre de sesión. */
@Composable
fun LoadFailedScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    val tv = DuskTheme.tv
    val type = DuskTheme.type
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuskTheme.colors.background)
            .padding(horizontal = tv.dp(40), vertical = tv.dp(60)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(24)),
        ) {
            BasicText(text = "Couldn’t Load Your Plex Account", style = type.sectionHeader)
            BasicText(
                text = message,
                style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(720)),
            )
            DuskGlassButton(
                text = "Try Again",
                onClick = onRetry,
                focusRequester = focus,
                modifier = Modifier.widthIn(min = tv.dp(320)),
            )
            DuskTextButton(text = "Sign Out", onClick = onSignOut, tint = DuskTheme.colors.error)
        }
    }
}
