package com.podparar.feature.home

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
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Home tab: full (Continue listening, Up next, New episodes), only new episodes, all caught up and
 * no subscriptions, light and dark, plus the full one at a large font scale; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: HomeUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        HomeContent(
                            state = state,
                            onPlay = {},
                            onOpenPodcast = {},
                            onPlayNext = {},
                            onAddToQueue = {},
                            onRemoveFromQueue = {},
                            onMoveInQueue = { _, _ -> },
                            onTogglePlayed = {},
                            onFindShows = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // Midday UTC, so "Today" / "Yesterday" hold in any nearby zone the tests run in.
    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private val minute = 60 * 1000L

    private val history = "https://feeds.example.com/the-rest-is-history"
    private val science = "https://feeds.example.com/science-weekly"
    private val football = "https://feeds.example.com/football-daily"

    private val showTitles = mapOf(
        history to "The Rest Is History",
        science to "Science Weekly",
        football to "Football Daily",
    )

    private fun item(
        feedUrl: String,
        guid: String,
        title: String,
        pubDate: Long,
        durationMs: Long,
        positionMs: Long = 0L,
        lastPlayedAt: Long = 0L,
    ) = EpisodeWithPodcast(
        episode = Episode(
            id = "$feedUrl#$guid",
            podcastUrl = feedUrl,
            title = title,
            description = "",
            audioUrl = "https://audio.example.com/$guid.mp3",
            imageUrl = null,
            pubDate = pubDate,
            durationMs = durationMs,
            positionMs = positionMs,
            played = false,
            lastPlayedAt = lastPlayedAt,
        ),
        podcastTitle = showTitles.getValue(feedUrl),
        podcastArtworkUrl = null,
    )

    // In progress, most recently played first.
    private val continueListening = listOf(
        item(
            history,
            "ep-500",
            "500. Napoleon in Russia: The Retreat From Moscow and the Destruction of the Grande Armée",
            pubDate = now - 3 * day,
            durationMs = 52 * minute,
            positionMs = 20 * minute,
            lastPlayedAt = now - 60 * minute,
        ),
        item(
            science,
            "sw-210",
            "Why do we dream?",
            pubDate = now - 6 * day,
            durationMs = 31 * minute,
            positionMs = 25 * minute,
            lastPlayedAt = now - day,
        ),
    )

    // In queue order.
    private val upNext = listOf(
        item(football, "fd-880", "Title race: can anyone catch the leaders?", pubDate = now - day, durationMs = 28 * minute),
        item(history, "ep-498", "498. The Wars of the Roses", pubDate = now - 14 * day, durationMs = 57 * minute),
        item(science, "sw-209", "The hunt for dark matter", pubDate = now - 13 * day, durationMs = 34 * minute),
    )

    // Newest first. The first one has just been started and is the current, playing episode.
    private val newEpisodes = listOf(
        item(history, "ep-501", "501. The Fall of Constantinople", pubDate = now - 2 * 60 * minute, durationMs = 65 * minute),
        item(football, "fd-881", "Matchday review", pubDate = now - 5 * 60 * minute, durationMs = 41 * minute),
        item(science, "sw-211", "Octopuses: the aliens among us", pubDate = now - day, durationMs = 0L),
        item(history, "ep-450", "450. The Titanic", pubDate = now - 300 * day, durationMs = 61 * minute),
    )

    private val fullState = HomeUiState(
        continueListening = continueListening,
        upNext = upNext,
        newEpisodes = newEpisodes,
        hasSubscriptions = true,
        isLoading = false,
        nowPlayingId = "$history#ep-501",
        isPlaying = true,
        now = now,
    )

    private val onlyNewState = HomeUiState(
        newEpisodes = newEpisodes,
        hasSubscriptions = true,
        isLoading = false,
        now = now,
    )

    private val caughtUpState = HomeUiState(hasSubscriptions = true, isLoading = false, now = now)

    private val noSubscriptionsState = HomeUiState(hasSubscriptions = false, isLoading = false, now = now)

    @Test
    fun full() = shoot("home-full", fullState)

    @Test
    fun fullDark() = shoot("home-full-dark", fullState, dark = true)

    @Test
    fun fullLargeText() = shoot("home-full-large-text", fullState, fontScale = 2f)

    @Test
    fun onlyNew() = shoot("home-only-new", onlyNewState)

    @Test
    fun onlyNewDark() = shoot("home-only-new-dark", onlyNewState, dark = true)

    @Test
    fun caughtUp() = shoot("home-caught-up", caughtUpState)

    @Test
    fun caughtUpDark() = shoot("home-caught-up-dark", caughtUpState, dark = true)

    @Test
    fun noSubscriptions() = shoot("home-no-subscriptions", noSubscriptionsState)

    @Test
    fun noSubscriptionsDark() = shoot("home-no-subscriptions-dark", noSubscriptionsState, dark = true)
}
