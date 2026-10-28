package com.podparar.feature.library

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** This feature's only public entry point; everything else stays internal to the module. */
const val LIBRARY_ROUTE = "library"

/** A show's page. The feed URL is [Uri.encode]d into the path; use [podcastRoute] to build it. */
const val PODCAST_ROUTE = "podcast/{$FEED_URL_ARG}"

internal const val FEED_URL_ARG = "feedUrl"

/** The route that opens the show page for [feedUrl]. */
fun podcastRoute(feedUrl: String): String = "podcast/" + Uri.encode(feedUrl)

/**
 * The Library grid and each show's page. [onOpenPodcast] is called with a feed URL when a show
 * is tapped, [onFindShows] when the empty Library's button is tapped, and [onBack] to leave a
 * show's page (the back arrow, or after unsubscribing).
 */
fun NavGraphBuilder.libraryScreen(
    onOpenPodcast: (feedUrl: String) -> Unit,
    onFindShows: () -> Unit,
    onBack: () -> Unit,
) {
    composable(LIBRARY_ROUTE) {
        LibraryScreen(onOpenPodcast = onOpenPodcast, onFindShows = onFindShows)
    }
    composable(
        route = PODCAST_ROUTE,
        arguments = listOf(navArgument(FEED_URL_ARG) { type = NavType.StringType }),
    ) {
        PodcastScreen(onBack = onBack)
    }
}
