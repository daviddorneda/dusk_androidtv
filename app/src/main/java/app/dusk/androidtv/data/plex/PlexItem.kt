package app.dusk.androidtv.data.plex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.max

enum class PlexMediaType {
    Movie, Show, Person, Season, Episode, Clip, Artist, Album, Track, Collection, Unknown;

    companion object {
        fun from(raw: String?): PlexMediaType = when (raw?.lowercase()) {
            "movie" -> Movie
            "show" -> Show
            "person" -> Person
            "season" -> Season
            "episode" -> Episode
            "clip" -> Clip
            "artist" -> Artist
            "album" -> Album
            "track" -> Track
            "collection" -> Collection
            else -> Unknown
        }
    }
}

/** Textos de presentación (equivale a `MediaTextFormatter`). */
object MediaText {
    fun seasonEpisodeLabel(season: Int?, episode: Int?, separator: String = " · "): String? = when {
        season != null && episode != null -> "Season $season${separator}Episode $episode"
        season != null -> "Season $season"
        episode != null -> "Episode $episode"
        else -> null
    }

    /** "1h 5m" / "42m". */
    fun playbackDuration(milliseconds: Int?): String? {
        if (milliseconds == null || milliseconds <= 0) return null
        val totalMinutes = milliseconds / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun seasonCount(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "1 season"
        else -> "$count seasons"
    }

    fun progress(durationMs: Int?, offsetMs: Int?): Double? {
        if (durationMs == null || durationMs <= 0 || offsetMs == null || offsetMs <= 0) return null
        return (offsetMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
    }
}

/**
 * Elemento de biblioteca (película, serie, temporada, episodio...). Equivale a `PlexItem`.
 * Los identificadores de Plex colisionan entre servidores: la identidad es (serverId, ratingKey).
 */
data class PlexItem(
    val serverId: String?,
    val ratingKey: String,
    val key: String,
    val type: PlexMediaType,
    val subtype: String?,
    val librarySectionId: String?,
    val title: String,
    val editionTitle: String?,
    val summary: String?,
    val year: Int?,
    val thumb: String?,
    val art: String?,
    val banner: String?,
    val clearLogo: String?,
    val duration: Int?,
    val viewCount: Int?,
    val viewOffset: Int?,
    val lastViewedAt: Long?,
    val addedAt: Long?,
    val index: Int?,
    val parentIndex: Int?,
    val parentRatingKey: String?,
    val parentTitle: String?,
    val parentThumb: String?,
    val grandparentRatingKey: String?,
    val grandparentTitle: String?,
    val grandparentThumb: String?,
    val grandparentArt: String?,
    val leafCount: Int?,
    val viewedLeafCount: Int?,
    val childCount: Int?,
    val guid: String?,
) {
    val storageKey: String get() = "${serverId.orEmpty()}|$ratingKey"

    val isClip: Boolean get() = type == PlexMediaType.Clip || subtype.equals("clip", ignoreCase = true)

    private val isPlayed: Boolean get() = (viewCount ?: 0) > 0

    val posterProgress: Double? get() = MediaText.progress(duration, viewOffset)

    val posterIsWatched: Boolean
        get() = when (type) {
            PlexMediaType.Movie, PlexMediaType.Episode, PlexMediaType.Clip -> isPlayed
            PlexMediaType.Show, PlexMediaType.Season -> {
                val leaves = leafCount ?: 0
                leaves > 0 && (viewedLeafCount ?: 0) >= leaves
            }
            else -> false
        }

    private fun withEdition(base: String?): String? =
        listOfNotNull(base?.takeIf { it.isNotEmpty() }, editionTitle?.takeIf { it.isNotEmpty() })
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }

    /** Título del héroe y de las tarjetas de "continuar viendo": la serie para un episodio. */
    val continueWatchingDisplayTitle: String
        get() = if (type == PlexMediaType.Episode && grandparentTitle != null) grandparentTitle else title

    val posterCardTitle: String
        get() = when (type) {
            PlexMediaType.Season -> parentTitle?.takeIf { it.isNotEmpty() } ?: title
            PlexMediaType.Episode -> grandparentTitle?.takeIf { it.isNotEmpty() } ?: title
            else -> title
        }

    val standardPosterSubtitle: String?
        get() = when (type) {
            PlexMediaType.Movie -> withEdition(year?.toString())
            PlexMediaType.Show -> childCount?.let { withEdition(MediaText.seasonCount(it)) } ?: withEdition(year?.toString())
            PlexMediaType.Season -> title.takeIf { it.isNotEmpty() } ?: MediaText.seasonEpisodeLabel(index, null)
            PlexMediaType.Episode -> MediaText.seasonEpisodeLabel(parentIndex, index) ?: grandparentTitle
            else -> withEdition(year?.toString())
        }

    /** Ruta del póster (vertical) de la tarjeta. */
    val posterPath: String?
        get() = when (type) {
            PlexMediaType.Episode -> grandparentThumb ?: parentThumb ?: thumb
            PlexMediaType.Season -> thumb ?: parentThumb
            else -> thumb ?: parentThumb ?: grandparentThumb
        }

    /** Fondo del héroe (`heroBackgroundPath`). */
    val heroBackgroundPath: String?
        get() = when (type) {
            PlexMediaType.Episode -> grandparentArt ?: art ?: banner ?: thumb ?: grandparentThumb
            PlexMediaType.Season -> art ?: banner ?: thumb ?: parentThumb
            else -> banner ?: art ?: thumb ?: parentThumb ?: grandparentArt ?: grandparentThumb
        }

    /** Subtítulo del héroe: el título del episodio si difiere del de la serie. */
    val heroEpisodeTitle: String?
        get() = if (type == PlexMediaType.Episode && title != continueWatchingDisplayTitle) title else null

    val heroMetadata: List<String>
        get() {
            val parts = mutableListOf<String>()
            when (type) {
                PlexMediaType.Episode -> MediaText.seasonEpisodeLabel(parentIndex, index)?.let(parts::add)
                PlexMediaType.Movie -> {
                    year?.let { parts += it.toString() }
                    editionTitle?.let(parts::add)
                }
                else -> Unit
            }
            heroDurationText()?.let(parts::add)
            return parts
        }

    private fun heroDurationText(): String? {
        val total = duration ?: return null
        if (total <= 0) return null
        val offset = viewOffset
        if (offset != null && offset > 0) {
            val remaining = max(total - offset, 0)
            val text = MediaText.playbackDuration(remaining) ?: return null
            return if (remaining > 0) "$text left" else text
        }
        return MediaText.playbackDuration(total)
    }

    val heroPrimaryActionTitle: String
        get() {
            val offset = viewOffset
            if (offset != null && offset > 0) {
                MediaText.playbackDuration(offset)?.let { return "Resume from $it" }
            }
            return "Play"
        }

    /** Clave de contenido para fusionar el mismo título de varios servidores. */
    val contentKey: String
        get() = guid?.takeIf { it.isNotEmpty() }
            ?: "${type.name}|${title.lowercase()}|${year ?: ""}|${parentIndex ?: ""}|${index ?: ""}"

    companion object {
        fun fromJson(json: JsonObject, serverId: String?): PlexItem? {
            val ratingKey = json.string("ratingKey") ?: return null
            val key = json.string("key") ?: return null
            val title = json.string("title") ?: return null
            return PlexItem(
                serverId = serverId,
                ratingKey = ratingKey,
                key = key,
                type = PlexMediaType.from(json.string("type")),
                subtype = json.string("subtype"),
                librarySectionId = json.string("librarySectionID"),
                title = title,
                editionTitle = json.string("editionTitle")?.trim()?.takeIf { it.isNotEmpty() },
                summary = json.string("summary")?.trim()?.takeIf { it.isNotEmpty() },
                year = json.int("year"),
                thumb = json.string("thumb"),
                art = json.string("art"),
                banner = json.string("banner"),
                clearLogo = json.string("clearLogo") ?: clearLogoFromImages(json["Image"]),
                duration = json.int("duration"),
                viewCount = json.int("viewCount"),
                viewOffset = json.int("viewOffset"),
                lastViewedAt = json.string("lastViewedAt")?.toLongOrNull(),
                addedAt = json.string("addedAt")?.toLongOrNull(),
                index = json.int("index"),
                parentIndex = json.int("parentIndex"),
                parentRatingKey = json.string("parentRatingKey"),
                parentTitle = json.string("parentTitle"),
                parentThumb = json.string("parentThumb"),
                grandparentRatingKey = json.string("grandparentRatingKey"),
                grandparentTitle = json.string("grandparentTitle"),
                grandparentThumb = json.string("grandparentThumb"),
                grandparentArt = json.string("grandparentArt"),
                leafCount = json.int("leafCount"),
                viewedLeafCount = json.int("viewedLeafCount"),
                childCount = json.int("childCount"),
                guid = json.string("guid"),
            )
        }

        private fun clearLogoFromImages(element: kotlinx.serialization.json.JsonElement?): String? {
            val array = element as? JsonArray ?: return null
            for (entry in array) {
                val obj = entry as? JsonObject ?: continue
                if (obj.string("type") == "clearLogo") obj.string("url")?.let { return it }
            }
            return null
        }
    }
}
