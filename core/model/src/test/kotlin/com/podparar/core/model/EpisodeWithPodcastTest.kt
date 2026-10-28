package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeWithPodcastTest {
    private fun episode(imageUrl: String?) = Episode(
        id = "https://example.com/feed.xml#1",
        podcastUrl = "https://example.com/feed.xml",
        title = "Episode 1",
        description = "",
        audioUrl = "https://example.com/1.mp3",
        imageUrl = imageUrl,
        pubDate = 0L,
        durationMs = 60_000L,
    )

    @Test
    fun `artwork is the episode's own image when it has one`() {
        val item = EpisodeWithPodcast(episode("https://img.example/ep.jpg"), "Show", "https://img.example/show.jpg")
        assertEquals("https://img.example/ep.jpg", item.artworkUrl)
    }

    @Test
    fun `artwork falls back to the show's artwork`() {
        val item = EpisodeWithPodcast(episode(null), "Show", "https://img.example/show.jpg")
        assertEquals("https://img.example/show.jpg", item.artworkUrl)
    }

    @Test
    fun `artwork is null when neither has one`() {
        assertNull(EpisodeWithPodcast(episode(null), "Show", null).artworkUrl)
    }

    @Test
    fun `episode image is used even when the show has no artwork`() {
        val item = EpisodeWithPodcast(episode("https://img.example/ep.jpg"), "Show", null)
        assertEquals("https://img.example/ep.jpg", item.artworkUrl)
    }

    @Test
    fun `keeps the episode and show title it was given`() {
        val ep = episode(null)
        val item = EpisodeWithPodcast(ep, "My Show", null)
        assertEquals(ep, item.episode)
        assertEquals("My Show", item.podcastTitle)
    }
}
