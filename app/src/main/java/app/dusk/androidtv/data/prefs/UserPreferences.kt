package app.dusk.androidtv.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajuste "Appearance" del original: System, Light o Dark. */
enum class AppearanceMode(val displayName: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Preferencias del usuario (equivale a `UserPreferences`). Por ahora solo la apariencia; el resto
 * llega con la pantalla de Ajustes.
 */
class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("dusk_preferences", Context.MODE_PRIVATE)

    private val _appearanceMode = MutableStateFlow(loadAppearance())
    val appearanceMode: StateFlow<AppearanceMode> = _appearanceMode.asStateFlow()

    fun setAppearanceMode(mode: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE, mode.name).apply()
        _appearanceMode.value = mode
    }

    /**
     * Igual que el original en tvOS: "el modo claro es desagradable para esta app", así que por
     * defecto es oscuro. Los televisores Android casi nunca informan de modo oscuro del sistema.
     */
    private fun loadAppearance(): AppearanceMode {
        val raw = prefs.getString(KEY_APPEARANCE, null)
        return AppearanceMode.entries.firstOrNull { it.name == raw } ?: AppearanceMode.Dark
    }

    private companion object {
        const val KEY_APPEARANCE = "appearance_mode"
    }
}
