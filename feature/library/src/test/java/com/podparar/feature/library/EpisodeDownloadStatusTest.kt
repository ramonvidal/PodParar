package com.podparar.feature.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.ui.EpisodeRow
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The status text and controls an [EpisodeRow] shows for each download state: "Downloading 42%" /
 * "Downloading" with a cancel control while queued or running, "Downloaded" once done, and neither
 * text when there is no download.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class EpisodeDownloadStatusTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val minute = 60 * 1000L
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    private val episode = Episode(
        id = "$feedUrl#ep-1",
        podcastUrl = feedUrl,
        title = "Alpha episode",
        description = "",
        audioUrl = "https://audio.example.com/ep-1.mp3",
        imageUrl = null,
        pubDate = now - 24 * 60 * minute,
        durationMs = 50 * minute,
        positionMs = 0L,
        played = false,
    )

    private fun download(state: DownloadState, bytes: Long = 0L, total: Long = 0L) = Download(
        episodeId = episode.id,
        state = state,
        filePath = if (state == DownloadState.DONE) "/data/files/ep-1.audio" else null,
        bytesDownloaded = bytes,
        totalBytes = total,
        updatedAt = now,
    )

    private fun show(download: Download?) {
        compose.setContent {
            PodPararTheme(darkTheme = false) {
                Surface {
                    EpisodeRow(
                        episode = episode,
                        now = now,
                        onClick = {},
                        download = download,
                        onDownload = {},
                        onRemoveDownload = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun textCount(text: String, substring: Boolean = false): Int =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun descriptionCount(description: String): Int =
        compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    @Test
    fun runningShowsPercentage() {
        show(download(DownloadState.RUNNING, bytes = 42, total = 100))
        assertEquals(1, textCount("Downloading 42%", substring = true))
        assertEquals(1, descriptionCount("Cancel download"))
        assertEquals(0, textCount("Downloaded", substring = true))
    }

    @Test
    fun queuedShowsPlainDownloading() {
        show(download(DownloadState.QUEUED))
        assertEquals(1, textCount("Downloading", substring = true))
        assertEquals(0, textCount("%", substring = true))
        assertEquals(1, descriptionCount("Cancel download"))
    }

    @Test
    fun runningWithUnknownSizeShowsPlainDownloading() {
        show(download(DownloadState.RUNNING, bytes = 1024, total = 0))
        assertEquals(1, textCount("Downloading", substring = true))
        assertEquals(0, textCount("%", substring = true))
        assertEquals(1, descriptionCount("Cancel download"))
    }

    @Test
    fun doneShowsDownloaded() {
        show(download(DownloadState.DONE, bytes = 100, total = 100))
        assertEquals(1, textCount("Downloaded", substring = true))
        assertEquals(1, descriptionCount("Downloaded"))
        assertEquals(0, textCount("Downloading", substring = true))
        assertEquals(0, descriptionCount("Cancel download"))
    }

    @Test
    fun noDownloadShowsNeitherText() {
        show(null)
        assertEquals(0, textCount("Downloading", substring = true))
        assertEquals(0, textCount("Downloaded", substring = true))
        assertEquals(0, descriptionCount("Cancel download"))
        assertEquals(0, descriptionCount("Downloaded"))
    }
}
