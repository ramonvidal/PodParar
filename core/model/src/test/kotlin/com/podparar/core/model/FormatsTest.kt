package com.podparar.core.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    // durationLabel

    @Test
    fun `unknown or negative duration is empty`() {
        assertEquals("", Formats.durationLabel(0L))
        assertEquals("", Formats.durationLabel(-1L))
        assertEquals("", Formats.durationLabel(-hour))
    }

    @Test
    fun `very short durations show at least one minute`() {
        assertEquals("1 min", Formats.durationLabel(1L))
        assertEquals("1 min", Formats.durationLabel(29_999L))
        assertEquals("1 min", Formats.durationLabel(30_000L))
    }

    @Test
    fun `minutes round to the nearest, half up`() {
        assertEquals("1 min", Formats.durationLabel(89_999L))
        assertEquals("2 min", Formats.durationLabel(90_000L))
        assertEquals("45 min", Formats.durationLabel(45 * minute))
        assertEquals("45 min", Formats.durationLabel(45 * minute + 29_999L))
        assertEquals("46 min", Formats.durationLabel(45 * minute + 30_000L))
        assertEquals("45 min", Formats.durationLabel(44 * minute + 30_000L))
    }

    @Test
    fun `just under an hour after rounding stays in minutes`() {
        assertEquals("59 min", Formats.durationLabel(59 * minute))
        assertEquals("59 min", Formats.durationLabel(59 * minute + 29_999L))
    }

    @Test
    fun `rounding up to sixty minutes shows hours`() {
        assertEquals("1 h", Formats.durationLabel(59 * minute + 30_000L))
    }

    @Test
    fun `exactly an hour is 1 h`() {
        assertEquals("1 h", Formats.durationLabel(hour))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals("1 h 1 min", Formats.durationLabel(hour + minute))
        assertEquals("1 h 5 min", Formats.durationLabel(65 * minute))
        assertEquals("1 h 59 min", Formats.durationLabel(119 * minute))
        assertEquals("2 h", Formats.durationLabel(119 * minute + 30_000L))
        assertEquals("2 h", Formats.durationLabel(2 * hour))
        assertEquals("2 h 30 min", Formats.durationLabel(150 * minute))
        assertEquals("10 h 3 min", Formats.durationLabel(10 * hour + 3 * minute))
    }

    @Test
    fun `seconds past an hour round like minutes`() {
        assertEquals("1 h", Formats.durationLabel(hour + 29_999L))
        assertEquals("1 h 1 min", Formats.durationLabel(hour + 30_000L))
    }

    // pubDateLabel

    private val madrid = ZoneId.of("Europe/Madrid")
    private val newYork = ZoneId.of("America/New_York")
    private val utc = ZoneOffset.UTC

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `unknown pub date is empty`() {
        val now = at(madrid, 2026, 3, 12)
        assertEquals("", Formats.pubDateLabel(0L, now, madrid))
        assertEquals("", Formats.pubDateLabel(-1L, now, madrid))
    }

    @Test
    fun `same local day is Today`() {
        val now = at(madrid, 2026, 3, 12, 23, 59)
        assertEquals("Today", Formats.pubDateLabel(at(madrid, 2026, 3, 12, 0, 0), now, madrid))
        assertEquals("Today", Formats.pubDateLabel(now, now, madrid))
    }

    @Test
    fun `the day before is Yesterday`() {
        val now = at(madrid, 2026, 3, 12, 10, 0)
        assertEquals("Yesterday", Formats.pubDateLabel(at(madrid, 2026, 3, 11, 0, 0), now, madrid))
        assertEquals("Yesterday", Formats.pubDateLabel(at(madrid, 2026, 3, 11, 23, 59), now, madrid))
    }

    @Test
    fun `two days before shows the date`() {
        val now = at(madrid, 2026, 3, 12, 0, 1)
        assertEquals("10 Mar", Formats.pubDateLabel(at(madrid, 2026, 3, 10, 23, 59), now, madrid))
    }

    @Test
    fun `yesterday across midnight is by local date, not elapsed time`() {
        // 00:30 in Madrid is 23:30 UTC the day before; the episode at 23:30 Madrid is 22:30 UTC.
        val now = at(madrid, 2026, 3, 12, 0, 30)
        val pub = at(madrid, 2026, 3, 11, 23, 30)
        assertEquals("Yesterday", Formats.pubDateLabel(pub, now, madrid))
        // In UTC both are on 11 March.
        assertEquals("Today", Formats.pubDateLabel(pub, now, utc))
    }

    @Test
    fun `same instants in a zone behind UTC`() {
        // 2026-03-12 02:00 UTC is 22:00 on 11 March in New York; 2026-03-11 06:00 UTC is 02:00 on 11 March there.
        val now = at(utc, 2026, 3, 12, 2, 0)
        val pub = at(utc, 2026, 3, 11, 6, 0)
        assertEquals("Today", Formats.pubDateLabel(pub, now, newYork))
        assertEquals("Yesterday", Formats.pubDateLabel(pub, now, utc))
    }

    @Test
    fun `earlier this year shows day and month`() {
        val now = at(madrid, 2026, 10, 28)
        assertEquals("12 Mar", Formats.pubDateLabel(at(madrid, 2026, 3, 12), now, madrid))
        assertEquals("1 Jan", Formats.pubDateLabel(at(madrid, 2026, 1, 1, 0, 0), now, madrid))
        assertEquals("5 Sep", Formats.pubDateLabel(at(madrid, 2026, 9, 5), now, madrid))
    }

    @Test
    fun `day of month has no leading zero`() {
        val now = at(madrid, 2026, 10, 28)
        assertEquals("3 Feb", Formats.pubDateLabel(at(madrid, 2026, 2, 3), now, madrid))
    }

    @Test
    fun `another year shows the year`() {
        val now = at(madrid, 2026, 10, 28)
        assertEquals("12 Mar 2023", Formats.pubDateLabel(at(madrid, 2023, 3, 12), now, madrid))
        assertEquals("28 Oct 2025", Formats.pubDateLabel(at(madrid, 2025, 10, 28), now, madrid))
    }

    @Test
    fun `yesterday across new year`() {
        val now = at(madrid, 2026, 1, 1, 0, 10)
        assertEquals("Yesterday", Formats.pubDateLabel(at(madrid, 2025, 12, 31, 23, 50), now, madrid))
        assertEquals("30 Dec 2025", Formats.pubDateLabel(at(madrid, 2025, 12, 30, 23, 50), now, madrid))
    }

    @Test
    fun `year is decided in the local zone`() {
        // 2026-01-01 00:30 in Madrid is still 2025 in UTC.
        val now = at(madrid, 2026, 1, 3, 12, 0)
        val pub = at(madrid, 2026, 1, 1, 0, 30)
        assertEquals("1 Jan", Formats.pubDateLabel(pub, now, madrid))
        assertEquals("31 Dec 2025", Formats.pubDateLabel(pub, now, utc))
    }

    @Test
    fun `month names are English whatever the default locale`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
            val now = at(madrid, 2026, 10, 28)
            assertEquals("12 Aug", Formats.pubDateLabel(at(madrid, 2026, 8, 12), now, madrid))
            assertEquals("12 Mar 2023", Formats.pubDateLabel(at(madrid, 2023, 3, 12), now, madrid))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
