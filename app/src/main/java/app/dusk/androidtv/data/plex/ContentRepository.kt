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

    private val _reports = MutableStateFlow<List<ServerLoadReport>>(emptyList())

    /** Qué devolvió cada servidor para Home (filas, "continuar viendo", bibliotecas) o por qué falló. */
    val reports: StateFlow<List<ServerLoadReport>> = _reports.asStateFlow()

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
            _reports.value = emptyList()
            _libraries.value = emptyList()
            _home.value = HomeContent(isLoading = snapshot.discovering)
            return
        }

        _home.value = _home.value.copy(isLoading = true)
        val payloads = coroutineScope {
            connections.map { connection -> async { load(connection) } }.awaitAll()
        }
        _libraries.value = payloads.flatMap { it.libraries.orEmpty() }
        _reports.value = payloads.map { payload ->
            ServerLoadReport(
                serverName = payload.connection.name,
                hubRows = payload.hubs?.size,
                continueWatching = payload.continueWatching?.size,
                libraries = payload.libraries?.size,
                error = payload.errors.joinToString("; ").takeIf { it.isNotEmpty() },
            )
        }
        _home.value = HomeMerge.build(payloads)
    }

    /** Un servidor que falla nunca impide que los demás pinten Home; el error se anota en el informe. */
    private suspend fun load(connection: ServerConnection): ServerHomePayload = coroutineScope {
        val hubs = async { attempt { api.hubs(connection) } }
        val continueWatching = async { attempt { api.continueWatching(connection) } }
        val libraries = async { attempt { api.libraries(connection) } }
        val hubsResult = hubs.await()
        val continueResult = continueWatching.await()
        val librariesResult = libraries.await()
        ServerHomePayload(
            connection = connection,
            hubs = hubsResult.getOrNull(),
            continueWatching = continueResult.getOrNull(),
            libraries = librariesResult.getOrNull(),
            errors = listOfNotNull(
                hubsResult.exceptionOrNull()?.let { "Rows: ${it.message}" },
                continueResult.exceptionOrNull()?.let { "Continue Watching: ${it.message}" },
                librariesResult.exceptionOrNull()?.let { "Libraries: ${it.message}" },
            ),
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

data class ServerLoadReport(
    val serverName: String,
    val hubRows: Int?,
    val continueWatching: Int?,
    val libraries: Int?,
    val error: String?,
)
