package app.dusk.androidtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.data.plex.PlexHomeUser
import app.dusk.androidtv.data.plex.ServerEntry
import app.dusk.androidtv.data.plex.ServerManager
import app.dusk.androidtv.data.plex.ServerState
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.DuskTextButton
import app.dusk.androidtv.ui.theme.DuskTheme

/**
 * Pantalla PROVISIONAL de diagnóstico: muestra el perfil activo y el estado de conexión de cada
 * servidor. Se sustituye por Home (hubs y bibliotecas) en la fase 3.
 */
@Composable
fun ServersScreen(
    activeUser: PlexHomeUser?,
    hasPlexHome: Boolean,
    servers: ServerManager,
    accountToken: String,
    onSwitchUser: () -> Unit,
    onSignOut: () -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val entries by servers.entries.collectAsStateWithLifecycle()
    val discovering by servers.isDiscovering.collectAsStateWithLifecycle()
    val error by servers.error.collectAsStateWithLifecycle()
    val retryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tv.dp(64), vertical = tv.dp(60)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tv.dp(24)),
    ) {
        BasicText(text = "Servers", style = type.pageTitle)
        BasicText(
            text = "Signed in as ${activeUser?.displayName ?: "your Plex account"}",
            style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
        )

        Column(
            modifier = Modifier.widthIn(max = tv.dp(1120)).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
        ) {
            if (entries.isEmpty() && discovering) {
                Box(Modifier.fillMaxWidth().padding(tv.dp(24)), contentAlignment = Alignment.Center) {
                    DuskSpinner(size = tv.dp(40), color = colors.textSecondary)
                }
            }
            entries.forEach { ServerRow(it) }
            if (entries.isEmpty() && !discovering && error == null) {
                BasicText(
                    text = "No Plex Media Servers were found for this account.",
                    style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        error?.let {
            BasicText(
                text = it,
                style = type.body.copy(color = colors.error, textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(720)),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(tv.dp(20)), verticalAlignment = Alignment.CenterVertically) {
            DuskGlassButton(
                text = "Retry",
                onClick = { servers.connectAll(accountToken) },
                loading = discovering,
                focusRequester = retryFocus,
            )
            if (hasPlexHome) {
                DuskGlassButton(text = "Switch User", onClick = onSwitchUser)
            }
            DuskTextButton(text = "Sign Out", onClick = onSignOut, tint = colors.error)
        }
    }
}

@Composable
private fun ServerRow(entry: ServerEntry) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val shape = RoundedCornerShape(tv.dp(28))

    val owner = when {
        entry.server.owned -> "Owned"
        entry.server.sourceTitle != null -> "Shared by ${entry.server.sourceTitle}"
        else -> "Shared"
    }
    val status = when (val state = entry.state) {
        ServerState.Connecting -> "Connecting…"
        is ServerState.Connected -> "Connected · " + when {
            state.connection.isLocal -> "Local network"
            state.connection.isRelay -> "Relay"
            else -> "Remote"
        }
        ServerState.Unauthorized -> "Not authorized"
        is ServerState.Offline -> "Offline · ${state.reason}"
    }
    val statusColor = when (entry.state) {
        is ServerState.Connected -> colors.textPrimary
        ServerState.Connecting -> colors.textSecondary
        else -> colors.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(tv.dp(1), colors.textPrimary.copy(alpha = 0.05f), shape)
            .padding(horizontal = tv.dp(28), vertical = tv.dp(22)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tv.dp(4))) {
            BasicText(text = entry.server.name, style = type.rowTitle)
            BasicText(text = owner, style = type.caption)
        }
        BasicText(text = status, style = type.rowValue.copy(color = statusColor))
    }
}
