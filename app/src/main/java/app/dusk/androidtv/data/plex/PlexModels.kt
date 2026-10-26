package app.dusk.androidtv.data.plex

import kotlinx.serialization.Serializable

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

class PlexException(message: String) : Exception(message)
