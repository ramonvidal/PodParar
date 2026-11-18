package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLimitTest {
    @Test
    fun `constants`() {
        assertEquals(1, DownloadLimit.MIN)
        assertEquals(200, DownloadLimit.MAX)
        assertEquals(20, DownloadLimit.DEFAULT)
        assertEquals(listOf(5, 10, 20, 30, 50, 100, 200), DownloadLimit.OPTIONS)
    }

    @Test
    fun `options and default are within range`() {
        assertTrue(DownloadLimit.DEFAULT in DownloadLimit.OPTIONS)
        assertTrue(DownloadLimit.OPTIONS.all { it in DownloadLimit.MIN..DownloadLimit.MAX })
    }

    @Test
    fun `coerce clamps to the range`() {
        assertEquals(1, DownloadLimit.coerce(0))
        assertEquals(1, DownloadLimit.coerce(-5))
        assertEquals(200, DownloadLimit.coerce(1000))
        assertEquals(1, DownloadLimit.coerce(1))
        assertEquals(200, DownloadLimit.coerce(200))
        assertEquals(37, DownloadLimit.coerce(37))
    }

    @Test
    fun `isReached is active at or above the limit`() {
        assertFalse(DownloadLimit.isReached(19, 20))
        assertTrue(DownloadLimit.isReached(20, 20))
        assertTrue(DownloadLimit.isReached(25, 20))
        assertFalse(DownloadLimit.isReached(0, 20))
    }

    @Test
    fun `isReached uses the coerced limit`() {
        assertTrue(DownloadLimit.isReached(1, 0))
        assertTrue(DownloadLimit.isReached(1, -5))
        assertFalse(DownloadLimit.isReached(0, 0))
        assertFalse(DownloadLimit.isReached(199, 1000))
        assertTrue(DownloadLimit.isReached(200, 1000))
    }

    @Test
    fun `slotsLeft counts the room left and is never negative`() {
        assertEquals(20, DownloadLimit.slotsLeft(0, 20))
        assertEquals(5, DownloadLimit.slotsLeft(15, 20))
        assertEquals(0, DownloadLimit.slotsLeft(20, 20))
        assertEquals(0, DownloadLimit.slotsLeft(50, 20))
        assertEquals(1, DownloadLimit.slotsLeft(0, 0))
        assertEquals(200, DownloadLimit.slotsLeft(0, 1000))
    }
}
