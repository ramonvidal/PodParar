package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedMergeChaptersTest {
    private val feed = "https://example.com/feed.xml"

    private fun ep(guid: String, chaptersUrl: String?, positionMs: Long = 0L, played: Boolean = false) = Episode(
        id = "$feed#$guid",
        podcastUrl = feed,
        title = "Episode $guid",
        description = "",
        audioUrl = "https://cdn.example.com/$guid.mp3",
        imageUrl = null,
        pubDate = 1_000L,
        durationMs = 1_800_000L,
        positionMs = positionMs,
        played = played,
        chaptersUrl = chaptersUrl,
    )

    @Test
    fun `known episode takes the fresh chapters url`() {
        val result = FeedMerge.merge(listOf(ep("a", "https://old/chapters.json")), listOf(ep("a", "https://new/chapters.json")))
        assertEquals("https://new/chapters.json", result.single().chaptersUrl)
    }

    @Test
    fun `known episode gains a chapters url it did not have`() {
        val result = FeedMerge.merge(listOf(ep("a", null)), listOf(ep("a", "https://new/chapters.json")))
        assertEquals("https://new/chapters.json", result.single().chaptersUrl)
    }

    @Test
    fun `stored chapters url stays when the fresh one is null`() {
        val result = FeedMerge.merge(listOf(ep("a", "https://old/chapters.json")), listOf(ep("a", null)))
        assertEquals("https://old/chapters.json", result.single().chaptersUrl)
    }

    @Test
    fun `both null stays null`() {
        assertNull(FeedMerge.merge(listOf(ep("a", null)), listOf(ep("a", null))).single().chaptersUrl)
    }

    @Test
    fun `new episode keeps its chapters url as parsed`() {
        val result = FeedMerge.merge(emptyList(), listOf(ep("a", "https://new/chapters.json")))
        assertEquals("https://new/chapters.json", result.single().chaptersUrl)
    }

    @Test
    fun `taking a fresh chapters url keeps progress`() {
        val stored = listOf(ep("a", null, positionMs = 123_000L, played = true))
        val result = FeedMerge.merge(stored, listOf(ep("a", "https://new/chapters.json")))
        assertEquals(123_000L, result.single().positionMs)
        assertEquals(true, result.single().played)
    }
}
