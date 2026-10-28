package com.podparar.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val HOME_ROUTE = "home"

fun NavGraphBuilder.homeScreen() {
    composable(HOME_ROUTE) { HomeScreen() }
}
