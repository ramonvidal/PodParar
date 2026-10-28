package com.podparar.core.model

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
}
