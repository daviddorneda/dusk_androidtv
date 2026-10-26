package app.dusk.androidtv.data.plex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Un candidato para alcanzar un servidor, en orden de prioridad. */
data class ConnectionCandidate(val baseUrl: String, val connection: PlexConnection)

sealed interface ConnectionResolution {
    data class Connected(val baseUrl: String, val connection: PlexConnection) : ConnectionResolution
    data object Unauthorized : ConnectionResolution
    data class Failed(val reason: String) : ConnectionResolution
}

/**
 * Carrera de conexiones de un servidor (equivale a `ServerProbe` y `ServerPool.connectionCandidates`).
 * No es "el primero que llega": un candidato solo se confirma cuando ningún otro de mayor
 * prioridad puede ganar, o cuando pasa la ventana de gracia tras el primer éxito.
 */
object ServerProbe {
    private const val GRACE_MS = 1_500L

    fun candidates(server: PlexServer, lastGoodUri: String?): List<ConnectionCandidate> {
        val candidates = mutableListOf<ConnectionCandidate>()
        val seen = HashSet<String>()

        fun append(uri: String, connection: PlexConnection) {
            val normalized = uri.trimEnd('/')
            if (normalized.isEmpty() || !seen.add(normalized)) return
            candidates += ConnectionCandidate(normalized, connection)
        }

        for (connection in server.sortedConnections) {
            if (connection.isKnownUnreachableAddress) continue
            val fallback = connection.httpFallbackUri
            if (connection.local && fallback != null) append(fallback, connection)
            append(connection.uri, connection)
            if (!connection.local && fallback != null) append(fallback, connection)
        }

        if (lastGoodUri == null) return candidates
        // La última conexión buena sube al frente de su propio nivel, nunca entre niveles.
        return candidates.withIndex().sortedWith { l, r ->
            val lp = l.value.connection.sortPriority
            val rp = r.value.connection.sortPriority
            when {
                lp != rp -> lp.compareTo(rp)
                else -> {
                    val lGood = l.value.baseUrl == lastGoodUri.trimEnd('/')
                    val rGood = r.value.baseUrl == lastGoodUri.trimEnd('/')
                    if (lGood != rGood) {
                        if (lGood) -1 else 1
                    } else {
                        l.index.compareTo(r.index)
                    }
                }
            }
        }.map { it.value }
    }

    suspend fun resolve(
        http: OkHttpClient,
        candidates: List<ConnectionCandidate>,
        headers: Headers,
        serverName: String,
    ): ConnectionResolution {
        if (candidates.isEmpty()) return ConnectionResolution.Failed("Could not connect to $serverName")

        return coroutineScope {
            val events = Channel<Event>(Channel.UNLIMITED)
            val pending = candidates.indices.toMutableSet()
            var best: Int? = null
            var sawUnauthorized = false
            var lastFailure = "Could not connect to $serverName"
            var graceJob: Job? = null
            var decided: ConnectionResolution? = null

            candidates.forEachIndexed { index, candidate ->
                launch {
                    events.send(Event.Probe(index, runProbe(http, candidate, headers)))
                }
            }

            fun winner(): ConnectionResolution? = best?.let {
                ConnectionResolution.Connected(candidates[it].baseUrl, candidates[it].connection)
            }

            fun bestIsUnbeatable(): Boolean {
                val b = best ?: return false
                return pending.none { it < b }
            }

            while (decided == null && pending.isNotEmpty()) {
                when (val event = events.receive()) {
                    is Event.Probe -> {
                        pending.remove(event.index)
                        when (val result = event.result) {
                            ProbeResult.Success -> {
                                if (best == null || event.index < best!!) best = event.index
                                if (graceJob == null && !bestIsUnbeatable()) {
                                    graceJob = launch {
                                        delay(GRACE_MS)
                                        events.send(Event.GraceElapsed)
                                    }
                                }
                            }
                            is ProbeResult.Failure -> {
                                if (result.unauthorized) sawUnauthorized = true
                                lastFailure = result.reason
                            }
                        }
                        if (bestIsUnbeatable()) decided = winner()
                    }
                    Event.GraceElapsed -> winner()?.let { decided = it }
                }
            }

            coroutineContext.cancelChildren()
            events.close()

            decided
                ?: winner()
                ?: if (sawUnauthorized) ConnectionResolution.Unauthorized else ConnectionResolution.Failed(lastFailure)
        }
    }

    /** `/identity` (¿responde?) y después `/library/sections` (¿está autorizado?). */
    private suspend fun runProbe(
        http: OkHttpClient,
        candidate: ConnectionCandidate,
        headers: Headers,
    ): ProbeResult {
        val timeout = if (candidate.connection.local) 20L else 8L
        val client = http.newBuilder().callTimeout(timeout, TimeUnit.SECONDS).build()
        return try {
            val identity = Request.Builder().url(candidate.baseUrl + "/identity").headers(headers).get().build()
            client.newCall(identity).await().use { response ->
                if (response.code == 401) return ProbeResult.Failure(true, "HTTP 401")
                if (!response.isSuccessful) return ProbeResult.Failure(false, "HTTP ${response.code}")
            }
            val sections = Request.Builder().url(candidate.baseUrl + "/library/sections").headers(headers).get().build()
            client.newCall(sections).await().use { response ->
                if (response.code == 401) return ProbeResult.Failure(true, "HTTP 401")
                if (!response.isSuccessful) return ProbeResult.Failure(false, "HTTP ${response.code}")
            }
            ProbeResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ProbeResult.Failure(false, e.message ?: "Network error")
        } catch (e: IllegalArgumentException) {
            ProbeResult.Failure(false, "Invalid address")
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    private sealed interface ProbeResult {
        data object Success : ProbeResult
        data class Failure(val unauthorized: Boolean, val reason: String) : ProbeResult
    }

    private sealed interface Event {
        data class Probe(val index: Int, val result: ProbeResult) : Event
        data object GraceElapsed : Event
    }
}
