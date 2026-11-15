package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimersMinutesLeftTest {
    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun timer(leftMs: Long) = SleepTimer(endsAt = now + leftMs, endOfEpisode = false)

    @Test
    fun `end of episode has no minutes`() {
        assertNull(SleepTimers.minutesLeft(SleepTimer(endsAt = null, endOfEpisode = true), now))
    }

    @Test
    fun `no end has no minutes`() {
        assertNull(SleepTimers.minutesLeft(SleepTimer(endsAt = null, endOfEpisode = false), now))
    }

    @Test
    fun `exactly N minutes left is N`() {
        for (n in listOf(1, 5, 15, 60)) {
            assertEquals(n, SleepTimers.minutesLeft(timer(n * minute), now))
        }
    }

    @Test
    fun `partial minutes round up`() {
        assertEquals(15, SleepTimers.minutesLeft(timer(14 * minute + 1), now))
        assertEquals(15, SleepTimers.minutesLeft(timer(14 * minute + 59_999L), now))
        assertEquals(2, SleepTimers.minutesLeft(timer(minute + 1), now))
    }

    @Test
    fun `one millisecond left is one minute`() {
        assertEquals(1, SleepTimers.minutesLeft(timer(1L), now))
    }

    @Test
    fun `expired is at least one minute`() {
        assertEquals(1, SleepTimers.minutesLeft(timer(0L), now))
        assertEquals(1, SleepTimers.minutesLeft(timer(-5 * minute), now))
    }
}
