package app.dusk.androidtv.data.plex

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Guarda el identificador de cliente de Plex y el token de la cuenta.
 * TODO: mover el token a Android Keystore (el original usa Keychain).
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dusk_session", Context.MODE_PRIVATE)

    val clientIdentifier: String = prefs.getString(KEY_CLIENT_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_CLIENT_ID, it).apply() }

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    fun setToken(value: String?) {
        val normalized = value?.takeIf { it.isNotBlank() }
        val editor = prefs.edit()
        if (normalized == null) editor.remove(KEY_TOKEN) else editor.putString(KEY_TOKEN, normalized)
        editor.apply()
        _token.value = normalized
    }

    private companion object {
        const val KEY_CLIENT_ID = "client_identifier"
        const val KEY_TOKEN = "auth_token"
    }
}
