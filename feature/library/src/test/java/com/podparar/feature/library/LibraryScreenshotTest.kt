package com.podparar.feature.library

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
import com.podparar.core.model.Podcast
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Library tab, empty and with a grid of shows; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class LibraryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: LibraryUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        LibraryContent(
                            state = state,
                            onOpenPodcast = {},
                            onRefresh = {},
                            onFindShows = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun show(title: String, author: String, slug: String) = Podcast(
        feedUrl = "https://feeds.example.com/$slug",
        title = title,
        author = author,
        description = "",
        artworkUrl = null,
    )

    // Sorted by title, case-insensitive, as the store returns them.
    private val gridState = LibraryUiState(
        isLoading = false,
        podcasts = listOf(
            show("99% Invisible", "Roman Mars", "99pi"),
            show("Hardcore History", "Dan Carlin", "hardcore-history"),
            show(
                "In Our Time: Philosophy, Science, History and Culture with Melvyn Bragg",
                "BBC Radio 4",
                "in-our-time",
            ),
            show("Radiolab", "WNYC Studios", "radiolab"),
            show("The Rest Is History", "Goalhanger", "the-rest-is-history"),
        ),
    )

    @Test
    fun empty() = shoot("library-empty", LibraryUiState(isLoading = false))

    @Test
    fun grid() = shoot("library-grid", gridState)

    @Test
    fun gridDark() = shoot("library-grid-dark", gridState, dark = true)

    @Test
    fun gridLargeText() = shoot("library-grid-large-text", gridState, fontScale = 2f)
}
