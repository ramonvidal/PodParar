package com.podparar.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.Chapter
import com.podparar.core.model.NowPlaying
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The chapter row in the player and the chapters list; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class ChaptersScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val second = 1000L
    private val minute = 60 * second
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    private val chapters = listOf(
        Chapter(0, "Introduction"),
        Chapter(4 * minute, "The walls of Constantinople"),
        Chapter(18 * minute + 30 * second, "The siege begins"),
        Chapter(35 * minute, "The final assault"),
        Chapter(52 * minute + 15 * second, "Aftermath", imageUrl = "https://img.example/aftermath.jpg"),
        Chapter(61 * minute, "Listener questions and a very long chapter title that does not fit on one line", url = "https://example.com/q"),
    )

    // 20:00 into a 1:05:00 episode: inside the third chapter, "The siege begins".
    private val inChapterThree = NowPlaying(
        episodeId = "$feedUrl#ep-501",
        podcastUrl = feedUrl,
        title = "501. The Fall of Constantinople",
        podcastTitle = "The Rest Is History",
        artworkUrl = null,
        isPlaying = true,
        isBuffering = false,
        positionMs = 20 * minute,
        durationMs = 65 * minute,
        speed = 1.0f,
        chapters = chapters,
    )

    private fun shootPlayer(name: String, dark: Boolean) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    PlayerContent(
                        state = inChapterThree,
                        onClose = {},
                        onPlayPause = {},
                        onSkipBack = {},
                        onSkipForward = {},
                        onSeek = {},
                        onSpeedChange = {},
                        onSeekToChapter = {},
                        onPreviousChapter = {},
                        onNextChapter = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shootSheet(name: String, dark: Boolean) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    ChaptersSheetContent(chapters = chapters, currentIndex = 2, onSelect = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun playerWithChapters() = shootPlayer("player-chapters", dark = false)

    @Test
    fun playerWithChaptersDark() = shootPlayer("player-chapters-dark", dark = true)

    @Test
    fun chaptersSheet() = shootSheet("player-chapters-sheet", dark = false)

    @Test
    fun chaptersSheetDark() = shootSheet("player-chapters-sheet-dark", dark = true)
}
