package com.podparar.core.data

import com.podparar.core.model.ImportResult
import com.podparar.core.model.LoadError
import com.podparar.core.model.Opml
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Phase 5: per-show speed, OPML export and import. */
class DefaultPodcastRepositoryPhase5Test {
    private val feeds = FakeFeedSource()
    private val directory = FakePodcastDirectory()
    private val store = FakePodcastStore()
    private val repository = DefaultPodcastRepository(feeds, directory, store)
    private val delta = 0.0001f

    private fun opml(vararg urls: String) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>t</title></head><body>""")
        urls.forEachIndexed { i, url -> append("""<outline type="rss" text="Show $i" xmlUrl="$url"/>""") }
        append("</body></opml>")
    }

    // setSpeed

    @Test
    fun `setSpeed stores an in-range speed as it is`() = runTest {
        val url = "https://a.example.com/rss"
        store.seed(podcast(url))

        repository.setSpeed(url, 1.5f)

        assertEquals(listOf("setSpeed($url, 1.5)"), store.speedCalls)
        assertEquals(1.5f, repository.podcast(url).first()!!.speed!!, delta)
    }

    @Test
    fun `setSpeed clamps out-of-range speeds`() = runTest {
        val url = "https://a.example.com/rss"
        store.seed(podcast(url))

        repository.setSpeed(url, 5f)
        assertEquals(3.0f, store.podcast(url)!!.speed!!, delta)

        repository.setSpeed(url, 0.25f)
        assertEquals(0.8f, store.podcast(url)!!.speed!!, delta)

        assertEquals(listOf("setSpeed($url, 3.0)", "setSpeed($url, 0.8)"), store.speedCalls)
    }

    @Test
    fun `setSpeed with null clears the show's speed`() = runTest {
        val url = "https://a.example.com/rss"
        store.seed(podcast(url).copy(speed = 2.0f))

        repository.setSpeed(url, null)

        assertEquals(listOf("setSpeed($url, null)"), store.speedCalls)
        assertNull(store.podcast(url)!!.speed)
    }

    @Test
    fun `a refresh keeps the show's speed`() = runTest {
        val url = "https://a.example.com/rss"
        store.seed(podcast(url))
        repository.setSpeed(url, 1.8f)

        repository.refresh(url)
        repository.refreshAll()

        assertEquals(1.8f, repository.podcast(url).first()!!.speed!!, delta)
    }

    // exportOpml

    @Test
    fun `exportOpml writes the current subscriptions`() = runTest {
        val shows = listOf(
            podcast("https://b.example.com/rss", "Beta & Co"),
            podcast("https://a.example.com/rss", "alpha"),
            podcast("https://c.example.com/rss", "Gamma"),
        )
        shows.forEach { store.seed(it) }

        val xml = repository.exportOpml()

        assertEquals(Opml.write(shows), xml)
        assertEquals(
            listOf("https://a.example.com/rss", "https://b.example.com/rss", "https://c.example.com/rss"),
            Opml.parse(xml).map { it.feedUrl },
        )
    }

    @Test
    fun `exportOpml with no subscriptions is an empty OPML`() = runTest {
        val xml = repository.exportOpml()
        assertEquals(Opml.write(emptyList()), xml)
        assertEquals(emptyList<Any>(), Opml.parse(xml))
    }

    @Test
    fun `exportOpml leaves out unsubscribed shows`() = runTest {
        store.seed(podcast("https://a.example.com/rss", "A"))
        store.seed(podcast("https://b.example.com/rss", "B"))
        repository.unsubscribe("https://a.example.com/rss")

        assertEquals(listOf("https://b.example.com/rss"), Opml.parse(repository.exportOpml()).map { it.feedUrl })
    }

    // importOpml

    @Test
    fun `importOpml subscribes to every new feed in order`() = runTest {
        val urls = arrayOf("https://b.example.com/rss", "https://a.example.com/rss", "https://c.example.com/rss")

        val result = repository.importOpml(opml(*urls))

        assertEquals(ImportResult(added = 3, alreadySubscribed = 0, failed = 0), result)
        assertEquals(urls.toList(), feeds.fetched)
        assertEquals(urls.toSet(), store.podcastUrls().toSet())
        assertEquals(urls.toSet(), repository.podcasts.first().map { it.feedUrl }.toSet())
    }

    @Test
    fun `importOpml skips shows already subscribed without fetching them`() = runTest {
        store.seed(podcast("https://a.example.com/rss"))

        val result = repository.importOpml(opml("https://a.example.com/rss", "https://b.example.com/rss"))

        assertEquals(ImportResult(added = 1, alreadySubscribed = 1, failed = 0), result)
        assertEquals(listOf("https://b.example.com/rss"), feeds.fetched)
        assertTrue(store.saved.none { it.podcast.feedUrl == "https://a.example.com/rss" })
    }

    @Test
    fun `importOpml compares normalized urls`() = runTest {
        store.seed(podcast("https://a.example.com/rss"))
        store.seed(podcast("http://plain.example.com/feed"))

        val result = repository.importOpml(
            opml(
                "feed://a.example.com/rss",
                "a.example.com/rss",
                "itpc://a.example.com/rss",
                "http://plain.example.com/feed",
                "https://new.example.com/rss",
            ),
        )

        assertEquals(ImportResult(added = 1, alreadySubscribed = 4, failed = 0), result)
        assertEquals(listOf("https://new.example.com/rss"), feeds.fetched)
    }

    @Test
    fun `importOpml subscribes with the normalized url`() = runTest {
        val result = repository.importOpml(opml("feed://n.example.com/rss"))

        assertEquals(ImportResult(added = 1, alreadySubscribed = 0, failed = 0), result)
        assertEquals(listOf("https://n.example.com/rss"), feeds.fetched)
        assertEquals(listOf("https://n.example.com/rss"), store.podcastUrls())
    }

    @Test
    fun `a later variant of a url imported earlier in the same file counts as already subscribed`() = runTest {
        val result = repository.importOpml(opml("https://n.example.com/rss", "feed://n.example.com/rss"))

        assertEquals(ImportResult(added = 1, alreadySubscribed = 1, failed = 0), result)
        assertEquals(listOf("https://n.example.com/rss"), feeds.fetched)
    }

    @Test
    fun `importOpml counts failures and carries on`() = runTest {
        feeds.failures["https://bad.example.com/rss"] = LoadError.NOT_A_FEED
        feeds.failures["https://down.example.com/rss"] = LoadError.OFFLINE

        val result = repository.importOpml(
            opml(
                "https://bad.example.com/rss",
                "https://good.example.com/rss",
                "https://down.example.com/rss",
                "https://fine.example.com/rss",
            ),
        )

        assertEquals(ImportResult(added = 2, alreadySubscribed = 0, failed = 2), result)
        assertEquals(setOf("https://good.example.com/rss", "https://fine.example.com/rss"), store.podcastUrls().toSet())
    }

    @Test
    fun `importOpml counts an entry that is not a link as failed`() = runTest {
        val result = repository.importOpml(opml("not a link", "https://ok.example.com/rss"))

        assertEquals(ImportResult(added = 1, alreadySubscribed = 0, failed = 1), result)
        assertEquals(listOf("https://ok.example.com/rss"), store.podcastUrls())
    }

    @Test
    fun `importOpml reports progress after each entry with the total of every entry`() = runTest {
        store.seed(podcast("https://a.example.com/rss"))
        feeds.failures["https://bad.example.com/rss"] = LoadError.NOT_A_FEED
        val progress = mutableListOf<Pair<Int, Int>>()

        repository.importOpml(
            opml("https://a.example.com/rss", "https://bad.example.com/rss", "https://new.example.com/rss"),
        ) { done, total -> progress += done to total }

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
    }

    @Test
    fun `importOpml progress comes after each subscribe finishes`() = runTest {
        val progress = mutableListOf<Pair<Int, List<String>>>()

        repository.importOpml(opml("https://a.example.com/rss", "https://b.example.com/rss")) { done, _ ->
            progress += done to store.saved.map { it.podcast.feedUrl }
        }

        assertEquals(
            listOf(
                1 to listOf("https://a.example.com/rss"),
                2 to listOf("https://a.example.com/rss", "https://b.example.com/rss"),
            ),
            progress,
        )
    }

    @Test
    fun `importOpml subscribes one show at a time`() = runTest {
        feeds.delayMs = 100
        val result = repository.importOpml(
            opml("https://a.example.com/rss", "https://b.example.com/rss", "https://c.example.com/rss"),
        )

        assertEquals(3, result.added)
        assertEquals(1, feeds.maxInFlight)
    }

    @Test
    fun `importOpml of an OPML with no feeds does nothing`() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = repository.importOpml(opml()) { done, total -> progress += done to total }

        assertEquals(ImportResult(0, 0, 0), result)
        assertTrue(feeds.fetched.isEmpty())
        assertTrue(progress.isEmpty())
    }

    @Test
    fun `importOpml of something that is not OPML throws NOT_A_FEED and subscribes to nothing`() = runTest {
        val inputs = listOf(
            "",
            "hello",
            """<?xml version="1.0"?><rss version="2.0"><channel><title>x</title></channel></rss>""",
            "<opml><body><outline xmlUrl=\"https://a.example.com/rss\"></body>",
        )
        for (input in inputs) {
            try {
                repository.importOpml(input)
                fail("Expected PodcastException(NOT_A_FEED) for: $input")
            } catch (e: PodcastException) {
                assertEquals(LoadError.NOT_A_FEED, e.error)
            }
        }
        assertTrue(feeds.fetched.isEmpty())
        assertTrue(store.saved.isEmpty())
    }
}
