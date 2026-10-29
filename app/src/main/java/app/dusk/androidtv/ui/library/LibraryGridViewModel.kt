package app.dusk.androidtv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.GenreOption
import app.dusk.androidtv.data.plex.LibraryKind
import app.dusk.androidtv.data.plex.LibrarySortOption
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.data.plex.PlexLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryGridState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<PlexItem> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
    val sort: LibrarySortOption = LibrarySortOption.TitleAscending,
    val genre: String? = null,
    val genres: List<String> = emptyList(),
    val unwatchedOnly: Boolean = false,
    val libraryCount: Int = 0,
)

/**
 * Rejilla de un tipo de biblioteca (Movies, TV Shows, Videos), versión reducida de
 * `LibraryItemsViewModel`. Si hay varias bibliotecas del mismo tipo (o varios servidores) se
 * recorren una detrás de otra y se quitan los títulos repetidos.
 * TODO: índice A–Z, hoja de filtros, colecciones, orden y "no vistos" recordados por biblioteca.
 */
class LibraryGridViewModel(private val kind: LibraryKind) : ViewModel() {
    private val _state = MutableStateFlow(LibraryGridState())
    val state: StateFlow<LibraryGridState> = _state.asStateFlow()

    private var libraries: List<PlexLibrary> = emptyList()
    private var genreKeys: Map<String, Map<String, String>> = emptyMap() // biblioteca -> título -> clave
    private var libraryIndex = 0
    private var libraryStart = 0
    private var generation = 0
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            DuskGraph.content.libraries
                .map { all -> all.filter { it.kind == kind } }
                .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
                .collect { matching ->
                    libraries = matching
                    _state.update { it.copy(libraryCount = matching.size) }
                    loadGenres()
                    reload()
                }
        }
    }

    fun setSort(sort: LibrarySortOption) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun setGenre(genre: String?) {
        if (genre == _state.value.genre) return
        _state.update { it.copy(genre = genre) }
        reload()
    }

    fun setUnwatchedOnly(value: Boolean) {
        if (value == _state.value.unwatchedOnly) return
        _state.update { it.copy(unwatchedOnly = value) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        generation++
        libraryIndex = 0
        libraryStart = 0
        _state.update { it.copy(items = emptyList(), hasMore = libraries.isNotEmpty(), isLoading = true, error = null) }
        if (libraries.isEmpty()) {
            _state.update { it.copy(isLoading = false, hasMore = false) }
            return
        }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (loadJob?.isActive == true || !current.hasMore) return
        val gen = generation
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = it.items.isNotEmpty()) }
            try {
                val collected = mutableListOf<PlexItem>()
                // Se rellena una página aunque alguna biblioteca esté vacía o no tenga ese género.
                while (collected.size < PAGE_SIZE && libraryIndex < libraries.size) {
                    val library = libraries[libraryIndex]
                    val connection = DuskGraph.content.connectionFor(library.serverId)
                    val filters = filtersFor(library)
                    if (connection == null || filters == null) {
                        libraryIndex++
                        libraryStart = 0
                        continue
                    }
                    val page = DuskGraph.serverApi.libraryItems(
                        connection = connection,
                        sectionId = library.key,
                        start = libraryStart,
                        size = PAGE_SIZE,
                        sort = _state.value.sort.plexValue,
                        filters = filters,
                    )
                    collected += page.items
                    libraryStart += page.items.size
                    val total = page.totalSize
                    val exhausted = page.items.isEmpty() || (total != null && libraryStart >= total)
                    if (exhausted) {
                        libraryIndex++
                        libraryStart = 0
                    }
                }
                if (gen != generation) return@launch
                _state.update { state ->
                    val merged = (state.items + collected).distinctBy { it.contentKey }
                    state.copy(
                        items = merged,
                        hasMore = libraryIndex < libraries.size,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = false,
                            error = if (it.items.isEmpty()) e.message ?: "Could not load this library." else null,
                        )
                    }
                }
            }
        }
    }

    /** Parámetros de filtro para una biblioteca, o `null` si el género elegido no existe en ella. */
    private fun filtersFor(library: PlexLibrary): Map<String, String>? {
        val state = _state.value
        val filters = mutableMapOf<String, String>()
        state.genre?.let { genre ->
            val key = genreKeys[library.id]?.get(genre) ?: return null
            filters["genre"] = key
        }
        if (state.unwatchedOnly) {
            // En series el filtro es sobre sus episodios (`unwatchedLeaves`).
            filters[if (kind == LibraryKind.Show) "unwatchedLeaves" else "unwatched"] = "1"
        }
        return filters
    }

    private suspend fun loadGenres() {
        val loaded = coroutineScope {
            libraries.map { library ->
                async {
                    val connection = DuskGraph.content.connectionFor(library.serverId)
                    val genres: List<GenreOption> = if (connection == null) {
                        emptyList()
                    } else {
                        try {
                            DuskGraph.serverApi.libraryGenres(connection, library.key)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    library.id to genres.associate { it.title to it.key }
                }
            }.awaitAll()
        }
        genreKeys = loaded.toMap()
        val titles = loaded.flatMap { it.second.keys }.distinct().sortedBy { it.lowercase() }
        _state.update { it.copy(genres = titles) }
    }

    companion object {
        private const val PAGE_SIZE = 56

        fun factory(kind: LibraryKind) = viewModelFactory {
            initializer { LibraryGridViewModel(kind) }
        }
    }
}
