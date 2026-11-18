package com.podparar.core.model

import java.time.Instant
import java.time.ZoneId

/** Age rules for episodes on Home's "New episodes" list. Plain Kotlin, testable on the JVM. */
object EpisodeAge {
    /** Epoch ms exactly one calendar month before [nowMs] in [zone] (`ZonedDateTime.minusMonths(1)`). */
    fun cutoffMs(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).minusMonths(1).toInstant().toEpochMilli()

    /**
     * Home "New episodes" rule: a downloaded episode always shows; otherwise it shows only if
     * [pubDateMs] is at or after [cutoffMs]. An unknown pubDate (<= 0) counts as recent, so we never
     * hide what we can't date.
     */
    fun showsInNew(
        pubDateMs: Long,
        downloaded: Boolean,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = downloaded || pubDateMs <= 0 || pubDateMs >= cutoffMs(nowMs, zone)
}
