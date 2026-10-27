package app.dusk.androidtv.data.plex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Fila ("hub") de la pantalla de inicio de Plex, p. ej. "Recently Added Movies". */
data class PlexHub(
    val serverId: String,
    val key: String?,
    val title: String,
    val type: String?,
    val hubIdentifier: String?,
    val more: Boolean,
    val librarySectionId: String?,
    val librarySectionTitle: String?,
    val items: List<PlexItem>,
) {
    val isRecentlyAdded: Boolean
        get() = listOfNotNull(hubIdentifier, key).any { it.lowercase().contains("recentlyadded") } ||
            title.lowercase().startsWith("recently added")

    companion object {
        fun fromJson(json: JsonObject, serverId: String): PlexHub? {
            val title = json.string("title") ?: return null
            val items = mutableListOf<PlexItem>()
            for (field in listOf("Metadata", "Directory")) {
                (json[field] as? JsonArray)?.forEach { element ->
                    (element as? JsonObject)?.let { PlexItem.fromJson(it, serverId) }?.let(items::add)
                }
            }
            return PlexHub(
                serverId = serverId,
                key = json.string("key"),
                title = title,
                type = json.string("type"),
                hubIdentifier = json.string("hubIdentifier"),
                more = json.bool("more") ?: false,
                librarySectionId = json.string("librarySectionID"),
                librarySectionTitle = json.string("librarySectionTitle"),
                items = items,
            )
        }
    }
}

enum class LibraryKind(val tabTitle: String) {
    Movie("Movies"), Show("TV Shows"), Video("Videos"), Photo("Photos"), Other("Other")
}

/** Sección de biblioteca de un servidor (`GET /library/sections`). */
data class PlexLibrary(
    val serverId: String,
    val key: String,
    val title: String,
    val type: String,
    val agent: String?,
    val scanner: String?,
) {
    val id: String get() = "$serverId|$key"

    /** "Other Videos" llega como `movie`: se distingue por agente sin metadatos o escáner de vídeo. */
    val kind: LibraryKind
        get() = when (type) {
            "movie" -> if (isOtherVideos) LibraryKind.Video else LibraryKind.Movie
            "show" -> LibraryKind.Show
            "photo" -> LibraryKind.Photo
            else -> LibraryKind.Other
        }

    private val isOtherVideos: Boolean
        get() = agent?.lowercase()?.endsWith("agents.none") == true ||
            scanner?.lowercase()?.contains("video files") == true

    companion object {
        fun fromJson(json: JsonObject, serverId: String): PlexLibrary? {
            val key = json.string("key") ?: return null
            return PlexLibrary(
                serverId = serverId,
                key = key,
                title = json.string("title") ?: key,
                type = json.string("type") ?: return null,
                agent = json.string("agent"),
                scanner = json.string("scanner"),
            )
        }
    }
}
