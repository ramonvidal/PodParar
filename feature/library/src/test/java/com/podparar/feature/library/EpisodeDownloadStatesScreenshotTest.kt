package com.podparar.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.ui.EpisodeRow
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Episode rows in every download state, stacked: not downloaded, queued, downloading at 0% and 42%,
 * downloaded and failed; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class EpisodeDownloadStatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private val minute = 60 * 1000L
    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

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

    private fun download(episode: Episode, state: DownloadState, bytes: Long = 0L, total: Long = 0L) =
        Download(
            episodeId = episode.id,
            state = state,
            filePath = if (state == DownloadState.DONE) "/data/files/${episode.id.hashCode()}.audio" else null,
            bytesDownloaded = bytes,
            totalBytes = total,
            updatedAt = now,
        )

    private val none = episode("ep-none", "Alpha: not downloaded", 1)
    private val queued = episode("ep-queued", "Bravo: queued", 2)
    private val running = episode("ep-running", "Charlie: downloading 42%", 3)
    private val done = episode("ep-done", "Delta: downloaded", 4)
    private val failed = episode("ep-failed", "Echo: failed", 5)

    private val rows: List<Pair<Episode, Download?>> = listOf(
        none to null,
        queued to download(queued, DownloadState.QUEUED),
        running to download(running, DownloadState.RUNNING, bytes = 42, total = 100),
        done to download(done, DownloadState.DONE, bytes = 100, total = 100),
        failed to download(failed, DownloadState.FAILED, bytes = 5, total = 100),
    )

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        rows.forEach { (episode, download) ->
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
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun downloadStates() = shoot("episode-download-states", dark = false)

    @Test
    fun downloadStatesDark() = shoot("episode-download-states-dark", dark = true)
}
