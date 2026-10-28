package com.podparar.feature.player

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
import com.podparar.core.model.NowPlaying
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The full player: playing, buffering, unknown duration, nothing loaded, and large text; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class PlayerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: NowPlaying?, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        PlayerContent(
                            state = state,
                            onClose = {},
                            onPlayPause = {},
                            onSkipBack = {},
                            onSkipForward = {},
                            onSeek = {},
                            onSpeedChange = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val second = 1000L
    private val minute = 60 * second
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    // 12:34 into a 1:05:00 episode: "12:34" on the left, "-52:26" on the right, at 1×.
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
        speed = 1.0f,
    )

    // Buffering just after a seek: the play/pause button shows a spinner.
    private val buffering = playing.copy(
        isPlaying = false,
        isBuffering = true,
        positionMs = 30 * minute,
    )

    // A live-ish stream with no known length, at 1.5×: slider disabled, only the elapsed time.
    private val unknownDuration = playing.copy(
        episodeId = "$feedUrl#ep-bonus",
        title = "Bonus: Your Questions Answered on Rome, the Vikings and the Cold War",
        positionMs = 5 * minute + 5 * second,
        durationMs = 0L,
        speed = 1.5f,
    )

    @Test
    fun playing() = shoot("player-playing", playing)

    @Test
    fun playingDark() = shoot("player-playing-dark", playing, dark = true)

    @Test
    fun playingLargeText() = shoot("player-playing-large-text", playing, fontScale = 2f)

    @Test
    fun buffering() = shoot("player-buffering", buffering)

    @Test
    fun bufferingDark() = shoot("player-buffering-dark", buffering, dark = true)

    @Test
    fun unknownDuration() = shoot("player-unknown-duration", unknownDuration)

    @Test
    fun unknownDurationDark() = shoot("player-unknown-duration-dark", unknownDuration, dark = true)

    @Test
    fun nothingPlaying() = shoot("player-nothing-playing", null)

    @Test
    fun nothingPlayingDark() = shoot("player-nothing-playing-dark", null, dark = true)
}
