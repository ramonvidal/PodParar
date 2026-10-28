package com.podparar.core.data

import com.podparar.core.model.EpisodeWithPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DefaultQueueRepository on a fake store: each change applies the Queue rule and writes the whole queue. */
class DefaultQueueRepositoryTest {
    private val store = FakePodcastStore()
    private val repository = DefaultQueueRepository(store)

    private val url = "https://example.com/feed.xml"
    private val other = "https://other.example/feed.xml"
    private val show = podcast(url, title = "The Show").copy(artworkUrl = "https://img.example/show.jpg")
    private val eps = (1..5).map { episode(url, "$it", pubDate = it * 1_000L) }
    private val ids = eps.map { it.id }

    private fun seed() = store.seed(show, eps)

    private suspend fun queued(): List<String> = store.queueIds()

    // --- queue flow ---

    @Test
    fun `queue emits the store's queue with each episode's show`() = runTest {
        seed()
        store.seedQueue(listOf(ids[2], ids[0]))

        val items = repository.queue.first()

        assertEquals(listOf(ids[2], ids[0]), items.map { it.episode.id })
        assertEquals(EpisodeWithPodcast(eps[2], "The Show", "https://img.example/show.jpg"), items[0])
    }

    @Test
    fun `queue is empty when nothing is queued`() = runTest {
        seed()
        assertEquals(emptyList<EpisodeWithPodcast>(), repository.queue.first())
    }

    @Test
    fun `queue follows changes`() = runTest {
        seed()
        repository.add(ids[0])
        repository.add(ids[1])
        assertEquals(listOf(ids[0], ids[1]), repository.queue.first().map { it.episode.id })

        repository.remove(ids[0])
        assertEquals(listOf(ids[1]), repository.queue.first().map { it.episode.id })
    }

    // --- add ---

    @Test
    fun `add appends to the end`() = runTest {
        seed()
        repository.add(ids[0])
        repository.add(ids[1])
        repository.add(ids[2])

        assertEquals(ids.take(3), queued())
        assertEquals(listOf(ids[0], ids[1], ids[2]), store.setQueueCalls.last())
    }

    @Test
    fun `add of an already queued id keeps one copy in place`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1]))

        repository.add(ids[0])

        assertEquals(listOf(ids[0], ids[1]), queued())
    }

    // --- playNext ---

    @Test
    fun `playNext puts a new id at the front`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1]))

        repository.playNext(ids[2])

        assertEquals(listOf(ids[2], ids[0], ids[1]), queued())
    }

    @Test
    fun `playNext moves an already queued id to the front without duplicating it`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1], ids[2]))

        repository.playNext(ids[2])

        assertEquals(listOf(ids[2], ids[0], ids[1]), queued())
    }

    @Test
    fun `playNext on an empty queue`() = runTest {
        seed()
        repository.playNext(ids[3])
        assertEquals(listOf(ids[3]), queued())
    }

    // --- remove ---

    @Test
    fun `remove takes the id out`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1], ids[2]))

        repository.remove(ids[1])

        assertEquals(listOf(ids[0], ids[2]), queued())
    }

    @Test
    fun `remove of an id not queued keeps the queue`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1]))

        repository.remove(ids[4])

        assertEquals(listOf(ids[0], ids[1]), queued())
    }

    // --- move ---

    @Test
    fun `move reorders the queue`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1], ids[2]))

        repository.move(0, 2)
        assertEquals(listOf(ids[1], ids[2], ids[0]), queued())

        repository.move(2, 0)
        assertEquals(listOf(ids[0], ids[1], ids[2]), queued())
    }

    @Test
    fun `move clamps the target and ignores an out of range source`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1], ids[2]))

        repository.move(0, 99)
        assertEquals(listOf(ids[1], ids[2], ids[0]), queued())

        repository.move(7, 0)
        assertEquals(listOf(ids[1], ids[2], ids[0]), queued())
    }

    // --- takeNext ---

    @Test
    fun `takeNext returns the first id and removes it`() = runTest {
        seed()
        store.seedQueue(listOf(ids[0], ids[1], ids[2]))

        assertEquals(ids[0], repository.takeNext())
        assertEquals(listOf(ids[1], ids[2]), queued())

        assertEquals(ids[1], repository.takeNext())
        assertEquals(ids[2], repository.takeNext())
        assertEquals(emptyList<String>(), queued())
    }

    @Test
    fun `takeNext on an empty queue returns null and leaves it empty`() = runTest {
        seed()
        assertNull(repository.takeNext())
        assertEquals(emptyList<String>(), queued())
    }

    // --- concurrency ---

    @Test
    fun `concurrent adds never lose an item`() = runTest {
        val many = (1..50).map { episode(url, "c$it") }
        store.seed(show, many)

        withContext(Dispatchers.Default) {
            many.map { ep -> async { repository.add(ep.id) } }.awaitAll()
        }

        val result = queued()
        assertEquals(50, result.size)
        assertEquals(many.map { it.id }.toSet(), result.toSet())
    }

    @Test
    fun `concurrent playNext and add never lose or duplicate an item`() = runTest {
        val many = (1..50).map { episode(url, "m$it") }
        store.seed(show, many)

        withContext(Dispatchers.Default) {
            many.mapIndexed { i, ep ->
                async { if (i % 2 == 0) repository.add(ep.id) else repository.playNext(ep.id) }
            }.awaitAll()
        }

        val result = queued()
        assertEquals(50, result.size)
        assertEquals(many.map { it.id }.toSet(), result.toSet())
    }

    @Test
    fun `concurrent takeNext hands out each id once`() = runTest {
        val many = (1..30).map { episode(url, "t$it") }
        store.seed(show, many)
        store.seedQueue(many.map { it.id })

        val taken = withContext(Dispatchers.Default) {
            (1..40).map { async { repository.takeNext() } }.awaitAll()
        }

        val got = taken.filterNotNull()
        assertEquals(30, got.size)
        assertEquals(many.map { it.id }.toSet(), got.toSet())
        assertTrue(queued().isEmpty())
    }

    @Test
    fun `the queue can hold episodes from several shows`() = runTest {
        seed()
        val elsewhere = episode(other, "x")
        store.seed(podcast(other, title = "Other"), listOf(elsewhere))

        repository.add(ids[0])
        repository.add(elsewhere.id)

        val items = repository.queue.first()
        assertEquals(listOf("The Show", "Other"), items.map { it.podcastTitle })
    }
}
