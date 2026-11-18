package com.podparar.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Home's "Continue listening" card shows a 4dp `download_stripe` as tall as the card only when the
 * episode is downloaded (DONE), even though the card sits in a lazy list with unbounded height.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ContinueCardStripeTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val minute = 60 * 1000L
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"
    private val stripe = "download_stripe"

    private val continueItem = EpisodeWithPodcast(
        episode = Episode(
            id = "$feedUrl#ep-500",
            podcastUrl = feedUrl,
            title = "Continue: The Retreat From Moscow",
            description = "",
            audioUrl = "https://audio.example.com/ep-500.mp3",
            imageUrl = null,
            pubDate = now - 3 * 24 * 60 * minute,
            durationMs = 50 * minute,
            positionMs = 20 * minute,
            played = false,
            lastPlayedAt = now - 60 * minute,
        ),
        podcastTitle = "The Rest Is History",
        podcastArtworkUrl = null,
    )

    private fun download(state: DownloadState) = Download(
        episodeId = continueItem.episode.id,
        state = state,
        filePath = if (state == DownloadState.DONE) "/data/files/ep-500.audio" else null,
        bytesDownloaded = 0L,
        totalBytes = 0L,
        updatedAt = now,
    )

    private fun show(download: Download?) {
        val state = HomeUiState(
            continueListening = listOf(continueItem),
            hasSubscriptions = true,
            isLoading = false,
            now = now,
            downloads = if (download == null) emptyMap() else mapOf(download.episodeId to download),
        )
        compose.setContent {
            PodPararTheme(darkTheme = false) {
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
        compose.waitForIdle()
    }

    private fun stripeCount(): Int =
        compose.onAllNodesWithTag(stripe, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun downloadedCardHasVisibleFourDpStripe() {
        show(download(DownloadState.DONE))
        compose.onNodeWithTag(stripe, useUnmergedTree = true)
            .assertWidthIsEqualTo(4.dp)
            .assertHeightIsAtLeast(1.dp)
    }

    @Test
    fun cardWithoutDownloadHasNoStripe() {
        show(null)
        assertEquals(0, stripeCount())
    }

    @Test
    fun queuedCardHasNoStripe() {
        show(download(DownloadState.QUEUED))
        assertEquals(0, stripeCount())
    }

    @Test
    fun runningCardHasNoStripe() {
        show(download(DownloadState.RUNNING))
        assertEquals(0, stripeCount())
    }

    @Test
    fun failedCardHasNoStripe() {
        show(download(DownloadState.FAILED))
        assertEquals(0, stripeCount())
    }
}
