package app.dusk.androidtv.data.plex

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Respuesta de `POST https://plex.tv/api/v2/pins` y `GET /api/v2/pins/{id}`. */
@Serializable
data class PlexPin(
    val id: Int,
    val code: String,
    val clientIdentifier: String,
    val expiresAt: String,
    /** Se rellena cuando el usuario aprueba el PIN en plex.tv/link. */
    val authToken: String? = null,
)

open class PlexException(message: String) : Exception(message)

class PlexHttpException(val code: Int) : PlexException("Plex returned HTTP $code.")

/** Cuenta autenticada (`GET https://plex.tv/api/v2/user`). */
data class PlexUser(
    val id: Int,
    val uuid: String?,
    val username: String?,
    val title: String?,
    val friendlyName: String?,
    val thumb: String?,
) {
    companion object {
        fun fromJson(json: JsonObject): PlexUser? {
            val id = json.int("id") ?: return null
            return PlexUser(
                id = id,
                uuid = json.string("uuid"),
                username = json.string("username"),
                title = json.string("title"),
                friendlyName = json.string("friendlyName"),
                thumb = json.string("thumb"),
            )
        }
    }
}

/**
 * Miembro de Plex Home. Sin token a propósito: el token que devuelve el cambio de usuario
 * lo gestiona solo `HomeSessionManager`.
 */
@Serializable
data class PlexHomeUser(
    val id: Int,
    val uuid: String? = null,
    val username: String? = null,
    val title: String? = null,
    val friendlyName: String? = null,
    val thumb: String? = null,
    val isProtected: Boolean = false,
    val isRestricted: Boolean = false,
    val isAdmin: Boolean = false,
    val isGuest: Boolean = false,
) {
    val displayName: String
        get() = friendlyName ?: title ?: username ?: "Plex User"

    /** Identidad estable y no secreta del perfil. */
    val stableProfileId: String
        get() = uuid ?: "plex-home-$id"

    companion object {
        fun fromJson(json: JsonObject): PlexHomeUser? {
            val id = json.int("id") ?: return null
            return PlexHomeUser(
                id = id,
                uuid = json.string("uuid"),
                username = json.string("username"),
                title = json.string("title"),
                friendlyName = json.string("friendlyName"),
                thumb = json.string("thumb"),
                isProtected = json.bool("protected") ?: false,
                isRestricted = json.bool("restricted") ?: false,
                isAdmin = json.bool("admin") ?: false,
                isGuest = json.bool("guest") ?: false,
            )
        }

        /** Acepta un array directo o un envoltorio (`users`, `Users`, `MediaContainer`...). */
        fun listFromJson(root: kotlinx.serialization.json.JsonElement): List<PlexHomeUser> {
            val array: JsonArray = when (root) {
                is JsonArray -> root
                is JsonObject -> findUserArray(root)
                    ?: throw PlexException("Expected a Plex Home users array or envelope.")
                else -> throw PlexException("Invalid Plex Home users response.")
            }
            return array.map { element ->
                (element as? JsonObject)?.let(::fromJson)
                    ?: throw PlexException("Plex Home returned users in an unsupported format.")
            }
        }

        private fun findUserArray(obj: JsonObject): JsonArray? {
            for (key in listOf("users", "Users", "User", "Metadata")) {
                when (val value = obj[key]) {
                    is JsonArray -> return value
                    is JsonObject -> findUserArray(value)?.let { return it }
                    else -> Unit
                }
            }
            for (key in listOf("MediaContainer", "mediaContainer")) {
                (obj[key] as? JsonObject)?.let { container -> findUserArray(container)?.let { return it } }
            }
            return null
        }
    }
}

/** Un punto de acceso a un servidor (local, remoto o relay). */
data class PlexConnection(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
) {
    val sortPriority: Int
        get() = when {
            isOnLocalNetwork -> 0
            !relay -> 1
            else -> 2
        }

    /** Llega al servidor por la red local: plex.tv lo marca `local` o la dirección es privada. */
    val isOnLocalNetwork: Boolean
        get() {
            if (relay) return false
            if (local) return true
            val hosts = listOfNotNull(address, hostOf(uri))
            return hosts.any { isPrivateAddress(plexDirectIpAddress(it) ?: it) }
        }

    /** 172.17–31.x es donde Docker crea sus redes puente: se prueba el último dentro de las locales. */
    val isLikelyContainerBridgeAddress: Boolean
        get() {
            val octets = address.split(".")
            if (octets.size != 4 || octets[0] != "172") return false
            val second = octets[1].toIntOrNull() ?: return false
            return second in 17..31
        }

    val isHttps: Boolean
        get() = protocol.equals("https", ignoreCase = true)

    val isKnownUnreachableAddress: Boolean
        get() {
            val normalized = address.trim('[', ']').replace('-', ':').lowercase()
            if (normalized == "::" || normalized == "0000:0000:0000:0000:0000:0000:0000:0000") return true
            return normalized.startsWith("fe80:")
        }

    val httpFallbackUri: String?
        get() {
            if (!isHttps) return null
            val host = if (address.contains(":") && !address.startsWith("[") && !address.endsWith("]")) {
                "[$address]"
            } else {
                address
            }
            return "http://$host:$port"
        }

    companion object {
        private fun hostOf(uri: String): String? = runCatching { java.net.URI(uri).host }.getOrNull()

        /** IP que codifica un host plex.direct ("192-168-1-10.<hash>.plex.direct"), o null. */
        fun plexDirectIpAddress(host: String): String? {
            val lowered = host.lowercase()
            if (!lowered.endsWith(".plex.direct")) return null
            val label = lowered.split(".").firstOrNull() ?: return null
            val dotted = label.replace('-', '.')
            if (dotted.split(".").size == 4) return dotted
            return label.replace('-', ':')
        }

        /** RFC 1918, loopback, link-local y direcciones locales únicas IPv6. */
        fun isPrivateAddress(address: String): Boolean {
            val normalized = address.trim('[', ']').lowercase()
            val octets = normalized.split(".").mapNotNull { it.toIntOrNull() }
            if (octets.size == 4 && normalized.split(".").size == 4) {
                val a = octets[0]
                val b = octets[1]
                return when {
                    a == 10 || a == 127 -> true
                    a == 192 && b == 168 -> true
                    a == 169 && b == 254 -> true
                    a == 172 && b in 16..31 -> true
                    else -> false
                }
            }
            if (!normalized.contains(":")) return false
            return normalized == "::1" || normalized.startsWith("fc") || normalized.startsWith("fd")
        }

        fun fromJson(json: JsonObject): PlexConnection? {
            val uri = json.string("uri") ?: return null
            return PlexConnection(
                protocol = json.string("protocol") ?: "https",
                address = json.string("address") ?: return null,
                port = json.int("port") ?: 32400,
                uri = uri,
                local = json.bool("local") ?: false,
                relay = json.bool("relay") ?: false,
            )
        }
    }
}

/** Servidor devuelto por `GET https://plex.tv/api/v2/resources`. */
data class PlexServer(
    val name: String,
    val clientIdentifier: String,
    val provides: String?,
    val owned: Boolean,
    val presence: Boolean,
    val accessToken: String?,
    val sourceTitle: String?,
    val connections: List<PlexConnection>,
) {
    /** Locales primero, luego remotas, luego relay; las redes puente de Docker al final de las locales. */
    val sortedConnections: List<PlexConnection>
        get() = connections.sortedWith { lhs, rhs ->
            when {
                lhs.sortPriority != rhs.sortPriority -> lhs.sortPriority.compareTo(rhs.sortPriority)
                lhs.isLikelyContainerBridgeAddress != rhs.isLikelyContainerBridgeAddress ->
                    if (lhs.isLikelyContainerBridgeAddress) 1 else -1
                lhs.isHttps != rhs.isHttps -> if (lhs.isHttps) -1 else 1
                else -> 0
            }
        }

    companion object {
        fun fromJson(json: JsonObject): PlexServer? {
            val name = json.string("name") ?: return null
            val id = json.string("clientIdentifier") ?: return null
            val connections = (json["connections"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.let(PlexConnection::fromJson) }
                ?: emptyList()
            return PlexServer(
                name = name,
                clientIdentifier = id,
                provides = json.string("provides"),
                owned = json.bool("owned") ?: false,
                presence = json.bool("presence") ?: false,
                accessToken = json.string("accessToken"),
                sourceTitle = json.string("sourceTitle"),
                connections = connections,
            )
        }
    }
}

/** Sesión viva con un servidor: dónde alcanzarlo y con qué token (siempre el del servidor). */
data class ServerConnection(
    val serverId: String,
    val name: String,
    val owned: Boolean,
    val sourceTitle: String?,
    val baseUrl: String,
    val token: String,
    val connection: PlexConnection?,
) {
    val isLocal: Boolean get() = connection?.isOnLocalNetwork == true
    val isRelay: Boolean get() = connection?.relay == true
}

sealed interface ServerState {
    data object Connecting : ServerState
    data class Connected(val connection: ServerConnection) : ServerState
    data object Unauthorized : ServerState
    data class Offline(val reason: String) : ServerState
}

data class ServerEntry(val server: PlexServer, val state: ServerState)
