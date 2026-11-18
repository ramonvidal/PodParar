package com.podparar.core.model

/** Cap on how many downloads may be active (queued or running) at once. */
object DownloadLimit {
    /** Smallest allowed limit. */
    const val MIN = 1

    /** Largest allowed limit. */
    const val MAX = 200

    /** Limit used when the user hasn't chosen one. */
    const val DEFAULT = 20

    /** Choices offered in settings. */
    val OPTIONS: List<Int> = listOf(5, 10, 20, 30, 50, 100, 200)

    /** Clamps [value] to [MIN]..[MAX]. */
    fun coerce(value: Int): Int = value.coerceIn(MIN, MAX)

    /** The [OPTIONS] that fit under the given [homeLimit] (strictly lower than it, see [HomeLimit]). */
    fun optionsFor(homeLimit: Int): List<Int> = OPTIONS.filter { it <= HomeLimit.maxDownloadsFor(homeLimit) }

    /**
     * Clamps [value] to [MIN]..[MAX] and then below the given [homeLimit]
     * ([HomeLimit.maxDownloadsFor]), never going under [MIN]. This is the one place the
     * "downloads < Home limit" invariant lives.
     */
    fun coerceFor(value: Int, homeLimit: Int): Int =
        coerce(value).coerceAtMost(HomeLimit.maxDownloadsFor(homeLimit)).coerceAtLeast(MIN)

    /** True when [active] downloads already meet or exceed the (coerced) [limit]. */
    fun isReached(active: Int, limit: Int): Boolean = active >= coerce(limit)

    /** How many more downloads fit under the (coerced) [limit], never negative. */
    fun slotsLeft(active: Int, limit: Int): Int = (coerce(limit) - active).coerceAtLeast(0)
}
