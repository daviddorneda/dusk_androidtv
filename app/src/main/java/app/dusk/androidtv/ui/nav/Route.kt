package app.dusk.androidtv.ui.nav

/**
 * Destinos apilados sobre la pestaña actual (equivale a `AppNavigationRoute`). Un destino apilado
 * cubre la barra de pestañas y se cierra con Atrás.
 * TODO: serie, temporada, episodio, biblioteca, persona, colección...
 */
sealed interface Route {
    data class MovieDetail(val serverId: String, val ratingKey: String) : Route

    /**
     * En tvOS no hay pantallas separadas de serie, temporada y episodio: las tres rutas abren la misma
     * página, que se abre en la temporada (y el episodio) indicados.
     */
    data class SeriesDetail(
        val serverId: String,
        val showRatingKey: String,
        val seasonRatingKey: String? = null,
        val episodeRatingKey: String? = null,
    ) : Route
}
