package app.dusk.androidtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.PlexHomeUser
import app.dusk.androidtv.data.plex.ServerEntry
import app.dusk.androidtv.data.plex.ServerManager
import app.dusk.androidtv.data.plex.ServerState
import app.dusk.androidtv.ui.account.PlexHomeUserAvatar
import app.dusk.androidtv.ui.components.SettingsActionRow
import app.dusk.androidtv.ui.components.SettingsInfoRow
import app.dusk.androidtv.ui.components.SettingsMetrics
import app.dusk.androidtv.ui.components.SettingsRowDivider
import app.dusk.androidtv.ui.components.SettingsSection
import app.dusk.androidtv.ui.theme.DuskTheme

/**
 * Ajustes (`SettingsTVView`), versión parcial: la estructura (título, secciones en tarjetas,
 * filas con banda de foco, "Sign Out" rojo en "Account") es la del original. Ya están Plex Home
 * y Account; "Servers" es un añadido de diagnóstico provisional. Faltan Support, Playback,
 * Appearance, Seerr, About...
 */
@Composable
fun SettingsScreen(
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
    val reports by DuskGraph.content.reports.collectAsStateWithLifecycle()
    val homeContent by DuskGraph.content.home.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = tv.dp(64), end = tv.dp(64), top = tv.dp(150), bottom = tv.dp(80)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = tv.dp(1100)).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tv.dp(SettingsMetrics.SECTION_SPACING_PT)),
        ) {
            BasicText(
                text = "Settings",
                style = type.pageTitle,
                modifier = Modifier.padding(start = tv.dp(SettingsMetrics.CONTENT_INSET_PT)),
            )

            if (hasPlexHome && activeUser != null) {
                SettingsSection(
                    title = "Plex Home",
                    footer = "When off, Dusk asks who’s watching whenever it starts.",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tv.dp(SettingsMetrics.ROW_BAND_OUTSET_PT),
                                vertical = tv.dp(12),
                            ),
                        horizontalArrangement = Arrangement.spacedBy(tv.dp(22)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlexHomeUserAvatar(user = activeUser, size = tv.dp(68))
                        Column(verticalArrangement = Arrangement.spacedBy(tv.dp(2))) {
                            BasicText(
                                text = "CURRENT USER",
                                style = type.badge.copy(color = colors.textSecondary, letterSpacing = tv.sp(0.5f)),
                            )
                            BasicText(text = activeUser.displayName, style = type.rowTitle)
                        }
                    }
                    SettingsRowDivider()
                    SettingsActionRow(title = "Switch User", showsChevron = true, onClick = onSwitchUser)
                }
            }

            SettingsSection(
                title = "Servers",
                footer = homeSummary(homeContent.heroItems.size, homeContent.shelves.size, reports),
            ) {
                if (entries.isEmpty()) {
                    SettingsInfoRow(
                        title = if (discovering) "Looking for servers…" else "No servers found",
                        detail = error ?: "",
                        detailColor = colors.error,
                    )
                }
                entries.forEachIndexed { index, entry ->
                    if (index > 0) SettingsRowDivider()
                    ServerInfoRow(entry)
                }
                SettingsRowDivider()
                SettingsActionRow(
                    title = "Retry Connection",
                    onClick = { servers.connectAll(accountToken) },
                    detail = if (discovering) "Connecting…" else null,
                )
            }

            SettingsSection(title = "Account") {
                SettingsActionRow(title = "Sign Out", tint = colors.error, onClick = onSignOut)
            }
        }
    }
}

@Composable
private fun ServerInfoRow(entry: ServerEntry) {
    val colors = DuskTheme.colors
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
        is ServerState.Connected -> colors.textSecondary
        ServerState.Connecting -> colors.textSecondary
        else -> colors.error
    }
    SettingsInfoRow(title = entry.server.name, subtitle = owner, detail = status, detailColor = statusColor)
}

private fun homeSummary(
    heroItems: Int,
    shelves: Int,
    reports: List<app.dusk.androidtv.data.plex.ServerLoadReport>,
): String? {
    if (reports.isEmpty()) return null
    val lines = mutableListOf("Home: $heroItems in the hero, $shelves rows")
    reports.forEach { report ->
        val summary = report.error
            ?: "${report.hubRows ?: "?"} rows · ${report.continueWatching ?: "?"} in Continue Watching · ${report.libraries ?: "?"} libraries"
        lines += "${report.serverName}: $summary"
    }
    return lines.joinToString("\n")
}
