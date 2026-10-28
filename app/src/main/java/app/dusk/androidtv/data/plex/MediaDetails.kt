package app.dusk.androidtv.data.plex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Miembro del reparto (`PlexRole`). */
data class PlexRole(val name: String, val roleName: String?, val thumb: String?)

/**
 * Metadatos completos de un elemento (`GET /library/metadata/{ratingKey}`), versión reducida de
 * `PlexMediaDetails`. TODO: valoraciones por fuente, extras/tráiler, colecciones, versiones.
 */
data class MediaDetails(
    val serverId: String,
    val ratingKey: String,
    val key: String,
    val type: PlexMediaType,
    val title: String,
    val editionTitle: String?,
    val summary: String?,
    val year: Int?,
    val duration: Int?,
    val viewOffset: Int?,
    val viewCount: Int?,
    val thumb: String?,
    val art: String?,
    val clearLogo: String?,
    val contentRating: String?,
    val studio: String?,
    val genres: List<String>,
    val directors: List<String>,
    val roles: List<PlexRole>,
    val mediaInfo: String?,
) {
    val isWatched: Boolean get() = (viewCount ?: 0) > 0

    val formattedDuration: String? get() = MediaText.playbackDuration(duration)

    val formattedResume: String?
        get() = viewOffset?.takeIf { it > 0 }?.let { MediaText.playbackDuration(it) }

    val genreText: String? get() = genres.take(3).joinToString(", ").takeIf { it.isNotEmpty() }

    val directorText: String? get() = directors.joinToString(", ").takeIf { it.isNotEmpty() }

    /** Línea bajo el título: año · edición · clasificación · duración. */
    val tagline: String?
        get() = listOfNotNull(year?.toString(), editionTitle, contentRating, formattedDuration)
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }

    companion object {
        fun fromJson(json: JsonObject, serverId: String): MediaDetails? {
            val ratingKey = json.string("ratingKey") ?: return null
            val title = json.string("title") ?: return null
            return MediaDetails(
                serverId = serverId,
                ratingKey = ratingKey,
                key = json.string("key") ?: "/library/metadata/$ratingKey",
                type = PlexMediaType.from(json.string("type")),
                title = title,
                editionTitle = json.string("editionTitle")?.trim()?.takeIf { it.isNotEmpty() },
                summary = json.string("summary")?.trim()?.takeIf { it.isNotEmpty() },
                year = json.int("year"),
                duration = json.int("duration"),
                viewOffset = json.int("viewOffset"),
                viewCount = json.int("viewCount"),
                thumb = json.string("thumb"),
                art = json.string("art"),
                clearLogo = json.string("clearLogo") ?: clearLogoFromImages(json["Image"]),
                contentRating = json.string("contentRating"),
                studio = json.string("studio"),
                genres = tags(json["Genre"]),
                directors = tags(json["Director"]),
                roles = roles(json["Role"]),
                mediaInfo = mediaInfo(json["Media"]),
            )
        }

        private fun tags(element: JsonElement?): List<String> =
            (element as? JsonArray)?.mapNotNull { (it as? JsonObject)?.string("tag") } ?: emptyList()

        private fun roles(element: JsonElement?): List<PlexRole> =
            (element as? JsonArray)?.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val name = obj.string("tag") ?: return@mapNotNull null
                PlexRole(name = name, roleName = obj.string("role"), thumb = obj.string("thumb"))
            } ?: emptyList()

        private fun clearLogoFromImages(element: JsonElement?): String? {
            val array = element as? JsonArray ?: return null
            for (entry in array) {
                val obj = entry as? JsonObject ?: continue
                if (obj.string("type") == "clearLogo") obj.string("url")?.let { return it }
            }
            return null
        }

        /** "1080 · H264 · EAC3 · 6ch" a partir de la primera versión del medio. */
        private fun mediaInfo(element: JsonElement?): String? {
            val media = (element as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val parts = mutableListOf<String>()
            media.string("videoResolution")?.uppercase()?.let(parts::add)
            media.string("videoCodec")?.uppercase()?.let(parts::add)
            media.string("audioCodec")?.uppercase()?.let(parts::add)
            media.int("audioChannels")?.let { parts += "${it}ch" }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }
}
