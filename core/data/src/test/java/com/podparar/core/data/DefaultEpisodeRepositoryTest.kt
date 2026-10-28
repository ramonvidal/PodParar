package com.podparar.core.data

import com.podparar.core.model.Episode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DefaultEpisodeRepository on a fake store and a fixed clock: saving positions, the played rule, pass-throughs. */
class DefaultEpisodeRepositoryTest {
    private val now = 1_727_863_200_000L // 2024-10-02T10:00:00Z
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val store = FakePodcastStore()
    private val repository = DefaultEpisodeRepository(store, clock)

    private val url = "https://example.com/feed.xml"
    private val hour = 3_600_000L

    private fun seeded(durationMs: Long = hour, positionMs: Long = 0L, played: Boolean = false): Episode {
        val ep = episode(url, "1").copy(durationMs = durationMs, positionMs = positionMs, played = played)
        store.seed(podcast(url), listOf(ep))
        return ep
    }

    private suspend fun stored(id: String): Episode = requireNotNull(store.episode(id))

    // savePosition

    @Test
    fun `a normal save stores the position with the clock time`() = runTest {
        val ep = seeded()
        repository.savePosition(ep.id, 754_000L, hour)

        assertEquals(listOf("savePosition(${ep.id}, 754000, $hour, $now)"), store.playbackCalls)
        val saved = stored(ep.id)
        assertEquals(754_000L, saved.positionMs)
        assertEquals(now, saved.lastPlayedAt)
        assertFalse(saved.played)
    }

    @Test
    fun `a negative position is stored as zero`() = runTest {
        val ep = seeded()
        repository.savePosition(ep.id, -5_000L, hour)

        assertEquals(listOf("savePosition(${ep.id}, 0, $hour, $now)"), store.playbackCalls)
        assertEquals(0L, stored(ep.id).positionMs)
        assertFalse(stored(ep.id).played)
    }

    @Test
    fun `exactly thirty seconds before the end is a normal save`() = runTest {
        val ep = seeded()
        repository.savePosition(ep.id, hour - 30_000L, hour)

        assertEquals(listOf("savePosition(${ep.id}, ${hour - 30_000L}, $hour, $now)"), store.playbackCalls)
        assertFalse(stored(ep.id).played)
        assertEquals(hour - 30_000L, stored(ep.id).positionMs)
    }

    @Test
    fun `a save near the end marks the episode played`() = runTest {
        val ep = seeded()
        repository.savePosition(ep.id, hour - 10_000L, hour)

        assertEquals("setPlayed(${ep.id}, true)", store.playbackCalls.last())
        assertTrue(store.playbackCalls.none { it.startsWith("setPlayed(${ep.id}, false") })
        val saved = stored(ep.id)
        assertTrue(saved.played)
        assertEquals(0L, saved.positionMs)
    }

    @Test
    fun `a save at or past the end marks the episode played`() = runTest {
        val ep = seeded()
        repository.savePosition(ep.id, hour, hour)
        assertTrue(stored(ep.id).played)
        assertEquals("setPlayed(${ep.id}, true)", store.playbackCalls.last())

        val other = episode(url, "2").copy(durationMs = hour)
        store.seed(podcast(url), listOf(stored(ep.id), other))
        repository.savePosition(other.id, hour + 5_000L, hour)
        assertTrue(stored(other.id).played)
        assertEquals(0L, stored(other.id).positionMs)
    }

    @Test
    fun `a near-end save also stores the player's duration when the stored one is unknown`() = runTest {
        val ep = seeded(durationMs = 0L)
        repository.savePosition(ep.id, hour - 5_000L, hour)

        val saved = stored(ep.id)
        assertTrue(saved.played)
        assertEquals(0L, saved.positionMs)
        assertEquals(hour, saved.durationMs)
        // The duration goes in through savePosition, before the episode is marked played.
        val durationSave = store.playbackCalls.indexOfFirst { it.startsWith("savePosition(${ep.id}, ") && it.contains(", $hour, ") }
        val played = store.playbackCalls.indexOf("setPlayed(${ep.id}, true)")
        assertTrue("calls: ${store.playbackCalls}", durationSave >= 0)
        assertTrue("calls: ${store.playbackCalls}", durationSave < played)
    }

    @Test
    fun `with unknown duration a save is never played`() = runTest {
        val ep = seeded(durationMs = 0L)
        repository.savePosition(ep.id, 10 * hour, 0L)

        assertEquals(listOf("savePosition(${ep.id}, ${10 * hour}, 0, $now)"), store.playbackCalls)
        val saved = stored(ep.id)
        assertFalse(saved.played)
        assertEquals(10 * hour, saved.positionMs)
        assertEquals(0L, saved.durationMs)
    }

    @Test
    fun `a save with unknown duration keeps the stored duration`() = runTest {
        val ep = seeded(durationMs = hour)
        repository.savePosition(ep.id, 60_000L, 0L)

        assertEquals(hour, stored(ep.id).durationMs)
        assertEquals(60_000L, stored(ep.id).positionMs)
    }

    @Test
    fun `saving an unknown episode does not fail or add anything`() = runTest {
        val ep = seeded()
        repository.savePosition("https://other.example/feed#nope", 1_000L, hour)
        repository.savePosition("https://other.example/feed#nope", hour, hour)

        assertNull(store.episode("https://other.example/feed#nope"))
        assertEquals(ep, stored(ep.id))
    }

    // setPlayed

    @Test
    fun `setPlayed passes through both ways`() = runTest {
        val ep = seeded(positionMs = 600_000L)

        repository.setPlayed(ep.id, true)
        assertEquals(listOf("setPlayed(${ep.id}, true)"), store.playbackCalls)
        assertTrue(stored(ep.id).played)
        assertEquals(0L, stored(ep.id).positionMs)

        repository.setPlayed(ep.id, false)
        assertEquals(listOf("setPlayed(${ep.id}, true)", "setPlayed(${ep.id}, false)"), store.playbackCalls)
        assertFalse(stored(ep.id).played)
        assertEquals(0L, stored(ep.id).positionMs)
    }

    // reads

    @Test
    fun `getEpisode and episode read from the store`() = runTest {
        val ep = seeded(positionMs = 1_000L)

        assertEquals(ep, repository.getEpisode(ep.id))
        assertEquals(ep, repository.episode(ep.id).first())
        assertNull(repository.getEpisode("unknown"))
        assertNull(repository.episode("unknown").first())
    }

    @Test
    fun `episode flow follows saves`() = runTest {
        val ep = seeded()
        repository.savePosition(ep.id, 120_000L, hour)

        assertEquals(120_000L, repository.episode(ep.id).first()?.positionMs)
    }

    @Test
    fun `getPodcast reads from the store`() = runTest {
        seeded()

        assertEquals(podcast(url), repository.getPodcast(url))
        assertNull(repository.getPodcast("https://unknown.example/feed"))
    }
}
