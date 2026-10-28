package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UpdatesTest {
    private val update = AppUpdate(
        buildNumber = 25,
        versionName = "0.25",
        downloadUrl = "https://github.com/ramonvidal/PodParar/releases/download/v0.25/PodParar.apk",
        sizeBytes = 12_345_678L,
    )
    private val now = Instant.parse("2026-10-28T10:00:00Z")

    @Test
    fun `checks are every six hours`() {
        assertEquals(Duration.ofHours(6), Updates.CHECK_EVERY)
    }

    @Test
    fun `release tags give the build number`() {
        assertEquals(25, Updates.buildNumber("v0.25"))
        assertEquals(1, Updates.buildNumber("v0.1"))
        assertEquals(123, Updates.buildNumber("v0.123"))
    }

    @Test
    fun `whitespace around the tag is ignored`() {
        assertEquals(25, Updates.buildNumber("  v0.25 "))
        assertEquals(25, Updates.buildNumber("v0.25\n"))
    }

    @Test
    fun `tags in another shape give no build number`() {
        assertNull(Updates.buildNumber("v1.2"))
        assertNull(Updates.buildNumber("0.5"))
        assertNull(Updates.buildNumber("v0.25.1"))
        assertNull(Updates.buildNumber("v0."))
        assertNull(Updates.buildNumber("v0.abc"))
        assertNull(Updates.buildNumber("latest"))
        assertNull(Updates.buildNumber(""))
        assertNull(Updates.buildNumber("garbage"))
    }

    @Test
    fun `only a higher build number is newer`() {
        assertTrue(Updates.isNewer(update, installedBuild = 24L))
        assertTrue(Updates.isNewer(update, installedBuild = 1L))
        assertFalse(Updates.isNewer(update, installedBuild = 25L))
        assertFalse(Updates.isNewer(update, installedBuild = 26L))
    }

    @Test
    fun `a check is due when there has never been one`() {
        assertTrue(Updates.isCheckDue(null, now))
    }

    @Test
    fun `a check is not due just under six hours after the last`() {
        assertFalse(Updates.isCheckDue(now, now))
        assertFalse(Updates.isCheckDue(now.minus(Duration.ofHours(3)), now))
        assertFalse(Updates.isCheckDue(now.minus(Duration.ofHours(6)).plusSeconds(1), now))
    }

    @Test
    fun `a check is due six hours or more after the last`() {
        assertTrue(Updates.isCheckDue(now.minus(Duration.ofHours(6)), now))
        assertTrue(Updates.isCheckDue(now.minus(Duration.ofHours(25)), now))
    }

    @Test
    fun `newer builds are offered, the installed one or older is not`() {
        assertTrue(Updates.shouldOffer(update, installedBuild = 24L, dismissedBuild = null))
        assertFalse(Updates.shouldOffer(update, installedBuild = 25L, dismissedBuild = null))
        assertFalse(Updates.shouldOffer(update, installedBuild = 30L, dismissedBuild = null))
    }

    @Test
    fun `Later hides that build until a newer one comes out`() {
        assertFalse(Updates.shouldOffer(update, installedBuild = 24L, dismissedBuild = 25))
        assertTrue(Updates.shouldOffer(update.copy(buildNumber = 26, versionName = "0.26"), installedBuild = 24L, dismissedBuild = 25))
        // Dismissing an older build doesn't hide a newer one.
        assertTrue(Updates.shouldOffer(update, installedBuild = 20L, dismissedBuild = 22))
    }
}
