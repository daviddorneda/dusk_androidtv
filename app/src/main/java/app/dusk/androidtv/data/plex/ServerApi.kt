package app.dusk.androidtv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Peticiones a un servidor concreto (Plex Media Server). Siempre con el token del servidor y
 * contra la dirección que ganó la carrera de conexiones.
 */
class ServerApi(private val plex: PlexClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun container(
        connection: ServerConnection,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        timeoutSeconds: Long = 30,
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = (connection.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
            .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder().url(url).headers(plex.headers(connection.token)).get().build()
        val client = plex.httpClient.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlexHttpException(response.code)
                val body = response.body?.string() ?: throw PlexException("Empty response from ${connection.name}.")
                val root = json.parseToJsonElement(body) as? JsonObject
                (root?.get("MediaContainer") as? JsonObject)
                    ?: throw PlexException("Unexpected response from ${connection.name}.")
            }
        } catch (e: IOException) {
            throw PlexException(e.message ?: "Could not reach ${connection.name}.")
        }
    }

    suspend fun libraries(connection: ServerConnection): List<PlexLibrary> {
        val container = container(connection, "/library/sections")
        return (container["Directory"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { obj -> PlexLibrary.fromJson(obj, connection.serverId) } }
            ?: emptyList()
    }

    /** `GET /hubs`: las filas de Home de ese servidor. */
    suspend fun hubs(connection: ServerConnection): List<PlexHub> =
        hubsAt(connection, "/hubs")

    /** `GET /hubs/continueWatching`, aplanado. */
    suspend fun continueWatching(connection: ServerConnection): List<PlexItem> =
        hubsAt(connection, "/hubs/continueWatching").flatMap { it.items }

    private suspend fun hubsAt(connection: ServerConnection, path: String): List<PlexHub> {
        val container = container(connection, path, listOf("includeGuids" to "1"))
        return (container["Hub"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { obj -> PlexHub.fromJson(obj, connection.serverId) } }
            ?: emptyList()
    }

    /** `GET /library/metadata/{ratingKey}`: ficha completa de un elemento. */
    suspend fun details(connection: ServerConnection, ratingKey: String): MediaDetails {
        val container = container(connection, "/library/metadata/$ratingKey", listOf("includeGuids" to "1"))
        val metadata = (container["Metadata"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw PlexException("This item is no longer available.")
        return MediaDetails.fromJson(metadata, connection.serverId)
            ?: throw PlexException("Unexpected item format from ${connection.name}.")
    }

    /** Marca como visto / no visto (`/:/scrobble` y `/:/unscrobble`). */
    suspend fun setWatched(connection: ServerConnection, ratingKey: String, watched: Boolean) =
        withContext(Dispatchers.IO) {
            val path = if (watched) "/:/scrobble" else "/:/unscrobble"
            val url = (connection.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
                .addQueryParameter("key", ratingKey)
                .addQueryParameter("identifier", "com.plexapp.plugins.library")
                .build()
            val request = Request.Builder().url(url).headers(plex.headers(connection.token)).get().build()
            try {
                plex.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw PlexHttpException(response.code)
                }
            } catch (e: IOException) {
                throw PlexException(e.message ?: "Could not reach ${connection.name}.")
            }
        }

    /** `GET /library/sections/{id}/all` paginado, con orden y filtros. */
    suspend fun libraryItems(
        connection: ServerConnection,
        sectionId: String,
        start: Int,
        size: Int,
        sort: String? = null,
        filters: Map<String, String> = emptyMap(),
    ): LibraryPage {
        val query = mutableListOf(
            "X-Plex-Container-Start" to start.toString(),
            "X-Plex-Container-Size" to size.toString(),
        )
        if (!sort.isNullOrEmpty()) query += "sort" to sort
        filters.toSortedMap().forEach { (name, value) -> query += name to value }
        val container = container(connection, "/library/sections/$sectionId/all", query)
        val items = (container["Metadata"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { obj -> PlexItem.fromJson(obj, connection.serverId) } }
            ?: emptyList()
        return LibraryPage(items = items, totalSize = container.int("totalSize"))
    }

    /** `GET /library/sections/{id}/genre`: géneros de la biblioteca. */
    suspend fun libraryGenres(connection: ServerConnection, sectionId: String): List<GenreOption> {
        val container = container(connection, "/library/sections/$sectionId/genre")
        return (container["Directory"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.string("title") ?: return@mapNotNull null
            val key = obj.string("key") ?: return@mapNotNull null
            GenreOption(title = title, key = key)
        } ?: emptyList()
    }
}
