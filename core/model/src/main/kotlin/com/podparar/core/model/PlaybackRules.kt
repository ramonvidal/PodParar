package com.podparar.core.model

object PlaybackRules {
    /** With less than this left, an episode counts as finished: outros and ads don't need hearing. */
    const val PLAYED_THRESHOLD_MS: Long = 30_000L

    /** True if playback [ended], or the duration is known and fewer than 30 s remain. */
    fun isPlayed(positionMs: Long, durationMs: Long, ended: Boolean = false): Boolean =
        ended || (durationMs > 0 && durationMs - positionMs < PLAYED_THRESHOLD_MS)

    /** Where to start: from the top if it's played or nearly over, otherwise where the listener left off. */
    fun resumePositionMs(episode: Episode): Long = when {
        episode.played -> 0L
        episode.durationMs > 0 && episode.durationMs - episode.positionMs < PLAYED_THRESHOLD_MS -> 0L
        else -> episode.positionMs.coerceAtLeast(0L)
    }

    /** How far along, 0..1; 0 when the duration is unknown. */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
}
