package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android Auto browse tree's ids (Car phase contract). */
class CarBrowseTest {
    private val feed = "https://example.com/feed.xml"

    // ids

    @Test
    fun `the folder ids are the agreed values`() {
        assertEquals("root", CarBrowse.ROOT)
        assertEquals("continue", CarBrowse.CONTINUE)
        assertEquals("up_next", CarBrowse.UP_NEXT)
        assertEquals("new", CarBrowse.NEW)
        assertEquals("shows", CarBrowse.SHOWS)
        assertEquals("show:", CarBrowse.SHOW_PREFIX)
    }

    @Test
    fun `the folder ids are all different`() {
        val ids = listOf(CarBrowse.ROOT, CarBrowse.CONTINUE, CarBrowse.UP_NEXT, CarBrowse.NEW, CarBrowse.SHOWS)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `lists hold at most fifty items`() {
        assertEquals(50, CarBrowse.MAX_ITEMS)
    }

    // showId / feedUrlOf

    @Test
    fun `showId is the prefix and the feed url`() {
        assertEquals("show:$feed", CarBrowse.showId(feed))
        assertTrue(CarBrowse.showId(feed).startsWith(CarBrowse.SHOW_PREFIX))
    }

    @Test
    fun `feedUrlOf gives back the feed url of a show id`() {
        assertEquals(feed, CarBrowse.feedUrlOf(CarBrowse.showId(feed)))
        assertEquals(feed, CarBrowse.feedUrlOf("show:$feed"))
    }

    @Test
    fun `round trip keeps urls with colons`() {
        val urls = listOf(
            "https://example.com:8443/feed.xml",
            "http://user:pass@example.com/rss",
            "https://example.com/feed?a=b:c&d=e",
        )
        for (url in urls) {
            assertEquals(url, CarBrowse.feedUrlOf(CarBrowse.showId(url)))
        }
    }

    @Test
    fun `round trip keeps urls with hashes`() {
        val urls = listOf(
            "https://example.com/feed.xml#section",
            "https://example.com/a#b#c",
            "https://example.com:80/x?y=1#z:w",
        )
        for (url in urls) {
            assertEquals(url, CarBrowse.feedUrlOf(CarBrowse.showId(url)))
        }
    }

    @Test
    fun `different feeds give different show ids`() {
        assertNotEquals(CarBrowse.showId(feed), CarBrowse.showId("https://other.example.org/rss"))
    }

    @Test
    fun `the prefix alone is not a show id`() {
        assertNull(CarBrowse.feedUrlOf("show:"))
        assertNull(CarBrowse.feedUrlOf(CarBrowse.SHOW_PREFIX))
    }

    @Test
    fun `ids that are not show ids give null`() {
        assertNull(CarBrowse.feedUrlOf(""))
        assertNull(CarBrowse.feedUrlOf(CarBrowse.ROOT))
        assertNull(CarBrowse.feedUrlOf(CarBrowse.CONTINUE))
        assertNull(CarBrowse.feedUrlOf(CarBrowse.UP_NEXT))
        assertNull(CarBrowse.feedUrlOf(CarBrowse.NEW))
        assertNull(CarBrowse.feedUrlOf(CarBrowse.SHOWS))
        assertNull(CarBrowse.feedUrlOf(feed))
        assertNull(CarBrowse.feedUrlOf("show"))
        assertNull(CarBrowse.feedUrlOf("shows:$feed"))
    }

    @Test
    fun `an episode id is not a show id`() {
        val episodeId = Episode.idFor(feed, "abc-123", "https://cdn.example.com/ep1.mp3")
        assertNull(CarBrowse.feedUrlOf(episodeId))
    }

    @Test
    fun `the prefix must be at the start`() {
        assertNull(CarBrowse.feedUrlOf("x show:$feed"))
        assertNull(CarBrowse.feedUrlOf("$feed#show:1"))
    }

    // isFolder

    @Test
    fun `the fixed folders are folders`() {
        assertTrue(CarBrowse.isFolder(CarBrowse.ROOT))
        assertTrue(CarBrowse.isFolder(CarBrowse.CONTINUE))
        assertTrue(CarBrowse.isFolder(CarBrowse.UP_NEXT))
        assertTrue(CarBrowse.isFolder(CarBrowse.NEW))
        assertTrue(CarBrowse.isFolder(CarBrowse.SHOWS))
    }

    @Test
    fun `show ids are folders`() {
        assertTrue(CarBrowse.isFolder(CarBrowse.showId(feed)))
        assertTrue(CarBrowse.isFolder(CarBrowse.showId("https://example.com:8443/a#b")))
    }

    @Test
    fun `episode ids and other ids are not folders`() {
        assertFalse(CarBrowse.isFolder(Episode.idFor(feed, "abc-123", "https://cdn.example.com/ep1.mp3")))
        assertFalse(CarBrowse.isFolder("$feed#https://cdn.example.com/ep1.mp3"))
        assertFalse(CarBrowse.isFolder(""))
        assertFalse(CarBrowse.isFolder(feed))
        assertFalse(CarBrowse.isFolder("Root"))
        assertFalse(CarBrowse.isFolder("something_else"))
    }
}
