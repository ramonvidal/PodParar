package com.podparar.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.model.Podcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The visible download button on each episode row of a show's page: its description and its tap
 * action depend on the episode's download state (none, running, queued, done, failed).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class EpisodeDownloadButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private val minute = 60 * 1000L
    private val megabyte = 1024L * 1024L

    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    private val podcast = Podcast(
        feedUrl = feedUrl,
        title = "The Rest Is History",
        author = "Goalhanger",
        description = "History.",
        artworkUrl = null,
        lastRefreshed = now,
    )

    private fun episode(guid: String, title: String, daysAgo: Long) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = title,
        description = "",
        audioUrl = "https://audio.example.com/$guid.mp3",
        imageUrl = null,
        pubDate = now - daysAgo * day,
        durationMs = 50 * minute,
        positionMs = 0L,
        played = false,
    )

    private val none = episode("ep-none", "Alpha: not downloaded", 1)
    private val running = episode("ep-running", "Bravo: downloading", 2)
    private val queued = episode("ep-queued", "Charlie: queued", 3)
    private val done = episode("ep-done", "Delta: downloaded", 4)
    private val failed = episode("ep-failed", "Echo: failed", 5)

    private fun download(episode: Episode, state: DownloadState, bytes: Long = 0L, total: Long = 0L) =
        Download(
            episodeId = episode.id,
            state = state,
            filePath = if (state == DownloadState.DONE) "/data/files/${episode.id.hashCode()}.audio" else null,
            bytesDownloaded = bytes,
            totalBytes = total,
            updatedAt = now,
        )

    private val state = PodcastUiState(
        podcast = podcast,
        episodes = listOf(none, running, queued, done, failed),
        isLoading = false,
        now = now,
        downloads = listOf(
            download(running, DownloadState.RUNNING, bytes = 20 * megabyte, total = 50 * megabyte),
            download(queued, DownloadState.QUEUED),
            download(done, DownloadState.DONE, bytes = 60 * megabyte, total = 60 * megabyte),
            download(failed, DownloadState.FAILED, bytes = 5 * megabyte, total = 45 * megabyte),
        ).associateBy { it.episodeId },
    )

    // Callback arguments are recorded as ids, whatever type the callbacks receive.
    private val downloadCalls = mutableListOf<String>()
    private val removeCalls = mutableListOf<String>()

    private fun idOf(argument: Any?): String = when (argument) {
        is Episode -> argument.id
        is String -> argument
        else -> error("Unexpected callback argument: $argument")
    }

    private fun show() {
        compose.setContent {
            PodPararTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    PodcastContent(
                        state = state,
                        onBack = {},
                        onRefresh = {},
                        onUnsubscribe = {},
                        onPlayEpisode = {},
                        onTogglePlayed = {},
                        onDownload = { downloadCalls += idOf(it) },
                        onRemoveDownload = { removeCalls += idOf(it) },
                        onToggleAutoDownload = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(description: String): Int =
        compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun click(description: String) {
        compose.onAllNodesWithContentDescription(description)[0].performClick()
        compose.waitForIdle()
    }

    // One button per state, exactly once (every state has exactly one episode)

    @Test
    fun eachStateHasItsButtonOnce() {
        show()
        assertEquals(1, count("Download"))
        assertEquals(2, count("Cancel download")) // RUNNING and QUEUED
        assertEquals(1, count("Downloaded"))
        assertEquals(1, count("Download failed. Tap to try again"))
    }

    // Taps

    @Test
    fun downloadButtonStartsTheDownload() {
        show()
        click("Download")
        assertEquals(listOf(none.id), downloadCalls)
        assertEquals(emptyList<String>(), removeCalls)
    }

    @Test
    fun cancelButtonCancelsRunningThenQueued() {
        show()
        // Rows are in list order: running comes before queued.
        compose.onAllNodesWithContentDescription("Cancel download")[0].performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Cancel download")[1].performClick()
        compose.waitForIdle()
        assertEquals(listOf(running.id, queued.id), removeCalls)
        assertEquals(emptyList<String>(), downloadCalls)
    }

    @Test
    fun failedButtonRetriesTheDownload() {
        show()
        click("Download failed. Tap to try again")
        assertEquals(listOf(failed.id), downloadCalls)
        assertEquals(emptyList<String>(), removeCalls)
    }

    // Downloaded: asks before removing

    @Test
    fun downloadedButtonAsksBeforeRemoving() {
        show()
        compose.onNodeWithText("Remove this download?").assertDoesNotExistCompat()
        click("Downloaded")
        compose.onNodeWithText("Remove this download?").assertExists()
        compose.onNodeWithText("The episode stays in your list, but you'll need internet to play it again.")
            .assertExists()
        compose.onNodeWithText("Remove download").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        assertEquals(emptyList<String>(), removeCalls)
    }

    @Test
    fun cancelClosesTheDialogWithoutRemoving() {
        show()
        click("Downloaded")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove this download?").assertDoesNotExistCompat()
        assertEquals(emptyList<String>(), removeCalls)
        assertEquals(emptyList<String>(), downloadCalls)
    }

    @Test
    fun removeDownloadRemovesThatEpisode() {
        show()
        click("Downloaded")
        compose.onNodeWithText("Remove download").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove this download?").assertDoesNotExistCompat()
        assertEquals(listOf(done.id), removeCalls)
        assertEquals(emptyList<String>(), downloadCalls)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() =
        assertDoesNotExist()
}
