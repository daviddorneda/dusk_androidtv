package app.dusk.androidtv.data.plex

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Guarda el identificador de cliente, el token de la cuenta principal y la sesión recordada
 * de Plex Home. TODO: mover los tokens a Android Keystore (el original usa Keychain).
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dusk_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val clientIdentifier: String = prefs.getString(KEY_CLIENT_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_CLIENT_ID, it).apply() }

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))

    /** Token de la cuenta principal de Plex. */
    val token: StateFlow<String?> = _token.asStateFlow()

    fun setToken(value: String?) {
        val normalized = value?.takeIf { it.isNotBlank() }
        if (normalized != _token.value) clearHomeSession(resetAutomatic = true)
        val editor = prefs.edit()
        if (normalized == null) editor.remove(KEY_TOKEN) else editor.putString(KEY_TOKEN, normalized)
        editor.apply()
        _token.value = normalized
    }

    // ---- Plex Home ----

    /** Si es true (por defecto), se reabre la app con el último perfil elegido. */
    var automaticHomeSignIn: Boolean
        get() = prefs.getBoolean(KEY_AUTOMATIC_HOME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTOMATIC_HOME, value).apply()
        }

    val rememberedHomeToken: String?
        get() = prefs.getString(KEY_HOME_TOKEN, null)

    fun rememberedHomeUser(): PlexHomeUser? =
        prefs.getString(KEY_HOME_USER, null)
            ?.let { runCatching { json.decodeFromString(PlexHomeUser.serializer(), it) }.getOrNull() }

    fun saveHomeSession(token: String, user: PlexHomeUser) {
        prefs.edit()
            .putString(KEY_HOME_TOKEN, token)
            .putString(KEY_HOME_USER, json.encodeToString(PlexHomeUser.serializer(), user))
            .apply()
    }

    fun clearHomeSession(resetAutomatic: Boolean = false) {
        val editor = prefs.edit().remove(KEY_HOME_TOKEN).remove(KEY_HOME_USER)
        if (resetAutomatic) editor.remove(KEY_AUTOMATIC_HOME)
        editor.apply()
    }

    // ---- Última conexión buena por servidor ----

    fun lastGoodUri(serverId: String): String? = prefs.getString(KEY_LAST_GOOD + serverId, null)

    fun setLastGoodUri(serverId: String, uri: String) {
        prefs.edit().putString(KEY_LAST_GOOD + serverId, uri).apply()
    }

    private companion object {
        const val KEY_CLIENT_ID = "client_identifier"
        const val KEY_TOKEN = "auth_token"
        const val KEY_AUTOMATIC_HOME = "automatic_home_sign_in"
        const val KEY_HOME_TOKEN = "home_token"
        const val KEY_HOME_USER = "home_user"
        const val KEY_LAST_GOOD = "last_good_"
    }
}
