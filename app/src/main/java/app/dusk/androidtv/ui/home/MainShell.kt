package app.dusk.androidtv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dusk.androidtv.DuskGraph
import app.dusk.androidtv.data.plex.LibraryKind
import app.dusk.androidtv.data.plex.PlexHomeUser
import app.dusk.androidtv.data.plex.PlexLibrary
import app.dusk.androidtv.data.plex.PlexMediaType
import app.dusk.androidtv.ui.components.DuskTabBar
import app.dusk.androidtv.ui.components.MainTab
import app.dusk.androidtv.ui.detail.MovieDetailScreen
import app.dusk.androidtv.ui.nav.Route
import app.dusk.androidtv.ui.theme.DuskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pestañas disponibles según las bibliotecas de los servidores conectados (`MainTabView.availableTabs`). */
private fun availableTabs(libraries: List<PlexLibrary>): List<MainTab> {
    val kinds = libraries.map { it.kind }.toSet()
    return buildList {
        add(MainTab.Home)
        if (LibraryKind.Movie in kinds) add(MainTab.Movies)
        if (LibraryKind.Show in kinds) add(MainTab.TvShows)
        if (LibraryKind.Video in kinds) add(MainTab.Videos)
        add(MainTab.Search)
        add(MainTab.Settings)
    }
}

/**
 * Shell de pestañas tras iniciar sesión y conectar (`MainTabTVShell`). Atrás en una pestaña que
 * no es Home vuelve a Home; en Home con scroll vuelve al héroe; solo en lo alto de Home sale.
 */
@Composable
fun MainShell(
    activeUser: PlexHomeUser?,
    hasPlexHome: Boolean,
    accountToken: String,
    onSwitchUser: () -> Unit,
    onSignOut: () -> Unit,
) {
    val tv = DuskTheme.tv
    val libraries by DuskGraph.content.libraries.collectAsStateWithLifecycle()
    val home by DuskGraph.content.home.collectAsStateWithLifecycle()
    val tabs = remember(libraries) { availableTabs(libraries) }

    var selectedName by rememberSaveable { mutableStateOf(MainTab.Home.name) }
    val selected = runCatching { MainTab.valueOf(selectedName) }.getOrNull()?.takeIf { it in tabs } ?: MainTab.Home

    // Destinos apilados sobre la pestaña (detalle...). Cubren la barra y se cierran con Atrás.
    val backStack = remember { mutableStateListOf<Route>() }
    val route = backStack.lastOrNull()

    val homeListState = rememberLazyListState()
    val heroFocus = remember { FocusRequester() }
    val tabBarFocus = remember { FocusRequester() }
    val firstShelfFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val scrolledOffTop by remember {
        derivedStateOf {
            homeListState.firstVisibleItemIndex > 0 ||
                homeListState.firstVisibleItemScrollOffset > homeListState.layoutInfo.viewportSize.height / 2
        }
    }
    val barVisible = selected != MainTab.Home || !scrolledOffTop

    // El foco inicial va al botón de reproducir del héroe, solo la primera vez (no al cambiar de pestaña).
    var initialFocusDone by remember { mutableStateOf(false) }
    val hasHero = home.heroItems.isNotEmpty()
    LaunchedEffect(hasHero, selected) {
        if (!initialFocusDone && selected == MainTab.Home && hasHero) {
            delay(150)
            runCatching { heroFocus.requestFocus() }
            initialFocusDone = true
        }
    }

    var wasInDetail by remember { mutableStateOf(false) }
    LaunchedEffect(route) {
        if (route != null) {
            wasInDetail = true
        } else if (wasInDetail) {
            wasInDetail = false
            homeListState.scrollToItem(0)
            delay(250)
            runCatching { (if (hasHero) heroFocus else firstShelfFocus).requestFocus() }
        }
    }

    BackHandler(enabled = selected != MainTab.Home || scrolledOffTop) {
        if (selected != MainTab.Home) {
            selectedName = MainTab.Home.name
        } else {
            scope.launch {
                homeListState.animateScrollToItem(0)
                delay(100)
                runCatching { heroFocus.requestFocus() }
            }
        }
    }

    BackHandler(enabled = backStack.isNotEmpty()) {
        backStack.removeAt(backStack.lastIndex)
    }

    Box(Modifier.fillMaxSize().background(DuskTheme.colors.background)) {
        if (route != null) {
            when (route) {
                is Route.MovieDetail -> MovieDetailScreen(route)
            }
        } else Crossfade(targetState = selected, animationSpec = tween(260), label = "tabContent") { tab ->
            when (tab) {
                MainTab.Home -> HomeScreen(
                    listState = homeListState,
                    heroFocus = heroFocus,
                    tabBarFocus = tabBarFocus,
                    firstShelfFocus = firstShelfFocus,
                    onPlay = { /* Reproductor: fase 5 */ },
                    onOpen = { item ->
                        // Por ahora solo las películas tienen detalle; series y episodios llegan después.
                        val serverId = item.serverId
                        if (item.type == PlexMediaType.Movie && serverId != null) {
                            backStack.add(Route.MovieDetail(serverId, item.ratingKey))
                        }
                    },
                )
                MainTab.Movies, MainTab.TvShows, MainTab.Videos -> {
                    val kind = when (tab) {
                        MainTab.Movies -> LibraryKind.Movie
                        MainTab.TvShows -> LibraryKind.Show
                        else -> LibraryKind.Video
                    }
                    val count = libraries.count { it.kind == kind }
                    ComingSoonScreen(
                        title = tab.title,
                        note = "$count ${if (count == 1) "library" else "libraries"} found. Browsing arrives in the next update.",
                    )
                }
                MainTab.Search -> ComingSoonScreen(title = "Search", note = "Search arrives in a later update.")
                MainTab.Settings -> SettingsScreen(
                    activeUser = activeUser,
                    hasPlexHome = hasPlexHome,
                    servers = DuskGraph.servers,
                    accountToken = accountToken,
                    onSwitchUser = onSwitchUser,
                    onSignOut = onSignOut,
                )
            }
        }

        if (barVisible && route == null) {
            DuskTabBar(
                tabs = tabs,
                selected = selected,
                onSelect = { selectedName = it.name },
                selectedFocus = tabBarFocus,
                onMoveDown = if (selected == MainTab.Home) {
                    {
                        runCatching { (if (hasHero) heroFocus else firstShelfFocus).requestFocus() }.isSuccess
                    }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = tv.dp(56)),
            )
        }
    }
}
