package com.podparar.core.model

import kotlin.math.max

/** A running sleep timer. Exactly one of [endsAt] (epoch ms) or [endOfEpisode] is meaningful. */
data class SleepTimer(val endsAt: Long?, val endOfEpisode: Boolean) {
    /** ms left (never negative); null for end of episode. */
    fun remainingMs(now: Long): Long? {
        if (endOfEpisode) return null
        val end = endsAt ?: return null
        return max(0L, end - now)
    }
}

/** Sleep timer options and timing rules: when it ends, how it fades, how it extends. */
object SleepTimers {
    val minuteOptions: List<Int> = listOf(15, 30, 45, 60)
    const val EXTEND_MINUTES: Int = 5
    const val FADE_MS: Long = 10_000L

    private const val MINUTE_MS: Long = 60_000L

    /** A timed timer ending [minutes] after [now]. */
    fun after(minutes: Int, now: Long): SleepTimer =
        SleepTimer(endsAt = now + minutes * MINUTE_MS, endOfEpisode = false)

    /** Timed: endsAt + EXTEND_MINUTES; end-of-episode: unchanged. */
    fun extend(timer: SleepTimer): SleepTimer {
        if (timer.endOfEpisode) return timer
        val end = timer.endsAt ?: return timer
        return timer.copy(endsAt = end + EXTEND_MINUTES * MINUTE_MS)
    }

    /** Volume factor for the fade: 1 when more than FADE_MS remain, then linear down to 0 at the end; 1 for end of episode. */
    fun volumeAt(timer: SleepTimer, now: Long): Float {
        val remaining = timer.remainingMs(now) ?: return 1f
        if (remaining >= FADE_MS) return 1f
        return (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    /** "23 min" (ceil minutes, at least 1) for timed timers, "End of episode" otherwise. */
    fun label(timer: SleepTimer, now: Long): String {
        val remaining = timer.remainingMs(now) ?: return "End of episode"
        val minutes = max(1L, (remaining + MINUTE_MS - 1) / MINUTE_MS)
        return "$minutes min"
    }
}
