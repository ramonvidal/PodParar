package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Download.progress and the Podcast.autoDownload default. */
class DownloadTest {
    private val delta = 0.0001f

    private fun download(
        state: DownloadState,
        bytes: Long,
        total: Long,
        filePath: String? = null,
    ) = Download(
        episodeId = "https://a.example/feed#1",
        state = state,
        filePath = filePath,
        bytesDownloaded = bytes,
        totalBytes = total,
        updatedAt = 1_000L,
    )

    @Test
    fun `progress is bytes over total`() {
        assertEquals(0.4f, download(DownloadState.RUNNING, 400, 1_000).progress, delta)
        assertEquals(0.5f, download(DownloadState.RUNNING, 1, 2).progress, delta)
    }

    @Test
    fun `progress is 0 before any bytes arrive`() {
        assertEquals(0f, download(DownloadState.QUEUED, 0, 1_000).progress, delta)
        assertEquals(0f, download(DownloadState.RUNNING, 0, 1_000).progress, delta)
    }

    @Test
    fun `progress is 0 when the total is unknown`() {
        assertEquals(0f, download(DownloadState.RUNNING, 5_000, 0).progress, delta)
        assertEquals(0f, download(DownloadState.QUEUED, 0, 0).progress, delta)
        assertEquals(0f, download(DownloadState.FAILED, 123, 0).progress, delta)
    }

    @Test
    fun `progress is clamped to 0 to 1`() {
        assertEquals(1f, download(DownloadState.RUNNING, 2_000, 1_000).progress, delta)
        assertEquals(0f, download(DownloadState.RUNNING, -5, 1_000).progress, delta)
    }

    @Test
    fun `progress is 1 when DONE whatever the byte counts say`() {
        assertEquals(1f, download(DownloadState.DONE, 1_000, 1_000, "/f").progress, delta)
        assertEquals(1f, download(DownloadState.DONE, 300, 0, "/f").progress, delta)
        assertEquals(1f, download(DownloadState.DONE, 0, 0, "/f").progress, delta)
        assertEquals(1f, download(DownloadState.DONE, 10, 1_000, "/f").progress, delta)
    }

    @Test
    fun `a full but not DONE download is 1`() {
        assertEquals(1f, download(DownloadState.RUNNING, 1_000, 1_000).progress, delta)
    }

    @Test
    fun `download states are the four in the contract`() {
        assertEquals(
            listOf("QUEUED", "RUNNING", "DONE", "FAILED"),
            DownloadState.entries.map { it.name },
        )
    }

    @Test
    fun `refresh result holds its values`() {
        val result = RefreshResult(failedFeeds = 2, newEpisodes = emptyList())
        assertEquals(2, result.failedFeeds)
        assertEquals(emptyList<Episode>(), result.newEpisodes)
        assertEquals(result, RefreshResult(2, emptyList()))
    }

    @Test
    fun `podcasts are not auto-downloaded by default`() {
        val show = Podcast(
            feedUrl = "https://a.example/feed",
            title = "A",
            author = "Author",
            description = "",
            artworkUrl = null,
            lastRefreshed = 0L,
        )
        assertFalse(show.autoDownload)
        assertEquals(true, show.copy(autoDownload = true).autoDownload)
    }
}
