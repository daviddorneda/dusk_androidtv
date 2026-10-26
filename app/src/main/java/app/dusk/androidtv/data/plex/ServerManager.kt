package app.dusk.androidtv.data.plex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Descubre los servidores de la cuenta y conecta todos en paralelo (equivale a
 * `PlexService+Servers.connectAllServers` + `ServerPool`). Un servidor caído nunca impide
 * que los demás conecten.
 * TODO: orden de prioridad y activar/desactivar servidores (`ServerPriorityStore`).
 */
class ServerManager(
    private val plex: PlexClient,
    private val store: SessionStore,
    private val scope: CoroutineScope,
) {
    private val _entries = MutableStateFlow<List<ServerEntry>>(emptyList())
    val entries: StateFlow<List<ServerEntry>> = _entries.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    fun connectAll(accountToken: String) {
        job?.cancel()
        val gen = ++generation
        _isDiscovering.value = true
        _error.value = null
        job = scope.launch {
            try {
                val servers = plex.discoverServers(accountToken)
                if (gen != generation) return@launch
                _entries.value = servers.map { ServerEntry(it, ServerState.Connecting) }

                val gate = Semaphore(MAX_CONCURRENT_CONNECTS)
                coroutineScope {
                    servers.map { server ->
                        async { gate.withPermit { connect(server, accountToken, gen) } }
                    }.awaitAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) _error.value = e.message ?: "Could not load your servers."
            } finally {
                if (gen == generation) _isDiscovering.value = false
            }
        }
    }

    fun clear() {
        generation++
        job?.cancel()
        _entries.value = emptyList()
        _error.value = null
        _isDiscovering.value = false
    }

    private suspend fun connect(server: PlexServer, accountToken: String, gen: Int) {
        val serverId = server.clientIdentifier
        val token = server.accessToken?.takeIf { it.isNotBlank() } ?: accountToken
        val candidates = ServerProbe.candidates(server, store.lastGoodUri(serverId))
        val resolution = ServerProbe.resolve(plex.httpClient, candidates, plex.headers(token), server.name)

        val state = when (resolution) {
            is ConnectionResolution.Connected -> {
                store.setLastGoodUri(serverId, resolution.baseUrl)
                ServerState.Connected(
                    ServerConnection(
                        serverId = serverId,
                        name = server.name,
                        owned = server.owned,
                        sourceTitle = server.sourceTitle,
                        baseUrl = resolution.baseUrl,
                        token = token,
                        connection = resolution.connection,
                    ),
                )
            }
            ConnectionResolution.Unauthorized -> ServerState.Unauthorized
            is ConnectionResolution.Failed -> ServerState.Offline(resolution.reason)
        }
        if (gen != generation) return
        _entries.update { list ->
            list.map { if (it.server.clientIdentifier == serverId) it.copy(state = state) else it }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_CONNECTS = 4
    }
}
