package com.podparar.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.Episode
import com.podparar.core.model.Podcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A show's page while one of its episodes is the current, playing one; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class PodcastPlayingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: PodcastUiState, dark: Boolean = false) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    PodcastContent(
                        state = state,
                        onBack = {},
                        onRefresh = {},
                        onUnsubscribe = {},
                        onPlayEpisode = {},
                        onTogglePlayed = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // Midday UTC, so "Today" / "Yesterday" hold in any nearby zone the tests run in.
    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private val minute = 60 * 1000L

    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    private val podcast = Podcast(
        feedUrl = feedUrl,
        title = "The Rest Is History",
        author = "Goalhanger",
        description = "Tom Holland and Dominic Sandbrook guide you through the past.",
        artworkUrl = null,
        lastRefreshed = now,
    )

    private fun episode(
        guid: String,
        title: String,
        pubDate: Long,
        durationMs: Long,
        positionMs: Long = 0L,
        played: Boolean = false,
    ) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = title,
        description = "",
        audioUrl = "https://audio.example.com/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = durationMs,
        positionMs = positionMs,
        played = played,
    )

    // Newest first, as the store returns them. The second one is loaded in the player and playing.
    private val episodes = listOf(
        episode("ep-502", "502. The Siege of Vienna", pubDate = now - 2 * 60 * minute, durationMs = 58 * minute),
        episode(
            "ep-501",
            "501. The Fall of Constantinople",
            pubDate = now - day,
            durationMs = 65 * minute,
            positionMs = 12 * minute,
        ),
        episode(
            "ep-500",
            "500. Napoleon in Russia",
            pubDate = now - 5 * day,
            durationMs = 52 * minute,
            positionMs = 20 * minute,
        ),
        episode(
            "ep-499",
            "499. The Great Fire of London",
            pubDate = now - 10 * day,
            durationMs = 48 * minute,
            positionMs = 0L,
            played = true,
        ),
    )

    private val playingState = PodcastUiState(
        podcast = podcast,
        episodes = episodes,
        isLoading = false,
        now = now,
        nowPlayingId = "$feedUrl#ep-501",
        isPlaying = true,
    )

    @Test
    fun playing() = shoot("podcast-playing", playingState)

    @Test
    fun playingDark() = shoot("podcast-playing-dark", playingState, dark = true)
}
