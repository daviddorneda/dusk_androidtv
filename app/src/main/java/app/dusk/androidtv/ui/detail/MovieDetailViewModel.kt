package app.dusk.androidtv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.MediaDetails
import app.dusk.androidtv.data.plex.PlexException
import app.dusk.androidtv.data.plex.ServerConnection
import app.dusk.androidtv.ui.nav.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieDetailState(
    val isLoading: Boolean = true,
    val details: MediaDetails? = null,
    val error: String? = null,
)

/** `MovieDetailViewModel`: carga la ficha y gestiona "visto / no visto". */
class MovieDetailViewModel(private val route: Route.MovieDetail) : ViewModel() {
    private val _state = MutableStateFlow(MovieDetailState())
    val state: StateFlow<MovieDetailState> = _state.asStateFlow()

    val connection: ServerConnection? get() = DuskGraph.content.connectionFor(route.serverId)

    init {
        load()
    }

    fun load() {
        val connection = connection
        if (connection == null) {
            _state.value = MovieDetailState(isLoading = false, error = "This server is not available right now.")
            return
        }
        _state.update { it.copy(isLoading = it.details == null, error = null) }
        viewModelScope.launch {
            try {
                val details = DuskGraph.serverApi.details(connection, route.ratingKey)
                _state.value = MovieDetailState(isLoading = false, details = details)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = if (it.details == null) e.message ?: "Could not load this movie." else null) }
            }
        }
    }

    fun toggleWatched() {
        val connection = connection ?: return
        val current = _state.value.details ?: return
        val target = !current.isWatched
        viewModelScope.launch {
            try {
                DuskGraph.serverApi.setWatched(connection, current.ratingKey, target)
                val refreshed = DuskGraph.serverApi.details(connection, current.ratingKey)
                _state.update { it.copy(details = refreshed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlexException) {
                // Sin conexión con el servidor: se deja el estado anterior.
            } catch (e: Exception) {
                // Igual que arriba.
            }
        }
    }

    companion object {
        fun factory(route: Route.MovieDetail) = viewModelFactory {
            initializer { MovieDetailViewModel(route) }
        }
    }
}
