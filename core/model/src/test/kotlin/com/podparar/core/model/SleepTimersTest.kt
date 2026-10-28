package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimersTest {
    private val delta = 0.0001f
    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    // SleepTimer.remainingMs

    @Test
    fun `remainingMs is the time until endsAt`() {
        val timer = SleepTimer(endsAt = now + 90_000L, endOfEpisode = false)
        assertEquals(90_000L, timer.remainingMs(now))
        assertEquals(1L, timer.remainingMs(now + 89_999L))
        assertEquals(0L, timer.remainingMs(now + 90_000L))
    }

    @Test
    fun `remainingMs is never negative`() {
        val timer = SleepTimer(endsAt = now, endOfEpisode = false)
        assertEquals(0L, timer.remainingMs(now + 5_000L))
        assertEquals(0L, timer.remainingMs(now + 10 * minute))
    }

    @Test
    fun `remainingMs is null for end of episode`() {
        assertNull(SleepTimer(endsAt = null, endOfEpisode = true).remainingMs(now))
    }

    // constants

    @Test
    fun `the options and constants are as specified`() {
        assertEquals(listOf(15, 30, 45, 60), SleepTimers.minuteOptions)
        assertEquals(5, SleepTimers.EXTEND_MINUTES)
        assertEquals(10_000L, SleepTimers.FADE_MS)
    }

    // after

    @Test
    fun `after sets endsAt minutes from now`() {
        for (m in SleepTimers.minuteOptions) {
            val timer = SleepTimers.after(m, now)
            assertEquals(now + m * minute, timer.endsAt)
            assertEquals(false, timer.endOfEpisode)
            assertEquals(m * minute, timer.remainingMs(now))
        }
    }

    @Test
    fun `after works for a length that is not an option`() {
        assertEquals(now + 7 * minute, SleepTimers.after(7, now).endsAt)
    }

    // extend

    @Test
    fun `extend adds five minutes to a timed timer`() {
        val timer = SleepTimers.after(15, now)
        val extended = SleepTimers.extend(timer)
        assertEquals(now + 20 * minute, extended.endsAt)
        assertEquals(false, extended.endOfEpisode)
    }

    @Test
    fun `extend adds to the end time, not to now`() {
        val timer = SleepTimer(endsAt = now + 30_000L, endOfEpisode = false)
        assertEquals(now + 30_000L + 5 * minute, SleepTimers.extend(timer).endsAt)
    }

    @Test
    fun `extend twice adds ten minutes`() {
        val timer = SleepTimers.after(30, now)
        assertEquals(now + 40 * minute, SleepTimers.extend(SleepTimers.extend(timer)).endsAt)
    }

    @Test
    fun `extend leaves an end of episode timer unchanged`() {
        val timer = SleepTimer(endsAt = null, endOfEpisode = true)
        assertEquals(timer, SleepTimers.extend(timer))
    }

    // volumeAt

    @Test
    fun `volume is full while more than the fade time remains`() {
        val timer = SleepTimer(endsAt = now + 60_000L, endOfEpisode = false)
        assertEquals(1f, SleepTimers.volumeAt(timer, now), delta)
        assertEquals(1f, SleepTimers.volumeAt(timer, now + 60_000L - 10_001L), delta)
    }

    @Test
    fun `volume is full exactly at the start of the fade`() {
        val timer = SleepTimer(endsAt = now + 10_000L, endOfEpisode = false)
        assertEquals(1f, SleepTimers.volumeAt(timer, now), delta)
    }

    @Test
    fun `volume falls linearly over the fade`() {
        val timer = SleepTimer(endsAt = now + 10_000L, endOfEpisode = false)
        assertEquals(0.75f, SleepTimers.volumeAt(timer, now + 2_500L), delta)
        assertEquals(0.5f, SleepTimers.volumeAt(timer, now + 5_000L), delta)
        assertEquals(0.25f, SleepTimers.volumeAt(timer, now + 7_500L), delta)
        assertEquals(0.1f, SleepTimers.volumeAt(timer, now + 9_000L), delta)
    }

    @Test
    fun `volume is zero at the end and after it`() {
        val timer = SleepTimer(endsAt = now + 10_000L, endOfEpisode = false)
        assertEquals(0f, SleepTimers.volumeAt(timer, now + 10_000L), delta)
        assertEquals(0f, SleepTimers.volumeAt(timer, now + 20_000L), delta)
    }

    @Test
    fun `volume is between zero and one throughout`() {
        val timer = SleepTimer(endsAt = now + 15_000L, endOfEpisode = false)
        var previous = 1f
        for (t in 0L..20_000L step 250L) {
            val v = SleepTimers.volumeAt(timer, now + t)
            assertTrue("volume $v at $t", v in 0f..1f)
            assertTrue("volume rose from $previous to $v at $t", v <= previous + delta)
            previous = v
        }
    }

    @Test
    fun `volume is full for end of episode`() {
        val timer = SleepTimer(endsAt = null, endOfEpisode = true)
        assertEquals(1f, SleepTimers.volumeAt(timer, now), delta)
        assertEquals(1f, SleepTimers.volumeAt(timer, now + 100 * minute), delta)
    }

    // label

    @Test
    fun `label shows whole minutes left`() {
        val timer = SleepTimer(endsAt = now + 23 * minute, endOfEpisode = false)
        assertEquals("23 min", SleepTimers.label(timer, now))
    }

    @Test
    fun `label rounds partial minutes up`() {
        val timer = SleepTimer(endsAt = now + 23 * minute, endOfEpisode = false)
        assertEquals("23 min", SleepTimers.label(timer, now + 1L))
        assertEquals("23 min", SleepTimers.label(timer, now + 59_999L))
        assertEquals("22 min", SleepTimers.label(timer, now + minute))
        assertEquals("22 min", SleepTimers.label(timer, now + minute + 1L))
    }

    @Test
    fun `label is at least one minute`() {
        val timer = SleepTimer(endsAt = now + 30_000L, endOfEpisode = false)
        assertEquals("1 min", SleepTimers.label(timer, now))
        assertEquals("1 min", SleepTimers.label(timer, now + 29_999L))
        assertEquals("1 min", SleepTimers.label(timer, now + 30_000L))
        assertEquals("1 min", SleepTimers.label(timer, now + 5 * minute))
    }

    @Test
    fun `label for a fresh timer from after`() {
        assertEquals("15 min", SleepTimers.label(SleepTimers.after(15, now), now))
        assertEquals("60 min", SleepTimers.label(SleepTimers.after(60, now), now))
    }

    @Test
    fun `label for end of episode`() {
        assertEquals("End of episode", SleepTimers.label(SleepTimer(endsAt = null, endOfEpisode = true), now))
    }
}
