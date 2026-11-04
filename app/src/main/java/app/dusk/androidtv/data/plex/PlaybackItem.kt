package app.dusk.androidtv.data.plex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Una pista (audio o subtítulo) de la parte reproducida (`Stream` de Plex). Los títulos que se muestran
 * son los del original: `AudioTrack` / `SubtitleTrack`.
 */
data class PlexStreamInfo(
    val id: String,
    val displayTitle: String,
    val language: String?,
    val codec: String?,
    val channels: Int?,
    val isDefault: Boolean,
    val isSelected: Boolean,
    val isForced: Boolean,
    /** Ruta del subtítulo externo (`key`); `null` si está dentro del fichero. */
    val externalKey: String?,
) {
    val isExternal: Boolean get() = externalKey != null

    /** Título corto: sin el bitrate que Plex añade tras " @ " (`compactDisplayTitle`). */
    val compactTitle: String get() = displayTitle.substringBefore(" @ ").ifEmpty { displayTitle }

    /** Detalle bajo el título de un audio: el título completo si trae bitrate; si no, el idioma que falte. */
    val audioDetail: String?
        get() = when {
            displayTitle.contains(" @ ") -> displayTitle
            language != null && !displayTitle.contains(language) -> language
            else -> null
        }

    /** Detalle bajo un subtítulo (`pickerDetailTitle`): idioma, y "External" si es un fichero aparte. */
    val subtitleDetail: String?
        get() = listOfNotNull(language, if (isExternal) "External" else null)
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }

    companion object {
        fun fromJson(json: JsonObject): PlexStreamInfo? {
            val id = json.string("id") ?: return null
            val language = json.string("language")
            val title = json.string("displayTitle") ?: language ?: json.string("codec")?.uppercase() ?: "Track"
            return PlexStreamInfo(
                id = id,
                displayTitle = title,
                language = language,
                codec = json.string("codec"),
                channels = json.int("channels"),
                isDefault = json.bool("default") ?: false,
                isSelected = json.bool("selected") ?: false,
                isForced = json.bool("forced") ?: false,
                externalKey = json.string("key"),
            )
        }
    }
}

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
    val summary: String? = null,
    val resolution: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioStreams: List<PlexStreamInfo> = emptyList(),
    val subtitleStreams: List<PlexStreamInfo> = emptyList(),
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
        private fun streams(part: JsonObject, streamType: Int): List<PlexStreamInfo> =
            (part["Stream"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.filter { it.int("streamType") == streamType }
                ?.mapNotNull { PlexStreamInfo.fromJson(it) }
                ?: emptyList()

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
                    summary = json.string("summary")?.trim()?.takeIf { it.isNotEmpty() },
                    resolution = media.string("videoResolution")?.uppercase(),
                    audioCodec = media.string("audioCodec")?.uppercase(),
                    audioChannels = media.int("audioChannels"),
                    audioStreams = streams(part, 2),
                    subtitleStreams = streams(part, 3),
                )
            }
            return null
        }
    }
}
