package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlsTest {

    @Test
    fun `https url is kept as is`() {
        assertEquals("https://example.com/feed.xml", FeedUrls.normalize("https://example.com/feed.xml"))
    }

    @Test
    fun `http url is kept and not upgraded`() {
        assertEquals("http://example.com/feed.xml", FeedUrls.normalize("http://example.com/feed.xml"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("https://example.com/feed.xml", FeedUrls.normalize("  https://example.com/feed.xml \n\t"))
    }

    @Test
    fun `blank input is null`() {
        assertNull(FeedUrls.normalize(""))
        assertNull(FeedUrls.normalize("   "))
        assertNull(FeedUrls.normalize("\n\t "))
    }

    @Test
    fun `feed itpc and pcast schemes become https`() {
        assertEquals("https://example.com/rss", FeedUrls.normalize("feed://example.com/rss"))
        assertEquals("https://example.com/rss", FeedUrls.normalize("itpc://example.com/rss"))
        assertEquals("https://example.com/rss", FeedUrls.normalize("pcast://example.com/rss"))
    }

    @Test
    fun `podcast schemes are case insensitive`() {
        assertEquals("https://example.com/rss", FeedUrls.normalize("FEED://example.com/rss"))
        assertEquals("https://example.com/rss", FeedUrls.normalize("Itpc://example.com/rss"))
        assertEquals("https://example.com/rss", FeedUrls.normalize("PCAST://example.com/rss"))
    }

    @Test
    fun `feed prefix before a full url gives the inner url`() {
        assertEquals("https://example.com/rss", FeedUrls.normalize("feed:https://example.com/rss"))
        assertEquals("http://example.com/rss", FeedUrls.normalize("feed:http://example.com/rss"))
    }

    @Test
    fun `no scheme gets https`() {
        assertEquals("https://example.com/feed.xml", FeedUrls.normalize("example.com/feed.xml"))
        assertEquals("https://feeds.example.co.uk", FeedUrls.normalize("feeds.example.co.uk"))
    }

    @Test
    fun `scheme case is lowered and the rest kept`() {
        assertEquals("https://example.com/Feed.XML", FeedUrls.normalize("HTTPS://example.com/Feed.XML"))
        assertEquals("http://example.com/Feed", FeedUrls.normalize("HTTP://example.com/Feed"))
        assertEquals("https://example.com/Feed", FeedUrls.normalize("Https://example.com/Feed"))
    }

    @Test
    fun `query and fragment are kept`() {
        assertEquals(
            "https://example.com/rss?format=mp3&id=7#top",
            FeedUrls.normalize("https://example.com/rss?format=mp3&id=7#top"),
        )
    }

    @Test
    fun `port is allowed and kept`() {
        assertEquals("https://example.com:8443/rss", FeedUrls.normalize("https://example.com:8443/rss"))
        assertEquals("http://192.168.1.10:8080/feed", FeedUrls.normalize("http://192.168.1.10:8080/feed"))
    }

    @Test
    fun `other schemes are rejected`() {
        assertNull(FeedUrls.normalize("ftp://example.com/feed.xml"))
        assertNull(FeedUrls.normalize("mailto:someone@example.com"))
        assertNull(FeedUrls.normalize("javascript:alert(1)"))
        assertNull(FeedUrls.normalize("file:///sdcard/feed.xml"))
    }

    @Test
    fun `empty host is rejected`() {
        assertNull(FeedUrls.normalize("https://"))
        assertNull(FeedUrls.normalize("https:///feed.xml"))
        assertNull(FeedUrls.normalize("feed://"))
    }

    @Test
    fun `host without a dot is rejected`() {
        assertNull(FeedUrls.normalize("https://localhost/feed.xml"))
        assertNull(FeedUrls.normalize("http://intranet/rss"))
        assertNull(FeedUrls.normalize("notalink"))
        assertNull(FeedUrls.normalize("hello"))
    }

    @Test
    fun `host ends at the port so a dot after it does not count`() {
        assertNull(FeedUrls.normalize("https://localhost:8080/feed.xml"))
    }

    @Test
    fun `host ends at query or fragment so a dot after it does not count`() {
        assertNull(FeedUrls.normalize("https://foo?u=a.b"))
        assertNull(FeedUrls.normalize("https://foo#a.b"))
        assertNull(FeedUrls.normalize("https://foo/a.b"))
    }

    @Test
    fun `host ending at query or fragment is fine when it has a dot`() {
        assertEquals("https://example.com?id=1", FeedUrls.normalize("https://example.com?id=1"))
        assertEquals("https://example.com#x", FeedUrls.normalize("https://example.com#x"))
    }

    @Test
    fun `whitespace inside the url is rejected`() {
        assertNull(FeedUrls.normalize("https://example.com/my feed.xml"))
        assertNull(FeedUrls.normalize("https://exa mple.com/feed.xml"))
        assertNull(FeedUrls.normalize("example.com/a\tb"))
        assertNull(FeedUrls.normalize("two words"))
    }

    @Test
    fun `podcast scheme still needs a valid host`() {
        assertNull(FeedUrls.normalize("feed://localhost/rss"))
        assertNull(FeedUrls.normalize("itpc://"))
    }
}
