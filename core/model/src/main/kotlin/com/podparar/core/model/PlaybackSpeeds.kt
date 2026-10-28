package com.podparar.core.model

import kotlin.math.abs

/** The playback speeds the player offers, and how to step through them. */
object PlaybackSpeeds {
    val options: List<Float> = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f, 2.5f, 3.0f)
    const val DEFAULT: Float = 1.0f

    private const val MIN: Float = 0.8f
    private const val MAX: Float = 3.0f

    /** Speeds this close count as the same option, so float noise (1.2000001) doesn't skip one. */
    private const val TOLERANCE: Float = 0.001f

    /**
     * The next option after [current] (wrapping to the first); a value not in [options] goes to
     * the first option above it, or the first.
     */
    fun next(current: Float): Float {
        val index = options.indexOfFirst { abs(it - current) < TOLERANCE }
        if (index >= 0) return options[(index + 1) % options.size]
        return options.firstOrNull { it > current } ?: options.first()
    }

    /** Clamped to 0.8..3.0; NaN -> DEFAULT. */
    fun clamp(speed: Float): Float =
        if (speed.isNaN()) DEFAULT else speed.coerceIn(MIN, MAX)
}
