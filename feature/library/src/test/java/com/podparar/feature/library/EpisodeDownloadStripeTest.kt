package com.podparar.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.ui.EpisodeRow
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A downloaded [EpisodeRow] carries a 4dp stripe (tag `download_stripe`) as tall as the row, even
 * inside a LazyColumn where the height is unbounded (it once measured 0). Rows that are not downloaded
 * (none, queued, running, failed) have no stripe at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class EpisodeDownloadStripeTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val minute = 60 * 1000L
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"
    private val stripe = "download_stripe"

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

    private fun download(state: DownloadState) = Download(
        episodeId = episode.id,
        state = state,
        filePath = if (state == DownloadState.DONE) "/data/files/ep-1.audio" else null,
        bytesDownloaded = 0L,
        totalBytes = 0L,
        updatedAt = now,
    )

    private fun show(download: Download?) {
        compose.setContent {
            PodPararTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn {
                        item {
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
            }
        }
        compose.waitForIdle()
    }

    private fun stripeCount(): Int =
        compose.onAllNodesWithTag(stripe, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun doneRowInLazyColumnHasVisibleFourDpStripe() {
        show(download(DownloadState.DONE))
        compose.onNodeWithTag(stripe, useUnmergedTree = true)
            .assertWidthIsEqualTo(4.dp)
            .assertHeightIsAtLeast(1.dp)
    }

    @Test
    fun doneRowStripeIsAsTallAsTheRow() {
        show(download(DownloadState.DONE))
        val stripeHeight = compose.onNodeWithTag(stripe, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        val rowHeight = compose.onNodeWithText("Alpha episode", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        // The row (a padded card) is at least as tall as its title text; the stripe spans the whole card.
        assertTrue("stripe $stripeHeight shorter than title $rowHeight", stripeHeight >= rowHeight)
    }

    @Test
    fun notDownloadedRowsHaveNoStripe() {
        show(null)
        assertEquals(0, stripeCount())
    }

    @Test
    fun queuedRowHasNoStripe() {
        show(download(DownloadState.QUEUED))
        assertEquals(0, stripeCount())
    }

    @Test
    fun runningRowHasNoStripe() {
        show(download(DownloadState.RUNNING))
        assertEquals(0, stripeCount())
    }

    @Test
    fun failedRowHasNoStripe() {
        show(download(DownloadState.FAILED))
        assertEquals(0, stripeCount())
    }
}
