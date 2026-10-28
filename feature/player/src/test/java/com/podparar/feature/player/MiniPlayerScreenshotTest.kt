package com.podparar.feature.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.NowPlaying
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The mini player bar, playing, paused and with titles too long for one line; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class MiniPlayerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: NowPlaying, dark: Boolean = false) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                // Only the bar is captured, on the app background it sits on above the tabs.
                Surface(Modifier.fillMaxWidth().testTag(TAG)) {
                    MiniPlayerContent(
                        state = state,
                        onPlayPause = {},
                        onOpen = {},
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val minute = 60 * 1000L
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    // A third of the way through, so the progress line along the top is clearly visible.
    private val playing = NowPlaying(
        episodeId = "$feedUrl#ep-501",
        podcastUrl = feedUrl,
        title = "501. The Fall of Constantinople",
        podcastTitle = "The Rest Is History",
        artworkUrl = null,
        isPlaying = true,
        isBuffering = false,
        positionMs = 20 * minute,
        durationMs = 60 * minute,
        speed = 1.0f,
    )

    private val longTitles = playing.copy(
        episodeId = "$feedUrl#ep-500",
        title = "500. Napoleon in Russia: The Retreat From Moscow and the Destruction of the Grande Armée, Part Two",
        podcastTitle = "You're Dead to Me: Comedy Meets History, Told by Historians and Comedians",
        positionMs = 45 * minute,
    )

    @Test
    fun playing() = shoot("mini-player-playing", playing)

    @Test
    fun playingDark() = shoot("mini-player-playing-dark", playing, dark = true)

    @Test
    fun paused() = shoot("mini-player-paused", playing.copy(isPlaying = false))

    @Test
    fun pausedDark() = shoot("mini-player-paused-dark", playing.copy(isPlaying = false), dark = true)

    @Test
    fun longTitles() = shoot("mini-player-long-titles", longTitles)

    @Test
    fun longTitlesDark() = shoot("mini-player-long-titles-dark", longTitles, dark = true)

    private companion object {
        const val TAG = "mini-player"
    }
}
