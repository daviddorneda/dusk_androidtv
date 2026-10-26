package app.dusk.androidtv.data.plex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HomePhase {
    data object Idle : HomePhase
    data object Loading : HomePhase
    data class SelectingUser(val users: List<PlexHomeUser>, val canCancel: Boolean) : HomePhase
    data object Ready : HomePhase
    data class Failed(val message: String) : HomePhase
}

/**
 * Resuelve el Plex Home de la cuenta y qué identidad está activa
 * (equivale a `PlexService+Home`). Con un solo miembro entra directo; con varios,
 * restaura el perfil recordado o pide elegir.
 */
class HomeSessionManager(
    private val plex: PlexClient,
    private val store: SessionStore,
) {
    private val _phase = MutableStateFlow<HomePhase>(HomePhase.Idle)
    val phase: StateFlow<HomePhase> = _phase.asStateFlow()

    private val _activeUser = MutableStateFlow<PlexHomeUser?>(null)
    val activeUser: StateFlow<PlexHomeUser?> = _activeUser.asStateFlow()

    /** Token de cuenta con el que se descubren servidores (principal o del miembro activo). */
    private val _accountToken = MutableStateFlow<String?>(null)
    val accountToken: StateFlow<String?> = _accountToken.asStateFlow()

    var homeUsers: List<PlexHomeUser> = emptyList()
        private set

    val hasPlexHome: Boolean get() = homeUsers.size > 1

    val automaticHomeSignIn: Boolean get() = store.automaticHomeSignIn

    private var bootstrappedFor: String? = null

    suspend fun bootstrap(force: Boolean = false) {
        val primary = store.token.value ?: return
        val current = _phase.value
        if (!force && bootstrappedFor == primary && current !is HomePhase.Idle && current !is HomePhase.Failed) return

        bootstrappedFor = primary
        _phase.value = HomePhase.Loading
        try {
            val users = plex.fetchHomeUsers(primary)
            homeUsers = users

            if (users.size <= 1) {
                activatePrimary(primary, users.firstOrNull())
                return
            }

            val remembered = store.rememberedHomeUser()
            val rememberedToken = store.rememberedHomeToken
            if (store.automaticHomeSignIn && remembered != null && rememberedToken != null) {
                val match = users.firstOrNull {
                    it.stableProfileId == remembered.stableProfileId || it.id == remembered.id
                }
                if (match != null && validateRestored(match, rememberedToken)) {
                    _accountToken.value = rememberedToken
                    _activeUser.value = match
                    store.saveHomeSession(rememberedToken, match)
                    _phase.value = HomePhase.Ready
                    return
                }
            }

            // Un Home con varios miembros siempre exige elegir si no hay sesión válida recordada.
            _accountToken.value = primary
            _activeUser.value = null
            store.clearHomeSession()
            _phase.value = HomePhase.SelectingUser(users, canCancel = false)
        } catch (e: CancellationException) {
            bootstrappedFor = null
            throw e
        } catch (e: Exception) {
            bootstrappedFor = null
            _phase.value = HomePhase.Failed(e.message ?: "Could not load your Plex Home.")
        }
    }

    /** Cambia la identidad activa. Si falla (p. ej. PIN incorrecto), la anterior sigue activa. */
    suspend fun switchUser(user: PlexHomeUser, pin: String?, remember: Boolean) {
        if (homeUsers.none { it.id == user.id }) {
            throw PlexException("That Plex Home user is no longer available.")
        }
        val primary = store.token.value ?: throw PlexException("You are signed out.")
        val normalizedPin = pin?.trim()?.takeIf { it.isNotEmpty() }
        if (user.isProtected && normalizedPin == null) {
            throw PlexException("Enter this user’s Plex Home PIN.")
        }

        val token = plex.switchHomeUser(user, normalizedPin, primary)

        _accountToken.value = token
        _activeUser.value = user
        store.automaticHomeSignIn = remember
        if (remember) store.saveHomeSession(token, user) else store.clearHomeSession()
        _phase.value = HomePhase.Ready
    }

    fun requestUserSelection() {
        if (!hasPlexHome) return
        _phase.value = HomePhase.SelectingUser(homeUsers, canCancel = _activeUser.value != null)
    }

    fun cancelUserSelection() {
        if (_activeUser.value == null) return
        _phase.value = HomePhase.Ready
    }

    fun reset() {
        bootstrappedFor = null
        homeUsers = emptyList()
        _activeUser.value = null
        _accountToken.value = null
        _phase.value = HomePhase.Idle
    }

    private fun activatePrimary(primary: String, user: PlexHomeUser?) {
        _accountToken.value = primary
        _activeUser.value = user
        store.clearHomeSession()
        _phase.value = HomePhase.Ready
    }

    /** `true` si el token recordado sigue siendo del mismo miembro; un fallo de red no lo descarta. */
    private suspend fun validateRestored(user: PlexHomeUser, token: String): Boolean {
        return try {
            val account = plex.fetchAccount(token)
            val expected = user.uuid
            if (expected != null && account.uuid != null) {
                expected == account.uuid
            } else {
                account.id == user.id || (user.username != null && user.username == account.username)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlexHttpException) {
            if (e.code == 401 || e.code == 403) false else true
        } catch (e: Exception) {
            true
        }
    }
}
