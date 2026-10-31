package app.dusk.androidtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.SearchGroup
import app.dusk.androidtv.data.plex.SearchMerge
import app.dusk.androidtv.data.plex.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SearchState(
    val query: String = "",
    val results: List<SearchGroup> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

/**
 * Búsqueda (`SearchViewModel`): espera 300 ms tras la última tecla, lanza la consulta a todos los
 * servidores conectados a la vez y republica la fusión según contesta cada uno, para que los
 * resultados de un servidor local estén en pantalla mientras uno remoto sigue buscando.
 * TODO: Seerr (pedidos), listas, ficha de personas.
 */
class SearchViewModel : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** Último póster enfocado: al volver desde un detalle el foco regresa a él. */
    var lastFocusedKey: String? = null
    var lastFocusedGroup: Int = 0
    var lastFocusedCard: Int = 0

    private var searchJob: Job? = null

    init {
        // Un servidor que se conecta o cae cambia qué significa "buscar": se repite la consulta.
        viewModelScope.launch {
            DuskGraph.content.connections
                .map { it.keys }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (_state.value.hasSearched) searchDebounced() }
        }
    }

    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query) }
        searchDebounced()
    }

    fun append(text: String) = setQuery(_state.value.query + text)

    fun backspace() {
        val current = _state.value.query
        if (current.isNotEmpty()) setQuery(current.dropLast(1))
    }

    fun retry() = searchDebounced()

    private fun searchDebounced() {
        searchJob?.cancel()
        val trimmed = _state.value.query.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(results = emptyList(), error = null, hasSearched = false, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch(trimmed)
        }
    }

    private suspend fun performSearch(query: String) {
        val connections: List<ServerConnection> = DuskGraph.content.connections.value.values.toList()
        _state.update { it.copy(isSearching = true, error = null) }
        if (connections.isEmpty()) {
            _state.update { it.copy(results = emptyList(), hasSearched = true, isSearching = false) }
            return
        }

        val resultsByServer = arrayOfNulls<List<SearchGroup>>(connections.size)
        val mutex = Mutex()
        var failures = 0
        var firstFailure: String? = null
        try {
            coroutineScope {
                connections.forEachIndexed { index, connection ->
                    launch {
                        val outcome = try {
                            Result.success(DuskGraph.serverApi.search(connection, query))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                        mutex.withLock {
                            outcome.onSuccess { resultsByServer[index] = it }
                            outcome.onFailure {
                                failures++
                                firstFailure = firstFailure ?: it.message
                            }
                            val merged = SearchMerge.merge(resultsByServer.map { it.orEmpty() })
                            _state.update { it.copy(results = merged, hasSearched = true) }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
        // Un error solo si TODOS los servidores fallaron y no hay nada que mostrar.
        _state.update {
            it.copy(
                isSearching = false,
                hasSearched = true,
                error = if (it.results.isEmpty() && failures == connections.size) firstFailure ?: "Search failed." else null,
            )
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SearchViewModel() } }
    }
}
