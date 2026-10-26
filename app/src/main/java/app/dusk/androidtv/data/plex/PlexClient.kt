package app.dusk.androidtv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente de la API de plex.tv. Equivale a `PlexService+Auth`, `+Home`, `+Servers` y
 * `+Networking` del original.
 */
class PlexClient(
    private val clientIdentifier: String,
    private val appVersion: String,
) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Cabeceras base de Plex (mismas que `basePlexHeaders` en el original). */
    fun headers(token: String? = null): Headers {
        val builder = Headers.Builder()
            .add("Accept", "application/json")
            .add("X-Plex-Client-Identifier", clientIdentifier)
            .add("X-Plex-Product", "Dusk")
            .add("X-Plex-Version", appVersion)
            .add("X-Plex-Platform", "Android")
            .add("X-Plex-Device-Name", "Android TV")
        if (token != null) builder.add("X-Plex-Token", token)
        return builder.build()
    }

    // ---------------- PIN (login) ----------------

    suspend fun generatePin(strong: Boolean = false): PlexPin = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
        if (strong) formBuilder.add("strong", "true")
        val request = Request.Builder()
            .url("$PLEX_TV/api/v2/pins")
            .headers(headers())
            .post(formBuilder.build())
            .build()
        json.decodeFromString(PlexPin.serializer(), execute(request))
    }

    /** Devuelve el token cuando el PIN ya fue aprobado; `null` mientras siga pendiente. */
    suspend fun checkPin(pinId: Int): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$PLEX_TV/api/v2/pins/$pinId")
            .headers(headers())
            .get()
            .build()
        json.decodeFromString(PlexPin.serializer(), execute(request)).authToken
    }

    // ---------------- Cuenta y Plex Home ----------------

    suspend fun fetchAccount(token: String): PlexUser = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$PLEX_TV/api/v2/user")
            .headers(headers(token))
            .get()
            .build()
        val root = json.parseToJsonElement(execute(request)) as? JsonObject
            ?: throw PlexException("Unexpected Plex account response.")
        PlexUser.fromJson(root) ?: throw PlexException("Plex account response had no id.")
    }

    suspend fun fetchHomeUsers(primaryToken: String): List<PlexHomeUser> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$PLEX_TV/api/v2/home/users")
            .headers(headers(primaryToken))
            .get()
            .build()
        PlexHomeUser.listFromJson(json.parseToJsonElement(execute(request)))
    }

    /**
     * Cambia de miembro de Plex Home. El PIN solo viaja en esta petición y no se guarda.
     * Devuelve el token de cuenta del miembro.
     */
    suspend fun switchHomeUser(user: PlexHomeUser, pin: String?, primaryToken: String): String =
        withContext(Dispatchers.IO) {
            val urlBuilder = "$PLEX_TV/api/home/users/${user.id}/switch".toHttpUrl().newBuilder()
            if (pin != null) urlBuilder.addQueryParameter("pin", pin)
            val request = Request.Builder()
                .url(urlBuilder.build())
                .headers(headers(primaryToken))
                .post(FormBody.Builder().build())
                .build()
            val body = try {
                execute(request)
            } catch (e: PlexHttpException) {
                if (user.isProtected && (e.code == 401 || e.code == 403)) {
                    throw PlexException("That Plex Home PIN is incorrect.")
                }
                throw e
            }
            switchToken(body)
                ?: throw PlexException("Plex did not return an account token when switching Home users.")
        }

    private fun switchToken(body: String): String? {
        runCatching { json.parseToJsonElement(body) }.getOrNull()?.let { element ->
            findSwitchToken(element)?.let { return it }
        }
        // Algunas respuestas llegan como XML con el token en un atributo.
        return Regex("""(?:authenticationToken|authToken)="([^"]+)"""").find(body)?.groupValues?.get(1)
    }

    private fun findSwitchToken(element: JsonElement): String? {
        val obj = element as? JsonObject ?: return null
        for (key in listOf("authToken", "authenticationToken")) {
            obj.string(key)?.let { return it }
        }
        for (key in listOf("user", "User", "account")) {
            obj[key]?.let { nested -> findSwitchToken(nested)?.let { return it } }
        }
        return null
    }

    // ---------------- Servidores ----------------

    suspend fun discoverServers(accountToken: String): List<PlexServer> = withContext(Dispatchers.IO) {
        val url = "$PLEX_TV/api/v2/resources".toHttpUrl().newBuilder()
            .addQueryParameter("includeHttps", "1")
            .addQueryParameter("includeRelay", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .headers(headers(accountToken))
            .get()
            .build()
        val array = json.parseToJsonElement(execute(request)) as? JsonArray
            ?: throw PlexException("Expected JSON array from resources endpoint.")
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val provides = obj.string("provides") ?: return@mapNotNull null
            if (!provides.contains("server")) return@mapNotNull null
            PlexServer.fromJson(obj)
        }
    }

    private fun execute(request: Request): String {
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlexHttpException(response.code)
                return response.body?.string() ?: throw PlexException("Plex returned an empty response.")
            }
        } catch (e: IOException) {
            throw PlexException(e.message ?: "Could not reach plex.tv.")
        }
    }

    private companion object {
        const val PLEX_TV = "https://plex.tv"
    }
}
