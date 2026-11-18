package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLimitTest {
    @Test
    fun `constants`() {
        assertEquals(10, HomeLimit.MIN)
        assertEquals(500, HomeLimit.MAX)
        assertEquals(50, HomeLimit.DEFAULT)
        assertEquals(listOf(30, 50, 100, 200, 500), HomeLimit.OPTIONS)
    }

    @Test
    fun `default and options are within range`() {
        assertTrue(HomeLimit.DEFAULT in HomeLimit.OPTIONS)
        assertTrue(HomeLimit.OPTIONS.all { it in HomeLimit.MIN..HomeLimit.MAX })
    }

    @Test
    fun `coerce clamps to the range`() {
        assertEquals(10, HomeLimit.coerce(0))
        assertEquals(10, HomeLimit.coerce(-5))
        assertEquals(10, HomeLimit.coerce(9))
        assertEquals(10, HomeLimit.coerce(10))
        assertEquals(37, HomeLimit.coerce(37))
        assertEquals(500, HomeLimit.coerce(500))
        assertEquals(500, HomeLimit.coerce(501))
        assertEquals(500, HomeLimit.coerce(100_000))
    }

    @Test
    fun `maxDownloadsFor is one below the coerced home limit`() {
        assertEquals(49, HomeLimit.maxDownloadsFor(50))
        assertEquals(29, HomeLimit.maxDownloadsFor(30))
        assertEquals(499, HomeLimit.maxDownloadsFor(500))
        assertEquals(9, HomeLimit.maxDownloadsFor(10))
        assertEquals(9, HomeLimit.maxDownloadsFor(0))
        assertEquals(499, HomeLimit.maxDownloadsFor(10_000))
    }
}
