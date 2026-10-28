package com.podparar.core.data

import com.podparar.core.model.EpisodeWithPodcast
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DefaultEpisodeRepository's Phase 3 members: Continue listening, New episodes, Mark all played. */
class DefaultEpisodeRepositoryHomeTest {
    private val now = 1_727_863_200_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val store = FakePodcastStore()
    private val repository = DefaultEpisodeRepository(store, clock)

    private val a = "https://a.example/feed"
    private val b = "https://b.example/feed"

    @Test
    fun `continueListening reads in-progress episodes from the store with a limit of 10`() = runTest {
        val started = episode(a, "1").copy(positionMs = 5_000L, lastPlayedAt = 10L)
        val later = episode(a, "2").copy(positionMs = 9_000L, lastPlayedAt = 20L)
        val fresh = episode(a, "3")
        val done = episode(a, "4").copy(played = true)
        store.seed(podcast(a, title = "A"), listOf(started, later, fresh, done))

        val items = repository.continueListening.first()

        assertEquals(listOf(10), store.inProgressLimits)
        assertEquals(listOf(later.id, started.id), items.map { it.episode.id })
        assertEquals(EpisodeWithPodcast(later, "A", null), items.first())
    }

    @Test
    fun `continueListening shows at most 10`() = runTest {
        val eps = (1..15).map { episode(a, "$it").copy(positionMs = 1_000L, lastPlayedAt = it.toLong()) }
        store.seed(podcast(a), eps)

        assertEquals(10, repository.continueListening.first().size)
    }

    @Test
    fun `newEpisodes reads unstarted unplayed episodes from the store with a limit of 50`() = runTest {
        val old = episode(a, "1", pubDate = 1_000L)
        val recent = episode(b, "2", pubDate = 3_000L)
        val started = episode(a, "3", pubDate = 5_000L).copy(positionMs = 1L)
        val done = episode(b, "4", pubDate = 6_000L).copy(played = true)
        store.seed(podcast(a, title = "A"), listOf(old, started))
        store.seed(podcast(b, title = "B"), listOf(recent, done))

        val items = repository.newEpisodes.first()

        assertEquals(listOf(50), store.newEpisodesLimits)
        assertEquals(listOf(recent.id, old.id), items.map { it.episode.id })
        assertEquals(listOf("B", "A"), items.map { it.podcastTitle })
    }

    @Test
    fun `newEpisodes shows at most 50`() = runTest {
        store.seed(podcast(a), (1..60).map { episode(a, "$it", pubDate = it.toLong()) })

        assertEquals(50, repository.newEpisodes.first().size)
    }

    @Test
    fun `markAllPlayed passes through to the store for that show only`() = runTest {
        val a1 = episode(a, "1").copy(positionMs = 5_000L)
        val a2 = episode(a, "2")
        val b1 = episode(b, "1").copy(positionMs = 7_000L)
        store.seed(podcast(a), listOf(a1, a2))
        store.seed(podcast(b), listOf(b1))

        repository.markAllPlayed(a)

        assertEquals(listOf(a), store.markAllPlayedCalls)
        for (id in listOf(a1.id, a2.id)) {
            val ep = requireNotNull(store.episode(id))
            assertTrue(ep.played)
            assertEquals(0L, ep.positionMs)
        }
        val untouched = requireNotNull(store.episode(b1.id))
        assertFalse(untouched.played)
        assertEquals(7_000L, untouched.positionMs)
    }

    @Test
    fun `after markAllPlayed the show leaves both Home lists`() = runTest {
        store.seed(podcast(a), listOf(episode(a, "1").copy(positionMs = 5_000L), episode(a, "2")))

        repository.markAllPlayed(a)

        assertEquals(emptyList<EpisodeWithPodcast>(), repository.continueListening.first())
        assertEquals(emptyList<EpisodeWithPodcast>(), repository.newEpisodes.first())
    }
}
