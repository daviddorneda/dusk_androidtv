package app.dusk.androidtv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente de la API de plex.tv. Equivale a `PlexService+Auth` / `PlexService+Networking` del original.
 * Por ahora solo cubre el inicio de sesión con PIN; el resto de endpoints se añadirá por fases.
 */
class PlexClient(
    private val clientIdentifier: String,
    private val appVersion: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Cabeceras base de Plex (mismas que `basePlexHeaders` en el original). */
    private fun headers(token: String? = null): Headers {
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

    private fun execute(request: Request): String {
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlexException("Plex returned HTTP ${response.code}.")
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
