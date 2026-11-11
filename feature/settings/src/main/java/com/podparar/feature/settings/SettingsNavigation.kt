package com.podparar.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val SETTINGS_ROUTE = "settings"

/** This phone's settings, the bottom bar's Settings tab. */
fun NavGraphBuilder.settingsScreen() {
    composable(SETTINGS_ROUTE) { SettingsScreen() }
}
