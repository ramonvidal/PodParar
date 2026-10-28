package com.podparar.feature.discover

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.LoadError
import com.podparar.core.model.SearchResult
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Discover tab in each of its states; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class DiscoverScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: DiscoverUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        DiscoverContent(
                            state = state,
                            onQueryChange = {},
                            onSearch = {},
                            onSubscribe = {},
                            onOpenPodcast = {},
                            onAddByLink = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val resultsState = DiscoverUiState(
        query = "history",
        hasSearched = true,
        results = listOf(
            SearchResultItem(
                result = SearchResult(
                    title = "The Rest Is History",
                    author = "Goalhanger",
                    feedUrl = "https://feeds.example.com/the-rest-is-history",
                    artworkUrl = null,
                ),
                isSubscribed = true,
            ),
            SearchResultItem(
                result = SearchResult(
                    title = "Hardcore History",
                    author = "Dan Carlin",
                    feedUrl = "https://feeds.example.com/hardcore-history",
                    artworkUrl = null,
                ),
                isSubscribed = false,
                isSubscribing = true,
            ),
            SearchResultItem(
                result = SearchResult(
                    title = "You're Dead to Me: Comedy Meets History, Told by Historians and Comedians",
                    author = "BBC Radio 4",
                    feedUrl = "https://feeds.example.com/youre-dead-to-me",
                    artworkUrl = null,
                ),
                isSubscribed = false,
            ),
            SearchResultItem(
                result = SearchResult(
                    title = "Història de Catalunya",
                    author = "Catalunya Ràdio",
                    feedUrl = "https://feeds.example.com/historia-de-catalunya",
                    artworkUrl = null,
                ),
                isSubscribed = false,
            ),
        ),
    )

    @Test
    fun start() = shoot("discover-start", DiscoverUiState())

    @Test
    fun startLargeText() = shoot("discover-start-large-text", DiscoverUiState(), fontScale = 2f)

    @Test
    fun results() = shoot("discover-results", resultsState)

    @Test
    fun resultsDark() = shoot("discover-results-dark", resultsState, dark = true)

    @Test
    fun noResults() = shoot(
        "discover-no-results",
        DiscoverUiState(query = "zzqxv podcast", hasSearched = true),
    )

    @Test
    fun error() = shoot(
        "discover-error",
        DiscoverUiState(query = "history", hasSearched = true, error = LoadError.OFFLINE),
    )
}
