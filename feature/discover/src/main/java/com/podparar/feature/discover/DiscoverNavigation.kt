package com.podparar.feature.discover

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val DISCOVER_ROUTE = "discover"

fun NavGraphBuilder.discoverScreen() {
    composable(DISCOVER_ROUTE) { DiscoverScreen() }
}
