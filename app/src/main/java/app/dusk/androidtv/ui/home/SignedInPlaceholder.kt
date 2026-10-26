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
import app.dusk.androidtv.ui.theme.DuskTheme

/** Pantalla provisional tras iniciar sesión. Se sustituirá por Home (servidores, hubs, bibliotecas). */
@Composable
fun SignedInPlaceholder(onSignOut: () -> Unit) {
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
            BasicText(text = "You're signed in", style = type.pageTitle)
            BasicText(
                text = "Servers, Home and libraries come next.",
                style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
            )
            DuskGlassButton(
                text = "Sign out",
                onClick = onSignOut,
                focusRequester = focus,
                modifier = Modifier.widthIn(min = tv.dp(260)),
            )
        }
    }
}
