package com.podparar.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.SkipLengths
import com.podparar.core.model.SleepTimer
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The full player with a sleep timer running (timed and end of episode) and custom skip lengths; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class PlayerSleepTimerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val second = 1000L
    private val minute = 60 * second

    // A fixed clock so the timer label never depends on when the test runs.
    private val now = 1_750_000_000_000L
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    private val playing = NowPlaying(
        episodeId = "$feedUrl#ep-501",
        podcastUrl = feedUrl,
        title = "501. The Fall of Constantinople",
        podcastTitle = "The Rest Is History",
        artworkUrl = null,
        isPlaying = true,
        isBuffering = false,
        positionMs = 12 * minute + 34 * second,
        durationMs = 65 * minute,
        speed = 1.5f,
    )

    // 23 minutes left: the timer button reads "23 min".
    private val timed = SleepTimer(endsAt = now + 23 * minute, endOfEpisode = false)

    // Stops when the episode ends: the timer button reads "End of episode".
    private val endOfEpisode = SleepTimer(endsAt = null, endOfEpisode = true)

    private fun shoot(
        name: String,
        timer: SleepTimer,
        dark: Boolean = false,
        skipBackMs: Long = SkipLengths.DEFAULT_BACK_MS,
        skipForwardMs: Long = SkipLengths.DEFAULT_FORWARD_MS,
    ) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    PlayerContent(
                        state = playing,
                        onClose = {},
                        onPlayPause = {},
                        onSkipBack = {},
                        onSkipForward = {},
                        onSeek = {},
                        onSpeedChange = {},
                        modifier = Modifier.fillMaxSize(),
                        sleepTimer = timer,
                        now = now,
                        skipBackMs = skipBackMs,
                        skipForwardMs = skipForwardMs,
                        onSetSleepTimer = {},
                        onSleepAtEndOfEpisode = {},
                        onCancelSleepTimer = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun timedTimer() = shoot(
        "player-sleep-timer-23-min",
        timed,
        skipBackMs = 5_000L,
        skipForwardMs = 10_000L,
    )

    @Test
    fun timedTimerDark() = shoot(
        "player-sleep-timer-23-min-dark",
        timed,
        dark = true,
        skipBackMs = 5_000L,
        skipForwardMs = 10_000L,
    )

    @Test
    fun endOfEpisode() = shoot("player-sleep-timer-end-of-episode", endOfEpisode)

    @Test
    fun endOfEpisodeDark() = shoot("player-sleep-timer-end-of-episode-dark", endOfEpisode, dark = true)
}
