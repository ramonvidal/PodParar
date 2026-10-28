package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formats.timeLabel / remainingLabel / speedLabel: the player clock and the speed button. */
class PlayerFormatsTest {
    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute

    // timeLabel

    @Test
    fun `zero is 0_00`() {
        assertEquals("0:00", Formats.timeLabel(0L))
    }

    @Test
    fun `negative is 0_00`() {
        assertEquals("0:00", Formats.timeLabel(-1L))
        assertEquals("0:00", Formats.timeLabel(-5 * second))
        assertEquals("0:00", Formats.timeLabel(-2 * hour))
    }

    @Test
    fun `seconds are floored`() {
        assertEquals("0:00", Formats.timeLabel(999L))
        assertEquals("0:01", Formats.timeLabel(1_000L))
        assertEquals("0:01", Formats.timeLabel(1_999L))
        assertEquals("0:59", Formats.timeLabel(59_999L))
    }

    @Test
    fun `seconds are padded, minutes are not`() {
        assertEquals("0:05", Formats.timeLabel(5 * second))
        assertEquals("1:00", Formats.timeLabel(minute))
        assertEquals("1:05", Formats.timeLabel(minute + 5 * second))
        assertEquals("12:34", Formats.timeLabel(12 * minute + 34 * second))
        assertEquals("59:59", Formats.timeLabel(hour - 1L))
    }

    @Test
    fun `an hour or more shows hours with padded minutes`() {
        assertEquals("1:00:00", Formats.timeLabel(hour))
        assertEquals("1:02:03", Formats.timeLabel(hour + 2 * minute + 3 * second))
        assertEquals("1:59:59", Formats.timeLabel(2 * hour - 1L))
        assertEquals("2:30:00", Formats.timeLabel(2 * hour + 30 * minute))
        assertEquals("10:00:00", Formats.timeLabel(10 * hour))
    }

    // remainingLabel

    @Test
    fun `remaining is the time left with a minus`() {
        assertEquals("-12:34", Formats.remainingLabel(0L, 12 * minute + 34 * second))
        assertEquals("-0:30", Formats.remainingLabel(30 * second, minute))
        assertEquals("-1:00:00", Formats.remainingLabel(30 * minute, hour + 30 * minute))
    }

    @Test
    fun `remaining is timeLabel of the difference, floored`() {
        assertEquals("-" + Formats.timeLabel(59_500L), Formats.remainingLabel(500L, minute))
        assertEquals("-0:59", Formats.remainingLabel(500L, minute))
    }

    @Test
    fun `remaining at the end is minus zero`() {
        assertEquals("-0:00", Formats.remainingLabel(hour, hour))
    }

    @Test
    fun `remaining never goes below minus zero`() {
        assertEquals("-0:00", Formats.remainingLabel(hour + 1L, hour))
        assertEquals("-0:00", Formats.remainingLabel(5 * hour, hour))
    }

    @Test
    fun `remaining with unknown duration is empty`() {
        assertEquals("", Formats.remainingLabel(0L, 0L))
        assertEquals("", Formats.remainingLabel(30 * second, 0L))
        assertEquals("", Formats.remainingLabel(30 * second, -1L))
    }

    // speedLabel

    @Test
    fun `whole speeds have no decimals`() {
        assertEquals("1×", Formats.speedLabel(1.0f))
        assertEquals("2×", Formats.speedLabel(2.0f))
        assertEquals("3×", Formats.speedLabel(3.0f))
    }

    @Test
    fun `fractional speeds drop trailing zeros`() {
        assertEquals("1.5×", Formats.speedLabel(1.5f))
        assertEquals("0.8×", Formats.speedLabel(0.8f))
        assertEquals("2.5×", Formats.speedLabel(2.5f))
        assertEquals("1.2×", Formats.speedLabel(1.2f))
        assertEquals("1.8×", Formats.speedLabel(1.8f))
    }

    @Test
    fun `two decimals are kept`() {
        assertEquals("1.25×", Formats.speedLabel(1.25f))
        assertEquals("0.75×", Formats.speedLabel(0.75f))
    }

    @Test
    fun `at most two decimals`() {
        assertEquals("1.33×", Formats.speedLabel(1.3333333f))
        assertEquals("1.1×", Formats.speedLabel(1.1f))
    }

    @Test
    fun `every speed option has a short label with the times sign`() {
        for (speed in PlaybackSpeeds.options) {
            val label = Formats.speedLabel(speed)
            assertTrue(label, label.endsWith("×"))
            assertTrue(label, !label.contains("x"))
            assertTrue("speedLabel($speed) = $label", label.length <= 5)
        }
    }
}
