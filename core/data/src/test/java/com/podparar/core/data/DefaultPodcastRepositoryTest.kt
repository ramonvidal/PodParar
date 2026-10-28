package com.podparar.core.data

import com.podparar.core.model.Episode
import com.podparar.core.model.LoadError
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultPodcastRepositoryTest {
    private val feeds = FakeFeedSource()
    private val directory = FakePodcastDirectory()
    private val store = FakePodcastStore()
    private val repository = DefaultPodcastRepository(feeds, directory, store)

    private suspend fun expectError(expected: LoadError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected PodcastException($expected)")
        } catch (e: PodcastException) {
            assertEquals(expected, e.error)
        }
    }

    // subscribe

    @Test
    fun `subscribe fetches the link, saves the feed and returns the stored show`() = runTest {
        val url = "https://example.com/feed.xml"
        val result = repository.subscribe(url)

        assertEquals(listOf(url), feeds.fetched)
        assertEquals(listOf(url), store.saved.map { it.podcast.feedUrl })
        assertEquals(podcast(url), result)
        assertEquals(podcast(url), store.observePodcast(url).first())
        assertEquals(listOf(episode(url, "1")), store.episodes(url))
    }

    @Test
    fun `subscribe normalizes a feed scheme link to https before fetching`() = runTest {
        val result = repository.subscribe("feed://example.com/rss")

        assertEquals(listOf("https://example.com/rss"), feeds.fetched)
        assertEquals("https://example.com/rss", result.feedUrl)
        assertEquals(listOf("https://example.com/rss"), store.podcastUrls())
    }

    @Test
    fun `subscribe trims and adds https to a bare link`() = runTest {
        repository.subscribe("  example.com/podcast.rss  ")
        assertEquals(listOf("https://example.com/podcast.rss"), feeds.fetched)
    }

    @Test
    fun `subscribe keeps http links as they are`() = runTest {
        repository.subscribe("http://example.com/rss")
        assertEquals(listOf("http://example.com/rss"), feeds.fetched)
    }

    @Test
    fun `subscribe rejects garbage without fetching`() = runTest {
        for (garbage in listOf("", "   ", "not a link", "hello", "ftp://example.com/feed", "javascript:alert(1)")) {
            expectError(LoadError.INVALID_LINK) { repository.subscribe(garbage) }
        }
        assertTrue(feeds.fetched.isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `subscribe passes fetch errors on and saves nothing`() = runTest {
        val url = "https://example.com/feed.xml"
        feeds.failures[url] = LoadError.NOT_A_FEED
        expectError(LoadError.NOT_A_FEED) { repository.subscribe(url) }
        feeds.failures[url] = LoadError.OFFLINE
        expectError(LoadError.OFFLINE) { repository.subscribe(url) }
        assertTrue(store.saved.isEmpty())
        assertTrue(store.podcastUrls().isEmpty())
    }

    @Test
    fun `subscribe saves the whole parsed feed`() = runTest {
        val url = "https://example.com/feed.xml"
        val parsed = ParsedFeed(podcast(url, "My Show"), listOf(episode(url, "a", 2_000L), episode(url, "b", 1_000L)))
        feeds.feeds[url] = parsed

        val result = repository.subscribe(url)

        assertEquals(listOf(parsed), store.saved)
        assertEquals("My Show", result.title)
    }

    // refresh

    @Test
    fun `refresh fetches and saves that show`() = runTest {
        val url = "https://example.com/feed.xml"
        store.seed(podcast(url))
        val parsed = ParsedFeed(podcast(url, "Renamed"), listOf(episode(url, "new")))
        feeds.feeds[url] = parsed

        repository.refresh(url)

        assertEquals(listOf(url), feeds.fetched)
        assertEquals(listOf(parsed), store.saved)
        assertEquals("Renamed", store.observePodcast(url).first()?.title)
    }

    @Test
    fun `refresh errors propagate and nothing is saved`() = runTest {
        val url = "https://example.com/feed.xml"
        store.seed(podcast(url))
        feeds.failures[url] = LoadError.NOT_FOUND

        expectError(LoadError.NOT_FOUND) { repository.refresh(url) }
        assertTrue(store.saved.isEmpty())
        assertEquals(podcast(url), store.observePodcast(url).first())
    }

    // refreshAll

    @Test
    fun `refreshAll with no subscriptions does nothing and returns 0`() = runTest {
        assertEquals(0, repository.refreshAll().failedFeeds)
        assertTrue(feeds.fetched.isEmpty())
    }

    @Test
    fun `refreshAll refreshes every subscription`() = runTest {
        val urls = listOf("https://a.example.com/rss", "https://b.example.com/rss", "https://c.example.com/rss")
        urls.forEach { store.seed(podcast(it)) }

        assertEquals(0, repository.refreshAll().failedFeeds)

        assertEquals(urls.toSet(), feeds.fetched.toSet())
        assertEquals(3, feeds.fetched.size)
        assertEquals(urls.toSet(), store.saved.map { it.podcast.feedUrl }.toSet())
    }

    @Test
    fun `refreshAll counts failures and still saves the others`() = runTest {
        val ok1 = "https://a.example.com/rss"
        val bad1 = "https://b.example.com/rss"
        val ok2 = "https://c.example.com/rss"
        val bad2 = "https://d.example.com/rss"
        listOf(ok1, bad1, ok2, bad2).forEach { store.seed(podcast(it)) }
        feeds.failures[bad1] = LoadError.OFFLINE
        feeds.failures[bad2] = LoadError.NOT_A_FEED

        assertEquals(2, repository.refreshAll().failedFeeds)

        assertEquals(setOf(ok1, bad1, ok2, bad2), feeds.fetched.toSet())
        assertEquals(setOf(ok1, ok2), store.saved.map { it.podcast.feedUrl }.toSet())
    }

    @Test
    fun `refreshAll when every show fails returns the number of shows`() = runTest {
        val urls = listOf("https://a.example.com/rss", "https://b.example.com/rss")
        urls.forEach {
            store.seed(podcast(it))
            feeds.failures[it] = LoadError.UNKNOWN
        }
        assertEquals(2, repository.refreshAll().failedFeeds)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `refreshAll runs at most four fetches at once`() = runTest {
        val urls = (1..10).map { "https://show$it.example.com/rss" }
        urls.forEach { store.seed(podcast(it)) }
        feeds.delayMs = 100L

        assertEquals(0, repository.refreshAll().failedFeeds)

        assertEquals(10, feeds.fetched.size)
        assertEquals(urls.toSet(), store.saved.map { it.podcast.feedUrl }.toSet())
        assertTrue("max in flight was ${feeds.maxInFlight}", feeds.maxInFlight in 1..4)
    }

    // Phase 4: what refresh and refreshAll report as new

    @Test
    fun `refresh returns only the episodes that were not stored before`() = runTest {
        val url = "https://example.com/feed.xml"
        store.seed(podcast(url), listOf(episode(url, "old", 1_000L)))
        feeds.feeds[url] = ParsedFeed(podcast(url), listOf(episode(url, "new", 2_000L), episode(url, "old", 1_000L)))

        val fresh = repository.refresh(url)

        assertEquals(listOf("$url#new"), fresh.map { it.id })
        assertEquals(setOf("$url#new", "$url#old"), store.episodes(url).map { it.id }.toSet())
    }

    @Test
    fun `refresh with nothing new returns an empty list`() = runTest {
        val url = "https://example.com/feed.xml"
        store.seed(podcast(url), listOf(episode(url, "1")))

        assertEquals(emptyList<Episode>(), repository.refresh(url))
    }

    @Test
    fun `refreshAll with no subscriptions returns an empty result`() = runTest {
        val result = repository.refreshAll()
        assertEquals(0, result.failedFeeds)
        assertEquals(emptyList<Episode>(), result.newEpisodes)
    }

    @Test
    fun `refreshAll gathers the new episodes of every feed that succeeded`() = runTest {
        val a = "https://a.example.com/rss"
        val b = "https://b.example.com/rss"
        val bad = "https://bad.example.com/rss"
        store.seed(podcast(a), listOf(episode(a, "old")))
        store.seed(podcast(b), listOf(episode(b, "kept")))
        store.seed(podcast(bad))
        feeds.feeds[a] = ParsedFeed(podcast(a), listOf(episode(a, "new1", 3_000L), episode(a, "new2", 2_000L), episode(a, "old")))
        feeds.feeds[b] = ParsedFeed(podcast(b), listOf(episode(b, "kept")))
        feeds.feeds[bad] = ParsedFeed(podcast(bad), listOf(episode(bad, "never")))
        feeds.failures[bad] = LoadError.OFFLINE

        val result = repository.refreshAll()

        assertEquals(1, result.failedFeeds)
        assertEquals(setOf("$a#new1", "$a#new2"), result.newEpisodes.map { it.id }.toSet())
        assertEquals(2, result.newEpisodes.size)
    }

    @Test
    fun `a second refreshAll finds nothing new`() = runTest {
        val a = "https://a.example.com/rss"
        store.seed(podcast(a))

        assertEquals(listOf("$a#1"), repository.refreshAll().newEpisodes.map { it.id })
        assertEquals(emptyList<Episode>(), repository.refreshAll().newEpisodes)
    }

    @Test
    fun `refreshAll when every show fails has no new episodes`() = runTest {
        val urls = listOf("https://a.example.com/rss", "https://b.example.com/rss")
        urls.forEach {
            store.seed(podcast(it))
            feeds.failures[it] = LoadError.UNKNOWN
        }
        val result = repository.refreshAll()
        assertEquals(2, result.failedFeeds)
        assertEquals(emptyList<Episode>(), result.newEpisodes)
    }

    // Phase 4: auto-download

    @Test
    fun `setAutoDownload is written to the store`() = runTest {
        val url = "https://a.example.com/rss"
        store.seed(podcast(url))

        repository.setAutoDownload(url, true)
        assertEquals(listOf("setAutoDownload($url, true)"), store.autoDownloadCalls)
        assertTrue(repository.podcast(url).first()!!.autoDownload)

        repository.setAutoDownload(url, false)
        assertEquals(listOf("setAutoDownload($url, true)", "setAutoDownload($url, false)"), store.autoDownloadCalls)
        assertFalse(repository.podcast(url).first()!!.autoDownload)
    }

    @Test
    fun `a refresh keeps the show's auto-download choice`() = runTest {
        val url = "https://a.example.com/rss"
        store.seed(podcast(url))
        repository.setAutoDownload(url, true)

        repository.refresh(url)
        repository.refreshAll()

        assertTrue(repository.podcast(url).first()!!.autoDownload)
    }

    // search

    @Test
    fun `search trims the term and returns the directory results`() = runTest {
        val hits = listOf(SearchResult("Serial", "This American Life", "https://serial.example.com/rss", null))
        directory.results = hits

        assertEquals(hits, repository.search("  serial \n"))
        assertEquals(listOf("serial"), directory.terms)
    }

    @Test
    fun `blank search returns empty without asking the directory`() = runTest {
        directory.results = listOf(SearchResult("X", "Y", "https://x.example.com/rss", null))
        assertEquals(emptyList<SearchResult>(), repository.search(""))
        assertEquals(emptyList<SearchResult>(), repository.search("   \t\n"))
        assertTrue(directory.terms.isEmpty())
    }

    @Test
    fun `search errors propagate`() = runTest {
        directory.failure = LoadError.OFFLINE
        expectError(LoadError.OFFLINE) { repository.search("news") }
    }

    // unsubscribe and observing

    @Test
    fun `unsubscribe deletes the show from the store`() = runTest {
        val keep = "https://a.example.com/rss"
        val drop = "https://b.example.com/rss"
        store.seed(podcast(keep))
        store.seed(podcast(drop), listOf(episode(drop, "1")))

        repository.unsubscribe(drop)

        assertEquals(listOf(drop), store.deleted)
        assertEquals(listOf(keep), repository.podcasts.first().map { it.feedUrl })
        assertNull(repository.podcast(drop).first())
        assertEquals(emptyList<Any>(), repository.episodes(drop).first())
    }

    @Test
    fun `subscribedFeedUrls reflects the store`() = runTest {
        assertEquals(emptySet<String>(), repository.subscribedFeedUrls.first())

        store.seed(podcast("https://a.example.com/rss"))
        store.seed(podcast("https://b.example.com/rss"))
        assertEquals(
            setOf("https://a.example.com/rss", "https://b.example.com/rss"),
            repository.subscribedFeedUrls.first(),
        )

        repository.subscribe("feed://c.example.com/rss")
        assertEquals(
            setOf("https://a.example.com/rss", "https://b.example.com/rss", "https://c.example.com/rss"),
            repository.subscribedFeedUrls.first(),
        )

        repository.unsubscribe("https://a.example.com/rss")
        assertEquals(
            setOf("https://b.example.com/rss", "https://c.example.com/rss"),
            repository.subscribedFeedUrls.first(),
        )
    }

    @Test
    fun `podcasts, podcast and episodes come from the store`() = runTest {
        val a = "https://a.example.com/rss"
        val b = "https://b.example.com/rss"
        store.seed(podcast(b, "beta"))
        store.seed(podcast(a, "Alpha"), listOf(episode(a, "old", 1_000L), episode(a, "new", 2_000L)))

        assertEquals(listOf("Alpha", "beta"), repository.podcasts.first().map { it.title })
        assertEquals(podcast(a, "Alpha"), repository.podcast(a).first())
        assertNull(repository.podcast("https://missing.example.com/rss").first())
        assertEquals(listOf("$a#new", "$a#old"), repository.episodes(a).first().map { it.id })
    }
}
