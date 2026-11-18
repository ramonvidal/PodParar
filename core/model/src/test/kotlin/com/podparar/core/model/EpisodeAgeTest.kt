package com.podparar.core.model

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeAgeTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun ms(text: String, zone: ZoneId = utc) = ZonedDateTime.parse(text).withZoneSameInstant(zone).toInstant().toEpochMilli()

    @Test
    fun `cutoff is one calendar month back`() {
        val now = ms("2026-05-15T12:00:00Z")
        assertEquals(ms("2026-04-15T12:00:00Z"), EpisodeAge.cutoffMs(now, utc))
    }

    @Test
    fun `cutoff clamps to the end of a shorter month`() {
        val now = ms("2026-03-31T12:00:00Z")
        assertEquals(ms("2026-02-28T12:00:00Z"), EpisodeAge.cutoffMs(now, utc))
    }

    @Test
    fun `cutoff crosses the year`() {
        val now = ms("2026-01-10T08:30:00Z")
        assertEquals(ms("2025-12-10T08:30:00Z"), EpisodeAge.cutoffMs(now, utc))
    }

    @Test
    fun `cutoff keeps local wall-clock time across a DST change`() {
        val zone = ZoneId.of("America/New_York")
        // 2026-04-10 12:00 EDT (UTC-4); a month back is 2026-03-10 12:00 EDT too (DST began 2026-03-08).
        val now = ZonedDateTime.of(2026, 4, 10, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, EpisodeAge.cutoffMs(now, zone))

        // 2026-03-20 12:00 EDT; a month back is 2026-02-20 12:00 EST (UTC-5): 1 hour more than a fixed offset.
        val now2 = ZonedDateTime.of(2026, 3, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected2 = ZonedDateTime.of(2026, 2, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected2, EpisodeAge.cutoffMs(now2, zone))
        assertEquals(ms("2026-02-20T17:00:00Z"), expected2)
    }

    @Test
    fun `a downloaded episode always shows, even years old`() {
        val now = ms("2026-05-15T12:00:00Z")
        assertTrue(EpisodeAge.showsInNew(ms("2022-01-01T00:00:00Z"), true, now, utc))
    }

    @Test
    fun `an undownloaded old episode is hidden`() {
        val now = ms("2026-05-15T12:00:00Z")
        assertFalse(EpisodeAge.showsInNew(ms("2026-03-01T00:00:00Z"), false, now, utc))
        assertFalse(EpisodeAge.showsInNew(EpisodeAge.cutoffMs(now, utc) - 1, false, now, utc))
    }

    @Test
    fun `an undownloaded recent episode shows`() {
        val now = ms("2026-05-15T12:00:00Z")
        assertTrue(EpisodeAge.showsInNew(ms("2026-05-14T00:00:00Z"), false, now, utc))
        assertTrue(EpisodeAge.showsInNew(now, false, now, utc))
    }

    @Test
    fun `the cutoff itself is inclusive`() {
        val now = ms("2026-05-15T12:00:00Z")
        val cutoff = EpisodeAge.cutoffMs(now, utc)
        assertTrue(EpisodeAge.showsInNew(cutoff, false, now, utc))
        assertFalse(EpisodeAge.showsInNew(cutoff - 1, false, now, utc))
    }

    @Test
    fun `an unknown pubDate shows`() {
        val now = ms("2026-05-15T12:00:00Z")
        assertTrue(EpisodeAge.showsInNew(0L, false, now, utc))
        assertTrue(EpisodeAge.showsInNew(-1L, false, now, utc))
    }
}
