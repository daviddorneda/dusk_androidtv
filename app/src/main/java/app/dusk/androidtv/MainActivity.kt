package app.dusk.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.data.plex.HomePhase
import app.dusk.androidtv.data.prefs.AppearanceMode
import app.dusk.androidtv.ui.account.HomeUserPickerScreen
import app.dusk.androidtv.ui.home.LoadFailedScreen
import app.dusk.androidtv.ui.home.LoadingScreen
import app.dusk.androidtv.ui.home.MainShell
import app.dusk.androidtv.ui.components.LocalBlursUnwatchedEpisodes
import app.dusk.androidtv.ui.signin.SignInScreen
import app.dusk.androidtv.ui.theme.DuskTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DuskGraph.init(applicationContext)
        setContent {
            val appearance by DuskGraph.preferences.appearanceMode.collectAsStateWithLifecycle()
            val dark = when (appearance) {
                AppearanceMode.System -> isSystemInDarkTheme()
                AppearanceMode.Light -> false
                AppearanceMode.Dark -> true
            }
            val blursUnwatched by DuskGraph.preferences.blursUnwatchedEpisodes.collectAsStateWithLifecycle()
            DuskTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalBlursUnwatchedEpisodes provides blursUnwatched) {
                    DuskRoot()
                }
            }
        }
    }
}

/** Equivale a `ContentView`: sin token -> inicio de sesión; con token -> flujo autenticado. */
@Composable
fun DuskRoot() {
    val token by DuskGraph.session.token.collectAsStateWithLifecycle()
    if (token == null) {
        SignInScreen()
    } else {
        AuthenticatedRoot(token = token!!)
    }
}

@Composable
private fun AuthenticatedRoot(token: String) {
    val home = DuskGraph.home
    val phase by home.phase.collectAsStateWithLifecycle()
    val accountToken by home.accountToken.collectAsStateWithLifecycle()
    val activeUser by home.activeUser.collectAsStateWithLifecycle()

    // Resuelve Plex Home al entrar (una sola vez por token).
    LaunchedEffect(token) { home.bootstrap() }

    // Cada vez que cambia la identidad activa se descubren y conectan los servidores de nuevo.
    val ready = phase is HomePhase.Ready
    LaunchedEffect(ready, accountToken) {
        val account = accountToken
        if (ready && account != null) DuskGraph.servers.connectAll(account)
    }

    when (val current = phase) {
        HomePhase.Idle, HomePhase.Loading -> LoadingScreen()
        is HomePhase.Failed -> LoadFailedScreen(
            message = current.message,
            onRetry = { DuskGraph.scope.launch { home.bootstrap(force = true) } },
            onSignOut = { DuskGraph.signOut() },
        )
        is HomePhase.SelectingUser -> HomeUserPickerScreen(
            users = current.users,
            canCancel = current.canCancel,
            activeProfileId = activeUser?.stableProfileId,
            onCancel = { home.cancelUserSelection() },
            onSignOut = { DuskGraph.signOut() },
        )
        HomePhase.Ready -> MainShell(
            activeUser = activeUser,
            hasPlexHome = home.hasPlexHome,
            accountToken = accountToken ?: token,
            onSwitchUser = { home.requestUserSelection() },
            onSignOut = { DuskGraph.signOut() },
        )
    }
}
