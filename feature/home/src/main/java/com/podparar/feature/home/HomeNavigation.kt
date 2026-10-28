package com.podparar.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val HOME_ROUTE = "home"

/**
 * The Home tab: Continue listening, Up next and New episodes. [onOpenPodcast] is called with a
 * feed URL to open that show's page, [onFindShows] when the empty Home's button is tapped.
 */
fun NavGraphBuilder.homeScreen(
    onOpenPodcast: (feedUrl: String) -> Unit,
    onFindShows: () -> Unit,
) {
    composable(HOME_ROUTE) {
        HomeScreen(onOpenPodcast = onOpenPodcast, onFindShows = onFindShows)
    }
}
