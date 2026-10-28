package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 2 constants in PlaybackRules: skip lengths and how often the position is saved. */
class PlaybackConstantsTest {
    @Test
    fun `skip back is ten seconds`() {
        assertEquals(10_000L, PlaybackRules.SKIP_BACK_MS)
    }

    @Test
    fun `skip forward is thirty seconds`() {
        assertEquals(30_000L, PlaybackRules.SKIP_FORWARD_MS)
    }

    @Test
    fun `position is saved every five seconds`() {
        assertEquals(5_000L, PlaybackRules.SAVE_INTERVAL_MS)
    }
}
