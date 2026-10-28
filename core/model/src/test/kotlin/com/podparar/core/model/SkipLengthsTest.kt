package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipLengthsTest {

    @Test
    fun `options and defaults are as specified`() {
        assertEquals(listOf(5_000L, 10_000L, 30_000L), SkipLengths.backOptionsMs)
        assertEquals(listOf(5_000L, 10_000L, 30_000L), SkipLengths.forwardOptionsMs)
        assertEquals(10_000L, SkipLengths.DEFAULT_BACK_MS)
        assertEquals(30_000L, SkipLengths.DEFAULT_FORWARD_MS)
        assertTrue(SkipLengths.DEFAULT_BACK_MS in SkipLengths.backOptionsMs)
        assertTrue(SkipLengths.DEFAULT_FORWARD_MS in SkipLengths.forwardOptionsMs)
    }

    @Test
    fun `an option is its own nearest`() {
        for (ms in SkipLengths.backOptionsMs) assertEquals(ms, SkipLengths.nearestBack(ms))
        for (ms in SkipLengths.forwardOptionsMs) assertEquals(ms, SkipLengths.nearestForward(ms))
    }

    @Test
    fun `values between options go to the nearest one`() {
        assertEquals(5_000L, SkipLengths.nearestBack(6_000L))
        assertEquals(10_000L, SkipLengths.nearestBack(9_000L))
        assertEquals(10_000L, SkipLengths.nearestBack(15_000L))
        assertEquals(30_000L, SkipLengths.nearestBack(25_000L))
        assertEquals(5_000L, SkipLengths.nearestForward(7_000L))
        assertEquals(10_000L, SkipLengths.nearestForward(19_999L))
        assertEquals(30_000L, SkipLengths.nearestForward(20_001L))
    }

    @Test
    fun `ties go to the smaller option`() {
        assertEquals(5_000L, SkipLengths.nearestBack(7_500L))
        assertEquals(10_000L, SkipLengths.nearestBack(20_000L))
        assertEquals(5_000L, SkipLengths.nearestForward(7_500L))
        assertEquals(10_000L, SkipLengths.nearestForward(20_000L))
    }

    @Test
    fun `values outside the range go to the ends`() {
        assertEquals(5_000L, SkipLengths.nearestBack(0L))
        assertEquals(5_000L, SkipLengths.nearestBack(-10_000L))
        assertEquals(30_000L, SkipLengths.nearestBack(120_000L))
        assertEquals(30_000L, SkipLengths.nearestBack(Long.MAX_VALUE))
        assertEquals(5_000L, SkipLengths.nearestForward(1L))
        assertEquals(30_000L, SkipLengths.nearestForward(45_000L))
    }
}
