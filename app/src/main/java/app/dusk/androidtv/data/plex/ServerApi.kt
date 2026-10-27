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
}
