package com.podparar.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val SETTINGS_ROUTE = "settings"

/** This phone's settings, opened from Library's top bar. [onBack] leaves it (the back arrow). */
fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable(SETTINGS_ROUTE) { SettingsScreen(onBack = onBack) }
}
