package app.dusk.androidtv.data.plex

import android.net.Uri

/** URLs de imagen de un servidor (equivale a `PlexService+Images.imageURL`). */
object PlexImages {
    /**
     * Pide al servidor una imagen redimensionada (`/photo/:/transcode`). La ruta de biblioteca
     * va relativa, como hace Plex Web: enviar al servidor su propia URL completa rompe los
     * pósters en LAN. El token va en la URL porque el cargador de imágenes no añade cabeceras.
     */
    fun url(connection: ServerConnection?, path: String?, width: Int, height: Int): String? {
        if (connection == null || path.isNullOrEmpty()) return null
        val base = connection.baseUrl.trimEnd('/')
        val source = if (Uri.parse(path).scheme != null) path else if (path.startsWith("/")) path else "/$path"
        return Uri.parse("$base/photo/:/transcode").buildUpon()
            .appendQueryParameter("width", maxOf(width, 1).toString())
            .appendQueryParameter("height", maxOf(height, 1).toString())
            .appendQueryParameter("minSize", "1")
            .appendQueryParameter("upscale", "0")
            .appendQueryParameter("url", source)
            .appendQueryParameter("X-Plex-Token", connection.token)
            .build()
            .toString()
    }
}
