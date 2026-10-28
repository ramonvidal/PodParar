package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastSpeedTest {
    private val show = Podcast(
        feedUrl = "https://a.example/feed",
        title = "Show",
        author = "Author",
        description = "About",
        artworkUrl = null,
        lastRefreshed = 1_000L,
    )

    @Test
    fun `a podcast has no speed of its own by default`() {
        assertNull(show.speed)
        assertEquals(false, show.autoDownload)
    }

    @Test
    fun `speed can be set and copied`() {
        val fast = show.copy(speed = 1.5f)
        assertEquals(1.5f, fast.speed!!, 0.0001f)
        assertNull(fast.copy(speed = null).speed)
        assertEquals(show.copy(speed = 1.5f), fast)
    }
}
