package com.podparar.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** This feature's only public entry point; everything else stays internal to the module. */
const val LIBRARY_ROUTE = "library"

fun NavGraphBuilder.libraryScreen() {
    composable(LIBRARY_ROUTE) { LibraryScreen() }
}
