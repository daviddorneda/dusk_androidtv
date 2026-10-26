package app.dusk.androidtv.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.PlexClient
import app.dusk.androidtv.data.plex.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val linkPinCode: String? = null,
    val error: String? = null,
)

/**
 * Equivale al flujo de tvOS de `SignInView`: se pide un PIN corto, se muestra en pantalla
 * (el usuario lo introduce en plex.tv/link) y se consulta cada segundo durante 120 intentos.
 */
class SignInViewModel(
    private val plex: PlexClient,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun signIn() {
        if (_state.value.isSigningIn) return
        pollingJob?.cancel()
        _state.value = SignInUiState(isSigningIn = true)
        pollingJob = viewModelScope.launch {
            try {
                val pin = plex.generatePin()
                _state.update { it.copy(linkPinCode = pin.code) }
                poll(pin.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SignInUiState(error = e.message ?: "Sign-in failed.")
            }
        }
    }

    fun cancel() {
        pollingJob?.cancel()
        _state.value = SignInUiState()
    }

    private suspend fun poll(pinId: Int) {
        for (attempt in 0 until MAX_ATTEMPTS) {
            delay(1_000)
            val token = try {
                plex.checkPin(pinId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (token != null) {
                session.setToken(token)
                _state.value = SignInUiState()
                return
            }
        }
        _state.value = SignInUiState(error = "Sign-in timed out. Please try again.")
    }

    companion object {
        private const val MAX_ATTEMPTS = 120

        val Factory = viewModelFactory {
            initializer { SignInViewModel(DuskGraph.plex, DuskGraph.session) }
        }
    }
}
