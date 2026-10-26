package app.dusk.androidtv.ui.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dusk.androidtv.R
import app.dusk.androidtv.ui.components.DuskGlassButton
import app.dusk.androidtv.ui.components.DuskTextButton
import app.dusk.androidtv.ui.theme.DuskTheme

/**
 * Pantalla de inicio de sesión de tvOS (`SignInView.tvSignInContent`):
 * logo 112 pt, título, "Sign in with Plex", código del PIN en una tarjeta y botón principal.
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel = viewModel(factory = SignInViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type

    val signInFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }

    // Foco inicial en el botón principal; al empezar el login pasa a "Cancel".
    LaunchedEffect(state.isSigningIn) {
        runCatching {
            if (state.isSigningIn) cancelFocus.requestFocus() else signInFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = tv.dp(40), vertical = tv.dp(60)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(24)),
        ) {
            // Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tv.dp(16)),
            ) {
                Image(
                    painter = painterResource(R.drawable.dusk_logo),
                    contentDescription = null,
                    modifier = Modifier.size(tv.dp(112)),
                    contentScale = ContentScale.Fit,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tv.dp(6)),
                ) {
                    BasicText(
                        text = "Dusk",
                        style = type.pageTitle.copy(fontSize = tv.sp(44), fontWeight = FontWeight.Bold),
                    )
                    BasicText(text = "The Plex App we Deserve.", style = type.caption)
                }
            }

            // Título y explicación
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tv.dp(10)),
            ) {
                BasicText(text = "Sign in with Plex", style = type.sectionHeader)
                BasicText(
                    text = "On another device, open plex.tv/link and enter the code shown below.",
                    style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.widthIn(max = tv.dp(720)),
                )
            }

            state.error?.let { message ->
                BasicText(
                    text = message,
                    style = type.body.copy(color = colors.error, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(horizontal = tv.dp(40)),
                )
            }

            state.linkPinCode?.let { code ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tv.dp(12)),
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(tv.dp(32)))
                        .padding(horizontal = tv.dp(54), vertical = tv.dp(28)),
                ) {
                    BasicText(
                        text = "plex.tv/link",
                        style = type.rowTitle.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText(text = code, style = type.linkCode)
                }
            }

            DuskGlassButton(
                text = if (state.isSigningIn) "Waiting for approval…" else "Sign in with Plex",
                onClick = viewModel::signIn,
                enabled = !state.isSigningIn,
                loading = state.isSigningIn,
                focusRequester = signInFocus,
                modifier = Modifier.widthIn(max = tv.dp(520)).fillMaxWidth(),
            )

            if (state.isSigningIn) {
                DuskTextButton(
                    text = "Cancel",
                    onClick = viewModel::cancel,
                    focusRequester = cancelFocus,
                )
            }
        }
    }
}
