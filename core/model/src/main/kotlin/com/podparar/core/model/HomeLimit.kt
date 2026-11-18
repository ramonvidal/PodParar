package com.podparar.core.model

/**
 * Cap on how many episodes Home's "New episodes" list shows, chosen in Settings.
 *
 * The number of downloaded episodes must always stay lower than this cap; see [maxDownloadsFor].
 */
object HomeLimit {
    /** Smallest allowed limit. */
    const val MIN = 10

    /** Largest allowed limit. */
    const val MAX = 500

    /** Limit used when the user hasn't chosen one. */
    const val DEFAULT = 50

    /** Choices offered in settings. */
    val OPTIONS: List<Int> = listOf(30, 50, 100, 200, 500)

    /** Clamps [value] to [MIN]..[MAX]. */
    fun coerce(value: Int): Int = value.coerceIn(MIN, MAX)

    /** The most downloads allowed under this Home limit: always strictly lower than it. */
    fun maxDownloadsFor(homeLimit: Int): Int = coerce(homeLimit) - 1
}
