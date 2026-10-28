package com.podparar.feature.player

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's entry point; apart from [MiniPlayer], everything else stays internal to the module. */
const val PLAYER_ROUTE = "player"

/** The full player, sliding up over the tabs. [onClose] goes back to where the listener came from. */
fun NavGraphBuilder.playerScreen(onClose: () -> Unit) {
    composable(
        route = PLAYER_ROUTE,
        enterTransition = { slideInVertically(initialOffsetY = { it }) },
        popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
    ) {
        PlayerScreen(onClose = onClose)
    }
}
