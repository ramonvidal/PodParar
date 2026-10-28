package com.podparar.core.model.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationParserTest {
    private val expected1h2m3s = (1 * 3600 + 2 * 60 + 3) * 1000L

    @Test
    fun `hours minutes seconds`() {
        assertEquals(expected1h2m3s, DurationParser.parseMs("01:02:03"))
        assertEquals((12 * 3600 + 34 * 60 + 56) * 1000L, DurationParser.parseMs("12:34:56"))
    }

    @Test
    fun `one digit hours`() {
        assertEquals(expected1h2m3s, DurationParser.parseMs("1:02:03"))
    }

    @Test
    fun `minutes seconds`() {
        assertEquals((12 * 60 + 34) * 1000L, DurationParser.parseMs("12:34"))
        assertEquals((5 * 60 + 7) * 1000L, DurationParser.parseMs("05:07"))
        assertEquals(0L, DurationParser.parseMs("00:00"))
    }

    @Test
    fun `plain seconds`() {
        assertEquals(1_834_000L, DurationParser.parseMs("1834"))
        assertEquals(0L, DurationParser.parseMs("0"))
    }

    @Test
    fun `fractional seconds`() {
        assertEquals(1_834_500L, DurationParser.parseMs("1834.5"))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals((12 * 60 + 34) * 1000L, DurationParser.parseMs("  12:34 "))
        assertEquals(1_834_000L, DurationParser.parseMs("\n1834\t"))
        assertEquals(expected1h2m3s, DurationParser.parseMs(" 01:02:03 "))
    }

    @Test
    fun `null and blank are zero`() {
        assertEquals(0L, DurationParser.parseMs(null))
        assertEquals(0L, DurationParser.parseMs(""))
        assertEquals(0L, DurationParser.parseMs("   "))
    }

    @Test
    fun `garbage is zero`() {
        assertEquals(0L, DurationParser.parseMs("abc"))
        assertEquals(0L, DurationParser.parseMs("one hour"))
        assertEquals(0L, DurationParser.parseMs("12:ab"))
        assertEquals(0L, DurationParser.parseMs(":"))
    }

    @Test
    fun `negative is zero`() {
        assertEquals(0L, DurationParser.parseMs("-5"))
        assertEquals(0L, DurationParser.parseMs("-1834.5"))
    }
}
