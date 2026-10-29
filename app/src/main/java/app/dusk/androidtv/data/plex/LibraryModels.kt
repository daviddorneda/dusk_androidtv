package app.dusk.androidtv.data.plex

/** Campos por los que se puede ordenar una biblioteca (`LibrarySortField` del original). */
enum class LibrarySortField(
    val title: String,
    val plexKey: String,
    val descendingTitle: String,
    val ascendingTitle: String,
) {
    Title("Title", "titleSort", "Z to A", "A to Z"),
    ReleaseDate("Release Date", "originallyAvailableAt", "Newest First", "Oldest First"),
    Year("Year", "year", "Newest First", "Oldest First"),
    AddedAt("Date Added", "addedAt", "Newest First", "Oldest First"),
    LastEpisodeAdded("Episode Added", "episode.addedAt", "Newest First", "Oldest First"),
    UserRating("Your Rating", "userRating", "Highest First", "Lowest First"),
    CriticRating("Critic Rating", "rating", "Highest First", "Lowest First"),
    AudienceRating("Audience Rating", "audienceRating", "Highest First", "Lowest First"),
    Duration("Duration", "duration", "Longest First", "Shortest First"),
    LastViewed("Last Watched", "lastViewedAt", "Most Recent First", "Least Recent First"),
    Random("Random", "random", "", "");

    /** El título se lee de la A a la Z; el resto empieza por lo más nuevo, alto o largo. */
    val defaultsToDescending: Boolean get() = this != Title

    /** "Random" no tiene orden que invertir. */
    val hasDirection: Boolean get() = this != Random

    companion object {
        fun fieldsFor(kind: LibraryKind): List<LibrarySortField> = when (kind) {
            LibraryKind.Video -> listOf(ReleaseDate, AddedAt, Title, Duration, LastViewed, Random)
            LibraryKind.Show -> listOf(
                Title, ReleaseDate, Year, AddedAt, LastEpisodeAdded,
                UserRating, CriticRating, AudienceRating, LastViewed, Random,
            )
            else -> listOf(
                Title, ReleaseDate, Year, AddedAt, UserRating,
                CriticRating, AudienceRating, Duration, LastViewed, Random,
            )
        }
    }
}

data class LibrarySortOption(val sortField: LibrarySortField, val isDescending: Boolean) {
    /** Etiqueta del chip y del menú: "Title A to Z", "Date Added · Newest First", "Random". */
    val title: String
        get() = when {
            !sortField.hasDirection -> sortField.title
            sortField == LibrarySortField.Title ->
                "Title " + if (isDescending) sortField.descendingTitle else sortField.ascendingTitle
            else -> "${sortField.title} · " + if (isDescending) sortField.descendingTitle else sortField.ascendingTitle
        }

    val plexValue: String
        get() = when {
            !sortField.hasDirection -> sortField.plexKey
            isDescending -> "${sortField.plexKey}:desc"
            else -> sortField.plexKey
        }

    companion object {
        val TitleAscending = LibrarySortOption(LibrarySortField.Title, isDescending = false)

        /** Las dos direcciones de cada campo, con la habitual primero (como el menú del original). */
        fun optionsFor(kind: LibraryKind): List<LibrarySortOption> =
            LibrarySortField.fieldsFor(kind).flatMap { field ->
                if (!field.hasDirection) {
                    listOf(LibrarySortOption(field, false))
                } else {
                    val first = field.defaultsToDescending
                    listOf(LibrarySortOption(field, first), LibrarySortOption(field, !first))
                }
            }
    }
}

/** Un género de una biblioteca: `key` es el valor del filtro `genre` en ese servidor. */
data class GenreOption(val title: String, val key: String)

data class LibraryPage(val items: List<PlexItem>, val totalSize: Int?)
