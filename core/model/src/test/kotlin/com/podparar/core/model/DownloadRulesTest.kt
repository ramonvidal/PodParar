package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When played downloads are deleted, which new episodes are auto-downloaded, and the alert's title. */
class DownloadRulesTest {
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val now = 1_727_863_200_000L // 2024-10-02T10:00:00Z

    private fun episode(
        feedUrl: String = "https://a.example/feed",
        guid: String = "1",
        pubDate: Long = now - day,
        played: Boolean = false,
        lastPlayedAt: Long = 0L,
    ) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = "Episode $guid",
        description = "",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = 60_000L,
        played = played,
        lastPlayedAt = lastPlayedAt,
    )

    private fun download(
        episode: Episode,
        state: DownloadState = DownloadState.DONE,
        updatedAt: Long = now - 10 * day,
    ) = Download(
        episodeId = episode.id,
        state = state,
        filePath = if (state == DownloadState.DONE) "/files/episodes/x.audio" else null,
        bytesDownloaded = 1_000L,
        totalBytes = 1_000L,
        updatedAt = updatedAt,
    )

    // --- KEEP_PLAYED_MS ---

    @Test
    fun `played downloads are kept for one day`() {
        assertEquals(86_400_000L, DownloadRules.KEEP_PLAYED_MS)
    }

    // --- shouldDelete ---

    @Test
    fun `played exactly one day ago is deleted`() {
        val ep = episode(played = true, lastPlayedAt = now - day)
        assertTrue(DownloadRules.shouldDelete(ep, download(ep), now))
    }

    @Test
    fun `played one millisecond less than a day ago is kept`() {
        val ep = episode(played = true, lastPlayedAt = now - day + 1)
        assertFalse(DownloadRules.shouldDelete(ep, download(ep), now))
    }

    @Test
    fun `played long ago is deleted`() {
        val ep = episode(played = true, lastPlayedAt = now - 30 * day)
        assertTrue(DownloadRules.shouldDelete(ep, download(ep), now))
    }

    @Test
    fun `played an hour ago is kept`() {
        val ep = episode(played = true, lastPlayedAt = now - hour)
        assertFalse(DownloadRules.shouldDelete(ep, download(ep), now))
    }

    @Test
    fun `the episode's lastPlayedAt wins over an old download time`() {
        val ep = episode(played = true, lastPlayedAt = now - hour)
        assertFalse(DownloadRules.shouldDelete(ep, download(ep, updatedAt = now - 30 * day), now))
    }

    @Test
    fun `with no lastPlayedAt the download's updatedAt is used`() {
        val ep = episode(played = true, lastPlayedAt = 0L)
        assertTrue(DownloadRules.shouldDelete(ep, download(ep, updatedAt = now - day), now))
        assertTrue(DownloadRules.shouldDelete(ep, download(ep, updatedAt = now - 2 * day), now))
        assertFalse(DownloadRules.shouldDelete(ep, download(ep, updatedAt = now - day + 1), now))
        assertFalse(DownloadRules.shouldDelete(ep, download(ep, updatedAt = now), now))
    }

    @Test
    fun `an unplayed episode is never deleted`() {
        val listenedLongAgo = episode(played = false, lastPlayedAt = now - 30 * day)
        assertFalse(DownloadRules.shouldDelete(listenedLongAgo, download(listenedLongAgo), now))
        val neverPlayed = episode(played = false, lastPlayedAt = 0L)
        assertFalse(DownloadRules.shouldDelete(neverPlayed, download(neverPlayed, updatedAt = now - 30 * day), now))
    }

    @Test
    fun `a download that is not DONE is never deleted by this rule`() {
        val ep = episode(played = true, lastPlayedAt = now - 30 * day)
        for (state in listOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.FAILED)) {
            assertFalse(state.name, DownloadRules.shouldDelete(ep, download(ep, state = state), now))
        }
    }

    // --- autoDownloadPicks ---

    private val a = "https://a.example/feed"
    private val b = "https://b.example/feed"
    private val c = "https://c.example/feed"

    @Test
    fun `picks the newest new episode of each auto-download show`() {
        val aOld = episode(a, "old", pubDate = now - 2 * day)
        val aNew = episode(a, "new", pubDate = now - hour)
        val aMid = episode(a, "mid", pubDate = now - day)
        val bOnly = episode(b, "only", pubDate = now - 3 * day)

        val picks = DownloadRules.autoDownloadPicks(listOf(aOld, bOnly, aNew, aMid), setOf(a, b))

        assertEquals(setOf(aNew.id, bOnly.id), picks.map { it.id }.toSet())
        assertEquals(2, picks.size)
    }

    @Test
    fun `newest is by pubDate, not list order`() {
        val first = episode(a, "listed-first", pubDate = now - 5 * day)
        val last = episode(a, "listed-last", pubDate = now)
        assertEquals(listOf(last), DownloadRules.autoDownloadPicks(listOf(first, last), setOf(a)))
        assertEquals(listOf(last), DownloadRules.autoDownloadPicks(listOf(last, first), setOf(a)))
    }

    @Test
    fun `shows without auto-download are ignored`() {
        val aEp = episode(a, "1", pubDate = now)
        val bEp = episode(b, "1", pubDate = now)
        val cEp = episode(c, "1", pubDate = now)

        assertEquals(listOf(bEp), DownloadRules.autoDownloadPicks(listOf(aEp, bEp, cEp), setOf(b)))
    }

    @Test
    fun `no auto-download shows means no picks`() {
        val eps = listOf(episode(a, "1"), episode(b, "1"))
        assertEquals(emptyList<Episode>(), DownloadRules.autoDownloadPicks(eps, emptySet()))
    }

    @Test
    fun `no new episodes means no picks`() {
        assertEquals(emptyList<Episode>(), DownloadRules.autoDownloadPicks(emptyList(), setOf(a, b)))
    }

    @Test
    fun `an auto-download show with no new episodes adds nothing`() {
        val aEp = episode(a, "1", pubDate = now)
        assertEquals(listOf(aEp), DownloadRules.autoDownloadPicks(listOf(aEp), setOf(a, b, c)))
    }

    @Test
    fun `a single new episode is picked`() {
        val aEp = episode(a, "1", pubDate = now)
        assertEquals(listOf(aEp), DownloadRules.autoDownloadPicks(listOf(aEp), setOf(a)))
    }

    // --- newEpisodesTitle ---

    @Test
    fun `no title when there is nothing new`() {
        assertNull(DownloadRules.newEpisodesTitle(0))
        assertNull(DownloadRules.newEpisodesTitle(-1))
        assertNull(DownloadRules.newEpisodesTitle(Int.MIN_VALUE))
    }

    @Test
    fun `one new episode is singular`() {
        assertEquals("1 new episode", DownloadRules.newEpisodesTitle(1))
    }

    @Test
    fun `more than one is plural`() {
        assertEquals("2 new episodes", DownloadRules.newEpisodesTitle(2))
        assertEquals("12 new episodes", DownloadRules.newEpisodesTitle(12))
        assertEquals("100 new episodes", DownloadRules.newEpisodesTitle(100))
    }
}
