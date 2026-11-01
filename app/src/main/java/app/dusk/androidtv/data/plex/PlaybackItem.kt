package app.dusk.androidtv.data.plex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Lo mínimo para reproducir un elemento: qué parte del medio abrir y cómo mostrarlo en el reproductor.
 * Se obtiene de `GET /library/metadata/{id}?checkFiles=1`, de modo que cada parte trae si es accesible
 * (`PlexMedia.firstAvailablePart` en el original).
 * TODO: elección de versión, pistas de audio y subtítulos, marcadores de intro y créditos.
 */
data class PlaybackItem(
    val serverId: String,
    val ratingKey: String,
    val type: PlexMediaType,
    val title: String,
    val grandparentTitle: String?,
    val parentIndex: Int?,
    val index: Int?,
    val durationMs: Long,
    val viewOffsetMs: Long,
    val partKey: String,
    val container: String?,
    val videoCodec: String?,
    val thumb: String?,
    val art: String?,
) {
    /** Título principal del reproductor: la serie para un episodio, la película en otro caso. */
    val displayTitle: String
        get() = if (type == PlexMediaType.Episode && !grandparentTitle.isNullOrEmpty()) grandparentTitle else title

    /** "Season 1 · Episode 3 · Título" para episodios; el año u otros datos no se muestran aquí. */
    val displaySubtitle: String?
        get() = if (type == PlexMediaType.Episode) {
            listOfNotNull(MediaText.seasonEpisodeLabel(parentIndex, index), title.takeIf { it != displayTitle })
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }
        } else {
            null
        }

    companion object {
        fun fromJson(json: JsonObject, serverId: String): PlaybackItem? {
            val ratingKey = json.string("ratingKey") ?: return null
            val title = json.string("title") ?: return null
            val medias = (json["Media"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
            // Primera versión con una parte disponible (existe y es accesible).
            for (media in medias) {
                val parts = (media["Part"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: continue
                val part = parts.firstOrNull { (it.bool("accessible") ?: true) && (it.bool("exists") ?: true) } ?: continue
                val partKey = part.string("key") ?: continue
                return PlaybackItem(
                    serverId = serverId,
                    ratingKey = ratingKey,
                    type = PlexMediaType.from(json.string("type")),
                    title = title,
                    grandparentTitle = json.string("grandparentTitle"),
                    parentIndex = json.int("parentIndex"),
                    index = json.int("index"),
                    durationMs = (json.int("duration") ?: media.int("duration") ?: 0).toLong(),
                    viewOffsetMs = (json.int("viewOffset") ?: 0).toLong(),
                    partKey = partKey,
                    container = media.string("container") ?: part.string("container"),
                    videoCodec = media.string("videoCodec"),
                    thumb = json.string("thumb"),
                    art = json.string("art"),
                )
            }
            return null
        }
    }
}
