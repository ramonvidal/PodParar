package com.podparar.feature.player

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val PLAYER_ROUTE = "player"

fun NavGraphBuilder.playerScreen() {
    composable(PLAYER_ROUTE) { PlayerScreen() }
}
