package com.podparar.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short labels for durations and dates on episode rows. */
object Formats {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    /** "" if ms <= 0. Minutes = (ms + 30_000) / 60_000, at least 1. >= 60 min: "1 h" or "1 h 5 min"; else "45 min". */
    fun durationLabel(ms: Long): String {
        if (ms <= 0) return ""
        val minutes = ((ms + 30_000) / 60_000).coerceAtLeast(1)
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) "$hours h" else "$hours h $rest min"
    }

    /**
     * "" if pubDate <= 0. Compares local dates in [zone]: same day "Today", the day before "Yesterday",
     * same year "12 Mar", otherwise "12 Mar 2023" (patterns "d MMM" / "d MMM yyyy", Locale.US).
     */
    fun pubDateLabel(pubDate: Long, now: Long, zone: ZoneId): String {
        if (pubDate <= 0) return ""
        val date = Instant.ofEpochMilli(pubDate).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> DAY_MONTH.format(date)
            else -> DAY_MONTH_YEAR.format(date)
        }
    }

    /** Player clock: "0:05", "12:34", "1:02:03". Negative -> "0:00". Seconds are floored. */
    fun timeLabel(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val ss = seconds.toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }

    /** "-12:34": timeLabel(durationMs - positionMs) with a leading minus; "" if durationMs <= 0. Never below "-0:00". */
    fun remainingLabel(positionMs: Long, durationMs: Long): String {
        if (durationMs <= 0) return ""
        val remaining = if (positionMs <= 0) durationMs else (durationMs - positionMs).coerceAtLeast(0L)
        return "-" + timeLabel(remaining)
    }

    /**
     * "1×", "1.5×", "0.8×", "2.5×": no trailing zeros, at most 2 decimals (half up), the × sign (U+00D7).
     * A NaN or infinite speed shows as [PlaybackSpeeds.DEFAULT].
     */
    fun speedLabel(speed: Float): String {
        val value = if (speed.isNaN() || speed.isInfinite()) PlaybackSpeeds.DEFAULT else speed
        // Float.toString gives the shortest decimal (1.2f -> "1.2"), so no binary noise like 1.2000000476.
        val text = BigDecimal(value.toString())
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return (if (text == "-0") "0" else text) + "\u00D7"
    }
}
