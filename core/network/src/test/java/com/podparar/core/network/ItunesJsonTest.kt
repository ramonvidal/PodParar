package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** ItunesJson against a response shaped like Apple's (src/test/resources/itunes/search.json). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ItunesJsonTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("itunes/$name")) { "missing fixture itunes/$name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private val results by lazy { ItunesJson.parse(fixture("search.json")) }

    private fun parseError(json: String): LoadError = try {
        ItunesJson.parse(json)
        fail("expected PodcastException(UNKNOWN)")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    @Test
    fun `results with a feed url, duplicates dropped, in response order`() {
        assertEquals(
            listOf("https://feeds.simplecast.com/54nAGcIl", "http://dailybread.example.org/rss", "https://cafe.example.fr/feed.xml"),
            results.map { it.feedUrl },
        )
    }

    @Test
    fun `fields come from collectionName, artistName, feedUrl and artworkUrl600`() {
        assertEquals(
            SearchResult(
                title = "The Daily",
                author = "The New York Times",
                feedUrl = "https://feeds.simplecast.com/54nAGcIl",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts/daily/600x600bb.jpg",
            ),
            results[0],
        )
    }

    @Test
    fun `duplicate feed url keeps the first result`() {
        assertEquals("The Daily", results.single { it.feedUrl == "https://feeds.simplecast.com/54nAGcIl" }.title)
        assertTrue(results.none { it.title == "The Daily (mirror listing)" })
    }

    @Test
    fun `missing artworkUrl600 falls back to artworkUrl100`() {
        val bread = results[1]
        assertEquals("Daily Bread", bread.title)
        assertEquals("Daily Bread Radio", bread.author)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Podcasts/bread/100x100bb.jpg", bread.artworkUrl)
    }

    @Test
    fun `blank artwork becomes null and non-ascii text survives`() {
        val cafe = results[2]
        assertEquals("Daily Café — Morning Edition", cafe.title)
        assertEquals("Café Société", cafe.author)
        assertEquals(null, cafe.artworkUrl)
    }

    @Test
    fun `results without a feed url or with a blank one are dropped`() {
        assertTrue(results.none { it.title == "The Daily Exclusive" })
        assertTrue(results.none { it.title == "Blank feed" })
    }

    @Test
    fun `no results`() {
        assertEquals(emptyList<SearchResult>(), ItunesJson.parse("""{"resultCount":0,"results":[]}"""))
    }

    @Test
    fun `malformed json is UNKNOWN`() {
        assertEquals(LoadError.UNKNOWN, parseError("""{"resultCount":1,"results":[{"collectionName":"Cut off"""))
        assertEquals(LoadError.UNKNOWN, parseError("<html><body>Service Unavailable</body></html>"))
        assertEquals(LoadError.UNKNOWN, parseError(""))
    }
}
