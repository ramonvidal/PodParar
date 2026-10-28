package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedsTest {
    private val delta = 0.0001f

    @Test
    fun `options run from 0_8 to 3 in order`() {
        assertEquals(listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f, 2.5f, 3.0f), PlaybackSpeeds.options)
    }

    @Test
    fun `default is normal speed`() {
        assertEquals(1.0f, PlaybackSpeeds.DEFAULT, delta)
        assertTrue(PlaybackSpeeds.DEFAULT in PlaybackSpeeds.options)
    }

    // next

    @Test
    fun `next steps through every option in order`() {
        val options = PlaybackSpeeds.options
        for (i in 0 until options.size - 1) {
            assertEquals("after ${options[i]}", options[i + 1], PlaybackSpeeds.next(options[i]), delta)
        }
    }

    @Test
    fun `next from a few known values`() {
        assertEquals(1.2f, PlaybackSpeeds.next(1.0f), delta)
        assertEquals(1.0f, PlaybackSpeeds.next(0.8f), delta)
        assertEquals(1.8f, PlaybackSpeeds.next(1.5f), delta)
        assertEquals(3.0f, PlaybackSpeeds.next(2.5f), delta)
    }

    @Test
    fun `next after the fastest wraps to the slowest`() {
        assertEquals(0.8f, PlaybackSpeeds.next(3.0f), delta)
    }

    @Test
    fun `cycling through all options comes back to the start`() {
        var speed = PlaybackSpeeds.DEFAULT
        repeat(PlaybackSpeeds.options.size) { speed = PlaybackSpeeds.next(speed) }
        assertEquals(PlaybackSpeeds.DEFAULT, speed, delta)
    }

    @Test
    fun `an off-list value goes to the first option above it`() {
        assertEquals(1.2f, PlaybackSpeeds.next(1.1f), delta)
        assertEquals(1.5f, PlaybackSpeeds.next(1.25f), delta)
        assertEquals(2.5f, PlaybackSpeeds.next(2.2f), delta)
        assertEquals(3.0f, PlaybackSpeeds.next(2.75f), delta)
        assertEquals(1.0f, PlaybackSpeeds.next(0.9f), delta)
    }

    @Test
    fun `an off-list value below every option goes to the first option above it`() {
        assertEquals(0.8f, PlaybackSpeeds.next(0.5f), delta)
        assertEquals(0.8f, PlaybackSpeeds.next(0.0f), delta)
    }

    @Test
    fun `an off-list value above every option goes to the first option`() {
        assertEquals(0.8f, PlaybackSpeeds.next(3.5f), delta)
        assertEquals(0.8f, PlaybackSpeeds.next(10f), delta)
    }

    @Test
    fun `next always returns one of the options`() {
        for (value in listOf(-1f, 0f, 0.5f, 0.8f, 0.95f, 1f, 1.33f, 1.9f, 2f, 2.9f, 3f, 4f, 100f)) {
            val next = PlaybackSpeeds.next(value)
            assertTrue("next($value) = $next", PlaybackSpeeds.options.any { kotlin.math.abs(it - next) < delta })
        }
    }

    // clamp

    @Test
    fun `clamp keeps values in range`() {
        assertEquals(0.8f, PlaybackSpeeds.clamp(0.8f), delta)
        assertEquals(1.0f, PlaybackSpeeds.clamp(1.0f), delta)
        assertEquals(1.3f, PlaybackSpeeds.clamp(1.3f), delta)
        assertEquals(2.5f, PlaybackSpeeds.clamp(2.5f), delta)
        assertEquals(3.0f, PlaybackSpeeds.clamp(3.0f), delta)
    }

    @Test
    fun `clamp raises slow values to 0_8`() {
        assertEquals(0.8f, PlaybackSpeeds.clamp(0.79f), delta)
        assertEquals(0.8f, PlaybackSpeeds.clamp(0.5f), delta)
        assertEquals(0.8f, PlaybackSpeeds.clamp(0f), delta)
        assertEquals(0.8f, PlaybackSpeeds.clamp(-2f), delta)
    }

    @Test
    fun `clamp lowers fast values to 3`() {
        assertEquals(3.0f, PlaybackSpeeds.clamp(3.01f), delta)
        assertEquals(3.0f, PlaybackSpeeds.clamp(5f), delta)
        assertEquals(3.0f, PlaybackSpeeds.clamp(Float.MAX_VALUE), delta)
    }

    @Test
    fun `clamp turns NaN into the default`() {
        assertEquals(PlaybackSpeeds.DEFAULT, PlaybackSpeeds.clamp(Float.NaN), delta)
    }

    @Test
    fun `clamp handles infinities`() {
        assertEquals(3.0f, PlaybackSpeeds.clamp(Float.POSITIVE_INFINITY), delta)
        assertEquals(0.8f, PlaybackSpeeds.clamp(Float.NEGATIVE_INFINITY), delta)
    }
}
