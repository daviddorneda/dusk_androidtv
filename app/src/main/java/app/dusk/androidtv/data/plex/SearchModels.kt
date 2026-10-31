package app.dusk.androidtv.data.plex

/** Grupo de resultados de un tipo (`PlexSearchResult`): una fila de pósters en la pantalla. */
data class SearchGroup(
    val id: String,
    val title: String,
    val type: String?,
    val items: List<PlexItem>,
) {
    /** Los grupos de vídeos sueltos usan miniaturas 16:9 en vez de pósters 2:3. */
    val isAllClips: Boolean get() = items.isNotEmpty() && items.all { it.isClip }

    companion object {
        /**
         * Plex agrupa los vídeos sueltos dentro del grupo de películas (los clips dicen `movie`), lo que
         * les impondría el póster 2:3. Un grupo mixto se divide en el original y un grupo "Videos"; un
         * grupo solo de clips se renombra a "Videos" (`PlexSearchResult.results(from:)`).
         */
        fun fromHub(hub: PlexHub): List<SearchGroup> {
            val clips = hub.items.filter { it.isClip }
            val key = hub.type ?: hub.title
            if (clips.isEmpty()) return listOf(SearchGroup(key, hub.title, hub.type, hub.items))
            val posters = hub.items.filterNot { it.isClip }
            return buildList {
                if (posters.isNotEmpty()) add(SearchGroup(key, hub.title, hub.type, posters))
                add(SearchGroup("clip", "Videos", "clip", clips))
            }
        }
    }
}

/**
 * Resultados de varios servidores en una sola lista (`SearchMerge`). Es una función pura de las
 * listas recibidas: el orden que el usuario ya está leyendo no depende de qué servidor contestó
 * primero. Los grupos del mismo tipo se unen intercalando los elementos de cada servidor.
 */
object SearchMerge {
    fun merge(lists: List<List<SearchGroup>>): List<SearchGroup> {
        val grouped = LinkedHashMap<String, MutableList<SearchGroup>>()
        for (list in lists) {
            for (group in list) grouped.getOrPut(group.id) { mutableListOf() }.add(group)
        }
        return grouped.values.mapNotNull { groups ->
            val representative = groups.first()
            val items = interleave(groups.map { it.items })
            if (items.isEmpty()) null else representative.copy(items = items)
        }
    }

    private fun interleave(lists: List<List<PlexItem>>): List<PlexItem> {
        val merged = mutableListOf<PlexItem>()
        val seen = HashSet<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (index in 0 until longest) {
            for (list in lists) {
                val item = list.getOrNull(index) ?: continue
                if (seen.add(item.contentKey)) merged += item
            }
        }
        return merged
    }
}
