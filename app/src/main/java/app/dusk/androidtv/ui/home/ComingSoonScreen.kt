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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.dusk.androidtv.ui.theme.DuskTheme

/** Destino todavía no portado: título y una nota. No pide foco para no quitárselo a la barra. */
@Composable
fun ComingSoonScreen(title: String, note: String) {
    val tv = DuskTheme.tv
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuskTheme.colors.background)
            .padding(horizontal = tv.dp(64), vertical = tv.dp(60)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
        ) {
            BasicText(text = title, style = DuskTheme.type.pageTitle)
            BasicText(
                text = note,
                style = DuskTheme.type.pageSubtitle.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(720)),
            )
        }
    }
}
