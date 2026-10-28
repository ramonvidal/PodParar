package com.podparar.core.model.parse

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DateParserTest {
    /** 2024-10-02T10:00:00Z, the instant most cases below describe. */
    private val tenUtc = Instant.parse("2024-10-02T10:00:00Z").toEpochMilli()

    private fun utc(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `expected instant is what we think`() {
        assertEquals(1_727_863_200_000L, tenUtc)
    }

    @Test
    fun `rfc 822 with numeric zero offset`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:00 +0000"))
    }

    @Test
    fun `rfc 822 named utc zones`() {
        for (zone in listOf("GMT", "UT", "UTC", "Z")) {
            assertEquals(zone, tenUtc, DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:00 $zone"))
        }
    }

    @Test
    fun `rfc 822 numeric offsets`() {
        assertEquals(utc("2024-10-02T08:00:00Z"), DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:00 +0200"))
        assertEquals(utc("2024-10-02T15:00:00Z"), DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:00 -0500"))
        assertEquals(utc("2024-10-02T04:30:00Z"), DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:00 +0530"))
    }

    @Test
    fun `rfc 822 us zones`() {
        val offsets = mapOf(
            "EST" to 5, "EDT" to 4,
            "CST" to 6, "CDT" to 5,
            "MST" to 7, "MDT" to 6,
            "PST" to 8, "PDT" to 7,
        )
        for ((zone, hoursBehind) in offsets) {
            val expected = tenUtc + hoursBehind * 3_600_000L
            assertEquals(zone, expected, DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:00 $zone"))
        }
    }

    @Test
    fun `rfc 822 without weekday`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("02 Oct 2024 10:00:00 +0000"))
        assertEquals(tenUtc, DateParser.parseEpochMs("02 Oct 2024 10:00:00 GMT"))
    }

    @Test
    fun `rfc 822 one digit day`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("Wed, 2 Oct 2024 10:00:00 +0000"))
        assertEquals(tenUtc, DateParser.parseEpochMs("2 Oct 2024 10:00:00 GMT"))
    }

    @Test
    fun `rfc 822 without seconds`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00 +0000"))
        assertEquals(utc("2024-10-02T10:05:00Z"), DateParser.parseEpochMs("Wed, 02 Oct 2024 10:05 GMT"))
    }

    @Test
    fun `rfc 822 seconds are kept`() {
        assertEquals(utc("2024-10-02T10:00:42Z"), DateParser.parseEpochMs("Wed, 02 Oct 2024 10:00:42 +0000"))
    }

    @Test
    fun `rfc 822 month and day names in any case`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("wed, 02 oct 2024 10:00:00 +0000"))
        assertEquals(tenUtc, DateParser.parseEpochMs("WED, 02 OCT 2024 10:00:00 +0000"))
        assertEquals(tenUtc, DateParser.parseEpochMs("02 oct 2024 10:00:00 GMT"))
    }

    @Test
    fun `rfc 822 every month`() {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        months.forEachIndexed { i, month ->
            val mm = (i + 1).toString().padStart(2, '0')
            assertEquals(month, utc("2024-$mm-15T12:00:00Z"), DateParser.parseEpochMs("15 $month 2024 12:00:00 +0000"))
        }
    }

    @Test
    fun `iso 8601 utc`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("2024-10-02T10:00:00Z"))
    }

    @Test
    fun `iso 8601 with offset`() {
        assertEquals(tenUtc, DateParser.parseEpochMs("2024-10-02T12:00:00+02:00"))
        assertEquals(tenUtc, DateParser.parseEpochMs("2024-10-02T05:00:00-05:00"))
    }

    @Test
    fun `iso 8601 fractional seconds`() {
        assertEquals(tenUtc + 123L, DateParser.parseEpochMs("2024-10-02T10:00:00.123Z"))
        assertEquals(tenUtc + 500L, DateParser.parseEpochMs("2024-10-02T12:00:00.5+02:00"))
    }

    @Test
    fun `iso 8601 date only is midnight utc`() {
        assertEquals(utc("2024-10-02T00:00:00Z"), DateParser.parseEpochMs("2024-10-02"))
        assertEquals(1_727_827_200_000L, DateParser.parseEpochMs("2024-10-02"))
    }

    @Test
    fun `null blank and garbage are zero`() {
        assertEquals(0L, DateParser.parseEpochMs(null))
        assertEquals(0L, DateParser.parseEpochMs(""))
        assertEquals(0L, DateParser.parseEpochMs("   "))
        assertEquals(0L, DateParser.parseEpochMs("yesterday"))
        assertEquals(0L, DateParser.parseEpochMs("Wed, 32 Foo 2024 25:00:00 +0000"))
        assertEquals(0L, DateParser.parseEpochMs("2024-13-45"))
    }
}
