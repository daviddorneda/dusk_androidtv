package app.dusk.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.ui.home.SignedInPlaceholder
import app.dusk.androidtv.ui.signin.SignInScreen
import app.dusk.androidtv.ui.theme.DuskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DuskGraph.init(applicationContext)
        setContent {
            DuskTheme {
                DuskRoot()
            }
        }
    }
}

/**
 * Equivale a `ContentView` del original: la app se decide solo por el estado de la sesión.
 * Sin token -> SignInScreen. Con token -> (por ahora) pantalla provisional.
 */
@Composable
fun DuskRoot() {
    val token by DuskGraph.session.token.collectAsStateWithLifecycle()
    if (token == null) {
        SignInScreen()
    } else {
        SignedInPlaceholder(onSignOut = { DuskGraph.session.setToken(null) })
    }
}
