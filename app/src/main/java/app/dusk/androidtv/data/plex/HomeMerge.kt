package app.dusk.androidtv.data.plex

/** Filas de Home y héroe ya fusionados de todos los servidores. */
data class HomeShelf(val id: String, val title: String, val items: List<PlexItem>)

data class HomeContent(
    val isLoading: Boolean = true,
    val heroItems: List<PlexItem> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
) {
    val isEmpty: Boolean get() = heroItems.isEmpty() && shelves.isEmpty()
}

/** Lo que devolvió un servidor para Home. */
data class ServerHomePayload(
    val connection: ServerConnection,
    val hubs: List<PlexHub>?,
    val continueWatching: List<PlexItem>?,
    val libraries: List<PlexLibrary>?,
    val errors: List<String> = emptyList(),
)

/**
 * Construye Home a partir de varios servidores (versión simplificada de `HubMerge`,
 * `ContinueWatchingMerge` y `HomeHubFilter`).
 * TODO: orden por biblioteca (`HomeHubArrangement`), fusión por guid de filas por sección,
 * lista de seguimiento (Watchlist) y estantes personalizados.
 */
object HomeMerge {
    private const val HERO_LIMIT = 10
    private const val RECENTLY_ADDED_LIMIT = 20

    /** Plex cubre continuar viendo con el héroe y las listas no son destino de Dusk. */
    fun shouldHide(hub: PlexHub): Boolean {
        val fields = listOfNotNull(hub.title, hub.key, hub.hubIdentifier).map { it.lowercase() }
        return fields.any {
            it.contains("continue watching") || it.contains("continuewatching") ||
                it.contains("on deck") || it.contains("ondeck") || it.contains("playlist")
        }
    }

    fun shouldHide(item: PlexItem): Boolean = when (item.type) {
        PlexMediaType.Artist, PlexMediaType.Album, PlexMediaType.Track, PlexMediaType.Unknown -> true
        else -> item.key.lowercase().contains("/playlists/")
    }

    fun build(payloads: List<ServerHomePayload>): HomeContent {
        // Continuar viendo: lo más reciente primero, sin repetir el mismo título de dos servidores.
        val continueWatching = payloads
            .flatMap { it.continueWatching.orEmpty() }
            .filterNot(::shouldHide)
            .sortedByDescending { it.lastViewedAt ?: 0L }
            .distinctBy { it.contentKey }
        val hero = continueWatching.filterNot { it.isClip }.take(HERO_LIMIT)

        // Filas: un estante por título de fila; si varios servidores lo tienen, se unen.
        val grouped = LinkedHashMap<String, MutableList<PlexHub>>()
        for (payload in payloads) {
            for (hub in payload.hubs.orEmpty()) {
                if (shouldHide(hub)) continue
                grouped.getOrPut("${hub.type}|${hub.title.lowercase()}") { mutableListOf() }.add(hub)
            }
        }

        val shelves = grouped.values.mapNotNull { hubs ->
            val first = hubs.first()
            val visible = hubs.flatMap { it.items }.filterNot(::shouldHide).distinctBy { it.contentKey }
            val items = if (first.isRecentlyAdded) {
                oneCardPerShow(visible.sortedByDescending { it.addedAt ?: 0L }).take(RECENTLY_ADDED_LIMIT)
            } else {
                visible
            }
            if (items.isEmpty()) null else HomeShelf(id = "${first.hubIdentifier ?: first.title}|${first.serverId}", title = first.title, items = items)
        }

        return HomeContent(isLoading = false, heroItems = hero, shelves = shelves)
    }

    /** Plex lista una temporada nueva y un episodio nuevo de la misma serie por separado; se queda el más reciente. */
    private fun oneCardPerShow(items: List<PlexItem>): List<PlexItem> {
        val seen = HashSet<String>()
        return items.filter { item ->
            val showKey = when (item.type) {
                PlexMediaType.Show -> item.ratingKey
                PlexMediaType.Season -> item.parentRatingKey
                PlexMediaType.Episode -> item.grandparentRatingKey
                else -> null
            } ?: return@filter true
            seen.add("${item.serverId}|$showKey")
        }
    }
}
