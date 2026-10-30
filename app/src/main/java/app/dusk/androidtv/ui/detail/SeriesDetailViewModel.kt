package app.dusk.androidtv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.MediaDetails
import app.dusk.androidtv.data.plex.PlexItem
import app.dusk.androidtv.data.plex.PlexMediaType
import app.dusk.androidtv.data.plex.ServerConnection
import app.dusk.androidtv.ui.nav.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val show: MediaDetails? = null,
    val seasons: List<PlexItem> = emptyList(),
    val selectedSeasonKey: String? = null,
    val seasonDetails: MediaDetails? = null,
    val episodes: List<PlexItem> = emptyList(),
    val anchorEpisodeKey: String? = null,
    val focusedEpisodeKey: String? = null,
    val focusedEpisodeDetails: MediaDetails? = null,
    val isSwitchingSeason: Boolean = false,
) {
    val focusedEpisode: PlexItem?
        get() = episodes.firstOrNull { it.ratingKey == focusedEpisodeKey }
            ?: episodes.firstOrNull { it.ratingKey == anchorEpisodeKey }
            ?: episodes.firstOrNull()

    val isSeasonWatched: Boolean get() = episodes.isNotEmpty() && episodes.all { it.isWatchedEpisode }

    /** Pastillas de temporada: solo merecen un foco cuando hay algo que elegir. */
    val seasonPills: List<PlexItem> get() = if (seasons.size > 1) seasons else emptyList()
}

val PlexItem.isWatchedEpisode: Boolean get() = (viewCount ?: 0) > 0
val PlexItem.isPartiallyWatchedEpisode: Boolean get() = (viewOffset ?: 0) > 0 && !isWatchedEpisode

/**
 * Página de serie de tvOS (`SeriesDetailViewModel`): una sola pantalla para serie, temporada y
 * episodio. Se abre en la temporada pedida o, si no, en la primera temporada regular sin terminar,
 * y en el episodio pedido o, si no, en el siguiente por ver.
 * TODO: Seerr, versiones, extras, colecciones y valorar.
 */
class SeriesDetailViewModel(private val route: Route.SeriesDetail) : ViewModel() {
    private val _state = MutableStateFlow(SeriesDetailState())
    val state: StateFlow<SeriesDetailState> = _state.asStateFlow()

    val connection: ServerConnection? get() = DuskGraph.content.connectionFor(route.serverId)

    private var selectionToken = 0
    private var detailsJob: Job? = null
    private val episodeDetailsCache = mutableMapOf<String, MediaDetails>()

    init {
        load()
    }

    fun load() {
        val connection = connection
        if (connection == null) {
            _state.value = SeriesDetailState(isLoading = false, error = "This server is not available right now.")
            return
        }
        _state.update { it.copy(isLoading = it.show == null, error = null) }
        viewModelScope.launch {
            try {
                val (show, seasons) = coroutineScope {
                    val showDeferred = async { DuskGraph.serverApi.details(connection, route.showRatingKey) }
                    val seasonsDeferred = async {
                        DuskGraph.serverApi.children(connection, route.showRatingKey)
                            .filter { it.type == PlexMediaType.Season }
                            .sortedBy { it.index ?: 0 }
                    }
                    showDeferred.await() to seasonsDeferred.await()
                }
                _state.update { it.copy(show = show, seasons = seasons) }
                val seasonKey = route.seasonRatingKey ?: initialSeasonKey(seasons)
                if (seasonKey == null) {
                    _state.update { it.copy(isLoading = false, error = "This show has no seasons.") }
                    return@launch
                }
                openSeason(connection, seasonKey, route.episodeRatingKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = if (it.show == null) e.message ?: "Could not load this show." else null) }
            }
        }
    }

    fun selectSeason(seasonKey: String) {
        val connection = connection ?: return
        if (seasonKey == _state.value.selectedSeasonKey) return
        viewModelScope.launch {
            try {
                openSeason(connection, seasonKey, episodeKey = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSwitchingSeason = false) }
            }
        }
    }

    fun onEpisodeFocused(episodeKey: String) {
        val connection = connection ?: return
        if (_state.value.focusedEpisodeKey == episodeKey) return
        _state.update { it.copy(focusedEpisodeKey = episodeKey, focusedEpisodeDetails = episodeDetailsCache[episodeKey]) }
        if (episodeDetailsCache.containsKey(episodeKey)) return
        // Un pequeño retardo evita pedir los datos de cada tarjeta al recorrer la fila.
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            delay(300)
            loadEpisodeDetails(connection, episodeKey)
        }
    }

    /** Marca la temporada entera como vista o no vista (botón del ojo del banner). */
    fun toggleSeasonWatched() {
        val connection = connection ?: return
        val current = _state.value
        val seasonKey = current.selectedSeasonKey ?: return
        val target = !current.isSeasonWatched
        viewModelScope.launch {
            try {
                DuskGraph.serverApi.setWatched(connection, seasonKey, target)
                episodeDetailsCache.clear()
                refreshSeason(connection, seasonKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin conexión con el servidor: se deja el estado anterior.
            }
        }
    }

    // ---------------------------------------------------------------- Carga

    private suspend fun openSeason(connection: ServerConnection, seasonKey: String, episodeKey: String?) {
        val token = ++selectionToken
        _state.update { it.copy(selectedSeasonKey = seasonKey, isSwitchingSeason = it.seasonDetails != null) }
        val (details, episodes) = coroutineScope {
            val detailsDeferred = async { DuskGraph.serverApi.details(connection, seasonKey) }
            val episodesDeferred = async {
                DuskGraph.serverApi.children(connection, seasonKey)
                    .filter { it.type == PlexMediaType.Episode }
                    .sortedBy { it.index ?: 0 }
            }
            detailsDeferred.await() to episodesDeferred.await()
        }
        // Una elección posterior gana: si el usuario cambió de temporada mientras cargaba, se descarta.
        if (token != selectionToken) return
        val anchor = episodes.firstOrNull { it.ratingKey == episodeKey } ?: nextEpisodeToPlay(episodes)
        _state.update {
            it.copy(
                isLoading = false,
                isSwitchingSeason = false,
                error = null,
                selectedSeasonKey = seasonKey,
                seasonDetails = details,
                episodes = episodes,
                anchorEpisodeKey = anchor?.ratingKey,
                focusedEpisodeKey = anchor?.ratingKey,
                focusedEpisodeDetails = anchor?.let { a -> episodeDetailsCache[a.ratingKey] },
            )
        }
        anchor?.let { if (!episodeDetailsCache.containsKey(it.ratingKey)) loadEpisodeDetails(connection, it.ratingKey) }
    }

    /** Recarga la temporada y la lista de temporadas sin mover el foco del banner. */
    private suspend fun refreshSeason(connection: ServerConnection, seasonKey: String) {
        val (episodes, seasons) = coroutineScope {
            val episodesDeferred = async {
                DuskGraph.serverApi.children(connection, seasonKey)
                    .filter { it.type == PlexMediaType.Episode }
                    .sortedBy { it.index ?: 0 }
            }
            val seasonsDeferred = async {
                DuskGraph.serverApi.children(connection, route.showRatingKey)
                    .filter { it.type == PlexMediaType.Season }
                    .sortedBy { it.index ?: 0 }
            }
            episodesDeferred.await() to seasonsDeferred.await()
        }
        _state.update { it.copy(episodes = episodes, seasons = seasons) }
    }

    private suspend fun loadEpisodeDetails(connection: ServerConnection, episodeKey: String) {
        try {
            val details = DuskGraph.serverApi.details(connection, episodeKey)
            episodeDetailsCache[episodeKey] = details
            _state.update {
                if (it.focusedEpisodeKey == episodeKey) it.copy(focusedEpisodeDetails = details) else it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // El banner se queda con los datos básicos del episodio.
        }
    }

    /** Primero un episodio a medias, luego el primero sin ver, luego el primero (`nextEpisodeToPlay`). */
    private fun nextEpisodeToPlay(episodes: List<PlexItem>): PlexItem? =
        episodes.firstOrNull { it.isPartiallyWatchedEpisode }
            ?: episodes.firstOrNull { !it.isWatchedEpisode }
            ?: episodes.firstOrNull()

    /** Una serie abre en la primera temporada regular que aún tiene algo sin ver. */
    private fun initialSeasonKey(seasons: List<PlexItem>): String? {
        val regular = seasons.filter { (it.index ?: 0) > 0 }
        val candidates = regular.ifEmpty { seasons }
        return (candidates.firstOrNull { !it.posterIsWatched } ?: candidates.firstOrNull())?.ratingKey
    }

    companion object {
        fun factory(route: Route.SeriesDetail) = viewModelFactory {
            initializer { SeriesDetailViewModel(route) }
        }
    }
}
