package com.podparar.core.model

import java.time.Duration
import java.time.Instant

/** A newer build published on the release page. */
data class AppUpdate(val buildNumber: Int, val versionName: String, val downloadUrl: String, val sizeBytes: Long)

object Updates {
    /** How often the app asks for a new build on its own; Settings can also check right away. */
    val CHECK_EVERY: Duration = Duration.ofHours(6)

    /** Releases are tagged "v0.<build>"; anything else isn't one of ours. */
    fun buildNumber(tag: String): Int? = Regex("""^v0\.(\d+)$""").matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun isNewer(update: AppUpdate, installedBuild: Long): Boolean = update.buildNumber > installedBuild

    fun isCheckDue(lastCheck: Instant?, now: Instant): Boolean = lastCheck == null || Duration.between(lastCheck, now) >= CHECK_EVERY

    /** A "Later" applies to that version only; the next one asks again. */
    fun shouldOffer(update: AppUpdate, installedBuild: Long, dismissedBuild: Int?): Boolean =
        isNewer(update, installedBuild) && update.buildNumber != dismissedBuild
}
