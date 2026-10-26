package app.dusk.androidtv.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.HomeSessionManager
import app.dusk.androidtv.data.plex.PlexHomeUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUserPickerState(
    val pendingUser: PlexHomeUser? = null,
    val pin: String = "",
    val switchingProfileId: String? = null,
    val error: String? = null,
    val rememberSelection: Boolean = true,
)

/** Estado transitorio del selector (`HomeUserPickerView`). El PIN vive solo aquí, nunca se guarda. */
class HomeUserPickerViewModel(private val home: HomeSessionManager) : ViewModel() {

    private val _state = MutableStateFlow(HomeUserPickerState(rememberSelection = home.automaticHomeSignIn))
    val state: StateFlow<HomeUserPickerState> = _state.asStateFlow()

    fun reset() {
        _state.value = HomeUserPickerState(rememberSelection = home.automaticHomeSignIn)
    }

    fun setRemember(value: Boolean) {
        _state.update { it.copy(rememberSelection = value) }
    }

    fun select(user: PlexHomeUser) {
        if (_state.value.switchingProfileId != null) return
        if (user.isProtected) {
            _state.update { it.copy(pendingUser = user, pin = "", error = null) }
        } else {
            switchTo(user, null)
        }
    }

    fun leavePin() {
        if (_state.value.switchingProfileId != null) return
        _state.update { it.copy(pendingUser = null, pin = "", error = null) }
    }

    fun appendDigit(digit: String) {
        val current = _state.value
        val user = current.pendingUser ?: return
        if (current.switchingProfileId != null || current.pin.length >= PIN_LENGTH) return
        val newPin = current.pin + digit
        _state.update { it.copy(pin = newPin) }
        if (newPin.length == PIN_LENGTH) switchTo(user, newPin)
    }

    fun deleteDigit() {
        val current = _state.value
        if (current.switchingProfileId != null || current.pin.isEmpty()) return
        _state.update { it.copy(pin = it.pin.dropLast(1)) }
    }

    private fun switchTo(user: PlexHomeUser, pin: String?) {
        if (_state.value.switchingProfileId != null) return
        val remember = _state.value.rememberSelection
        _state.update { it.copy(error = null, switchingProfileId = user.stableProfileId) }
        viewModelScope.launch {
            try {
                home.switchUser(user, pin, remember)
                _state.value = HomeUserPickerState(rememberSelection = remember)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = e.message ?: "Could not switch users.",
                        switchingProfileId = null,
                        pin = "",
                    )
                }
            }
        }
    }

    companion object {
        const val PIN_LENGTH = 4

        val Factory = viewModelFactory {
            initializer { HomeUserPickerViewModel(DuskGraph.home) }
        }
    }
}
