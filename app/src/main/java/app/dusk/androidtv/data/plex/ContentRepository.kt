package app.dusk.androidtv.data.plex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Contenido de todos los servidores conectados: bibliotecas (pestañas) y Home. Se recarga solo
 * cuando cambia el conjunto de servidores conectados (equivale a `serverContentRevision`).
 */
@OptIn(FlowPreview::class)
class ContentRepository(
    private val api: ServerApi,
    servers: ServerManager,
    scope: CoroutineScope,
) {
    private val _libraries = MutableStateFlow<List<PlexLibrary>>(emptyList())
    val libraries: StateFlow<List<PlexLibrary>> = _libraries.asStateFlow()

    private val _home = MutableStateFlow(HomeContent())
    val home: StateFlow<HomeContent> = _home.asStateFlow()

    private val _connections = MutableStateFlow<Map<String, ServerConnection>>(emptyMap())
    val connections: StateFlow<Map<String, ServerConnection>> = _connections.asStateFlow()

    private data class Snapshot(val connections: List<ServerConnection>, val discovering: Boolean)

    private val job = scope.launch {
        combine(servers.entries, servers.isDiscovering) { entries, discovering ->
            Snapshot(
                connections = entries.mapNotNull { (it.state as? ServerState.Connected)?.connection },
                discovering = discovering,
            )
        }
            .distinctUntilChanged()
            .debounce(250)
            .collectLatest { reload(it) }
    }

    fun connectionFor(serverId: String?): ServerConnection? = serverId?.let { _connections.value[it] }

    private suspend fun reload(snapshot: Snapshot) {
        val connections = snapshot.connections
        _connections.value = connections.associateBy { it.serverId }

        if (connections.isEmpty()) {
            _libraries.value = emptyList()
            _home.value = HomeContent(isLoading = snapshot.discovering)
            return
        }

        _home.value = _home.value.copy(isLoading = true)
        val payloads = coroutineScope {
            connections.map { connection -> async { load(connection) } }.awaitAll()
        }
        _libraries.value = payloads.flatMap { it.libraries.orEmpty() }
        _home.value = HomeMerge.build(payloads)
    }

    /** Un servidor que falla nunca impide que los demás pinten Home. */
    private suspend fun load(connection: ServerConnection): ServerHomePayload = coroutineScope {
        val hubs = async { attempt { api.hubs(connection) } }
        val continueWatching = async { attempt { api.continueWatching(connection) } }
        val libraries = async { attempt { api.libraries(connection) } }
        ServerHomePayload(connection, hubs.await(), continueWatching.await(), libraries.await())
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
