package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRulesTest {
    private val hour = 3_600_000L

    private fun episode(positionMs: Long = 0L, durationMs: Long = hour, played: Boolean = false) = Episode(
        id = "https://example.com/feed.xml#guid-1",
        podcastUrl = "https://example.com/feed.xml",
        title = "Episode 1",
        description = "",
        audioUrl = "https://example.com/1.mp3",
        imageUrl = null,
        pubDate = 0L,
        durationMs = durationMs,
        positionMs = positionMs,
        played = played,
    )

    @Test
    fun `threshold is thirty seconds`() {
        assertEquals(30_000L, PlaybackRules.PLAYED_THRESHOLD_MS)
    }

    @Test
    fun `exactly thirty seconds left is not played yet`() {
        assertFalse(PlaybackRules.isPlayed(positionMs = 70_000L, durationMs = 100_000L))
    }

    @Test
    fun `just under thirty seconds left is played`() {
        assertTrue(PlaybackRules.isPlayed(positionMs = 70_001L, durationMs = 100_000L))
    }

    @Test
    fun `start and middle are not played`() {
        assertFalse(PlaybackRules.isPlayed(positionMs = 0L, durationMs = hour))
        assertFalse(PlaybackRules.isPlayed(positionMs = hour / 2, durationMs = hour))
    }

    @Test
    fun `at or past the end is played`() {
        assertTrue(PlaybackRules.isPlayed(positionMs = hour, durationMs = hour))
        assertTrue(PlaybackRules.isPlayed(positionMs = hour + 5_000L, durationMs = hour))
    }

    @Test
    fun `unknown duration is never played by position`() {
        assertFalse(PlaybackRules.isPlayed(positionMs = 0L, durationMs = 0L))
        assertFalse(PlaybackRules.isPlayed(positionMs = 10 * hour, durationMs = 0L))
        assertFalse(PlaybackRules.isPlayed(positionMs = 10_000L, durationMs = -1L))
    }

    @Test
    fun `ended is played whatever the position`() {
        assertTrue(PlaybackRules.isPlayed(positionMs = 0L, durationMs = hour, ended = true))
        assertTrue(PlaybackRules.isPlayed(positionMs = 1_000L, durationMs = 0L, ended = true))
    }

    @Test
    fun `resume a played episode from the start`() {
        assertEquals(0L, PlaybackRules.resumePositionMs(episode(positionMs = hour / 2, played = true)))
    }

    @Test
    fun `resume near the end from the start`() {
        assertEquals(0L, PlaybackRules.resumePositionMs(episode(positionMs = hour - 29_999L)))
        assertEquals(0L, PlaybackRules.resumePositionMs(episode(positionMs = hour)))
    }

    @Test
    fun `resume exactly thirty seconds from the end where it was`() {
        assertEquals(hour - 30_000L, PlaybackRules.resumePositionMs(episode(positionMs = hour - 30_000L)))
    }

    @Test
    fun `resume in the middle where it was`() {
        assertEquals(754_000L, PlaybackRules.resumePositionMs(episode(positionMs = 754_000L)))
        assertEquals(0L, PlaybackRules.resumePositionMs(episode(positionMs = 0L)))
    }

    @Test
    fun `resume with unknown duration where it was`() {
        assertEquals(10 * hour, PlaybackRules.resumePositionMs(episode(positionMs = 10 * hour, durationMs = 0L)))
    }

    @Test
    fun `resume never negative`() {
        assertEquals(0L, PlaybackRules.resumePositionMs(episode(positionMs = -5_000L)))
        assertEquals(0L, PlaybackRules.resumePositionMs(episode(positionMs = -5_000L, durationMs = 0L)))
    }

    @Test
    fun `progress is the played fraction`() {
        assertEquals(0f, PlaybackRules.progress(0L, hour), 0.0001f)
        assertEquals(0.25f, PlaybackRules.progress(hour / 4, hour), 0.0001f)
        assertEquals(1f, PlaybackRules.progress(hour, hour), 0.0001f)
    }

    @Test
    fun `progress is clamped`() {
        assertEquals(1f, PlaybackRules.progress(2 * hour, hour), 0.0001f)
        assertEquals(0f, PlaybackRules.progress(-1_000L, hour), 0.0001f)
    }

    @Test
    fun `progress with unknown duration is zero`() {
        assertEquals(0f, PlaybackRules.progress(1_000L, 0L), 0.0001f)
        assertEquals(0f, PlaybackRules.progress(1_000L, -1L), 0.0001f)
        assertEquals(0f, PlaybackRules.progress(0L, 0L), 0.0001f)
    }

    @Test
    fun `episode state`() {
        assertEquals(EpisodeState.NEW, episode().state)
        assertEquals(EpisodeState.IN_PROGRESS, episode(positionMs = 1L).state)
        assertEquals(EpisodeState.PLAYED, episode(played = true).state)
        assertEquals(EpisodeState.PLAYED, episode(positionMs = hour / 2, played = true).state)
    }
}
