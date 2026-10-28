package com.podparar.feature.discover

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val DISCOVER_ROUTE = "discover"

/** The Discover tab. [onOpenPodcast] opens a subscribed show's page (`:app` does the navigation). */
fun NavGraphBuilder.discoverScreen(onOpenPodcast: (feedUrl: String) -> Unit) {
    composable(DISCOVER_ROUTE) { DiscoverScreen(onOpenPodcast = onOpenPodcast) }
}
