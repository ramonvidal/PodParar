package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeIdTest {
    private val feed = "https://example.com/feed.xml"
    private val audio = "https://cdn.example.com/ep1.mp3"

    @Test
    fun `guid is joined to the feed url with a hash`() {
        assertEquals("$feed#abc-123", Episode.idFor(feed, "abc-123", audio))
    }

    @Test
    fun `guid is trimmed`() {
        assertEquals("$feed#abc-123", Episode.idFor(feed, "  abc-123\n\t", audio))
    }

    @Test
    fun `null guid falls back to the audio url`() {
        assertEquals("$feed#$audio", Episode.idFor(feed, null, audio))
    }

    @Test
    fun `empty or blank guid falls back to the audio url`() {
        assertEquals("$feed#$audio", Episode.idFor(feed, "", audio))
        assertEquals("$feed#$audio", Episode.idFor(feed, "   \n", audio))
    }

    @Test
    fun `a guid that is itself a url is used as is`() {
        val guid = "https://example.com/?p=42"
        assertEquals("$feed#$guid", Episode.idFor(feed, guid, audio))
    }

    @Test
    fun `same guid in different feeds gives different ids`() {
        val other = "https://other.example.org/rss"
        assertEquals("$other#1", Episode.idFor(other, "1", audio))
        assertEquals("$feed#1", Episode.idFor(feed, "1", audio))
    }
}
