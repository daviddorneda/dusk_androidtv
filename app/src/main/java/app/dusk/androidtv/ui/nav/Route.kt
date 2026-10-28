package app.dusk.androidtv.ui.nav

/**
 * Destinos apilados sobre la pestaña actual (equivale a `AppNavigationRoute`). Un destino apilado
 * cubre la barra de pestañas y se cierra con Atrás.
 * TODO: serie, temporada, episodio, biblioteca, persona, colección...
 */
sealed interface Route {
    data class MovieDetail(val serverId: String, val ratingKey: String) : Route
}
