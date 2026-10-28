package com.podparar.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.model.Podcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A show's page whose episodes are in every download state: downloaded, downloading at 40%, queued,
 * failed and not downloaded; saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class PodcastDownloadsScreenshotTest {
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
                        onDownload = {},
                        onRemoveDownload = {},
                        onToggleAutoDownload = {},
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
    private val megabyte = 1024L * 1024L

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

    // Newest first, as the store returns them.
    private val episodes = listOf(
        episode("ep-505", "505. The Battle of Hastings", pubDate = now - 2 * 60 * minute, durationMs = 62 * minute),
        episode("ep-504", "504. The Siege of Vienna", pubDate = now - day, durationMs = 58 * minute),
        episode("ep-503", "503. The Fall of Constantinople", pubDate = now - 4 * day, durationMs = 65 * minute),
        episode(
            "ep-502",
            "502. Napoleon in Russia",
            pubDate = now - 8 * day,
            durationMs = 52 * minute,
            positionMs = 20 * minute,
        ),
        episode("ep-501", "501. The Great Fire of London", pubDate = now - 12 * day, durationMs = 48 * minute),
    )

    private fun id(guid: String) = "$feedUrl#$guid"

    // One state per episode; ep-501 has no download at all.
    private val downloadsById: Map<String, Download> = listOf(
        Download(
            episodeId = id("ep-505"),
            state = DownloadState.DONE,
            filePath = "/data/user/0/com.podparar/files/episodes/ep-505.audio",
            bytesDownloaded = 60 * megabyte,
            totalBytes = 60 * megabyte,
            updatedAt = now - 30 * minute,
        ),
        Download(
            episodeId = id("ep-504"),
            state = DownloadState.RUNNING,
            filePath = null,
            bytesDownloaded = 20 * megabyte,
            totalBytes = 50 * megabyte, // 40%
            updatedAt = now,
        ),
        Download(
            episodeId = id("ep-503"),
            state = DownloadState.QUEUED,
            filePath = null,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            updatedAt = now,
        ),
        Download(
            episodeId = id("ep-502"),
            state = DownloadState.FAILED,
            filePath = null,
            bytesDownloaded = 5 * megabyte,
            totalBytes = 45 * megabyte,
            updatedAt = now - 5 * minute,
        ),
    ).associateBy { it.episodeId }

    private val downloadsState = PodcastUiState(
        podcast = podcast,
        episodes = episodes,
        isLoading = false,
        now = now,
        downloads = downloadsById,
    )

    @Test
    fun downloads() = shoot("podcast-downloads", downloadsState)

    @Test
    fun downloadsDark() = shoot("podcast-downloads-dark", downloadsState, dark = true)
}
