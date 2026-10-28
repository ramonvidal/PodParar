package com.podparar.core.model

/** The skip back / skip forward lengths the listener can choose from. */
object SkipLengths {
    val backOptionsMs: List<Long> = listOf(5_000L, 10_000L, 30_000L)
    val forwardOptionsMs: List<Long> = listOf(5_000L, 10_000L, 30_000L)
    const val DEFAULT_BACK_MS: Long = 10_000L
    const val DEFAULT_FORWARD_MS: Long = 30_000L

    /** The nearest back option (ties -> the smaller). */
    fun nearestBack(ms: Long): Long = nearest(backOptionsMs, ms)

    /** The nearest forward option (ties -> the smaller). */
    fun nearestForward(ms: Long): Long = nearest(forwardOptionsMs, ms)

    private fun nearest(options: List<Long>, ms: Long): Long =
        options.sorted().minByOrNull { distance(it, ms) }!!

    // Saturating, so Long.MIN_VALUE / MAX_VALUE inputs can't overflow.
    private fun distance(a: Long, b: Long): Long {
        val d = a - b
        val overflowed = ((a xor b) and (a xor d)) < 0
        return if (overflowed) Long.MAX_VALUE else kotlin.math.abs(d)
    }
}
