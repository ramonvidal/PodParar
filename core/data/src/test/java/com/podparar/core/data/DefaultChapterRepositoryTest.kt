package com.podparar.core.data

import com.podparar.core.model.Chapter
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** DefaultChapterRepository on a fake store and a fake source. */
class DefaultChapterRepositoryTest {
    private val store = FakePodcastStore()
    private val source = FakeChapterSource()
    private val repository = DefaultChapterRepository(store, source)

    private val feed = "https://example.com/feed.xml"
    private val chaptersUrl = "https://example.com/chapters.json"
    private val chapters = listOf(Chapter(0, "Intro"), Chapter(60_000, "Main", imageUrl = "https://img.example/m.jpg"))

    private fun seedEpisode(guid: String = "1", url: String? = chaptersUrl) {
        store.seed(podcast(feed), listOf(episode(feed, guid).copy(chaptersUrl = url)))
    }

    private val id = "$feed#1"

    @Test
    fun `fetches and stores when the episode has a url and nothing is stored`() = runTest {
        seedEpisode()
        source.chapters[chaptersUrl] = chapters

        val result = repository.ensureChapters(id)

        assertTrue(result)
        assertEquals(listOf(chaptersUrl), source.fetched)
        assertEquals(chapters, store.chapters(id))
    }

    @Test
    fun `no fetch when chapters are already stored`() = runTest {
        seedEpisode()
        store.seedChapters(id, chapters)

        val result = repository.ensureChapters(id)

        assertTrue(result)
        assertEquals(emptyList<String>(), source.fetched)
        assertEquals(chapters, store.chapters(id))
    }

    @Test
    fun `no fetch and false when the episode has no chapters url`() = runTest {
        seedEpisode(url = null)

        assertFalse(repository.ensureChapters(id))
        assertEquals(emptyList<String>(), source.fetched)
        assertEquals(emptyList<Chapter>(), store.chapters(id))
    }

    @Test
    fun `no fetch and false for an unknown episode`() = runTest {
        assertFalse(repository.ensureChapters("$feed#nope"))
        assertEquals(emptyList<String>(), source.fetched)
    }

    @Test
    fun `stored chapters count as true even without a url`() = runTest {
        seedEpisode(url = null)
        store.seedChapters(id, chapters)

        assertTrue(repository.ensureChapters(id))
        assertEquals(emptyList<String>(), source.fetched)
    }

    @Test
    fun `a failing fetch is swallowed and returns false`() = runTest {
        seedEpisode()
        source.failures[chaptersUrl] = PodcastException(LoadError.OFFLINE)

        assertFalse(repository.ensureChapters(id))
        assertEquals(listOf(chaptersUrl), source.fetched)
        assertEquals(emptyList<Chapter>(), store.chapters(id))
    }

    @Test
    fun `unexpected exceptions are swallowed too`() = runTest {
        seedEpisode()
        source.failures[chaptersUrl] = IllegalStateException("boom")

        assertFalse(repository.ensureChapters(id))
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        seedEpisode()
        source.failures[chaptersUrl] = CancellationException("cancelled")

        try {
            repository.ensureChapters(id)
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun `an empty chapters file stores nothing and returns false`() = runTest {
        seedEpisode()
        source.chapters[chaptersUrl] = emptyList()

        assertFalse(repository.ensureChapters(id))
        assertEquals(emptyList<Chapter>(), store.chapters(id))
    }

    @Test
    fun `a second call after a success does not fetch again`() = runTest {
        seedEpisode()
        source.chapters[chaptersUrl] = chapters

        assertTrue(repository.ensureChapters(id))
        assertTrue(repository.ensureChapters(id))

        assertEquals(1, source.fetched.size)
    }

    @Test
    fun `a failure can be retried later`() = runTest {
        seedEpisode()
        source.failures[chaptersUrl] = PodcastException(LoadError.OFFLINE)
        assertFalse(repository.ensureChapters(id))

        source.failures.clear()
        source.chapters[chaptersUrl] = chapters

        assertTrue(repository.ensureChapters(id))
        assertEquals(chapters, store.chapters(id))
    }

    @Test
    fun `chapters flow is empty then follows the store`() = runTest {
        seedEpisode()
        assertEquals(emptyList<Chapter>(), repository.chapters(id).first())

        store.seedChapters(id, chapters)

        assertEquals(chapters, repository.chapters(id).first())
    }

    @Test
    fun `chapters flow shows what ensureChapters stored`() = runTest {
        seedEpisode()
        source.chapters[chaptersUrl] = chapters

        repository.ensureChapters(id)

        assertEquals(chapters, repository.chapters(id).first())
    }
}
