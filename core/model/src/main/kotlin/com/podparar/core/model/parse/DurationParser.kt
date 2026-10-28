package com.podparar.core.model.parse

import kotlin.math.roundToLong

/** `<itunes:duration>` in its many shapes. */
object DurationParser {
    private val NUMBER = Regex("""\d+(?:\.\d+)?""")

    /**
     * "HH:MM:SS", "H:MM:SS", "MM:SS" or plain seconds ("1834", "1834.5"), in milliseconds.
     * Null, blank, negative or garbage gives 0; it never throws.
     */
    fun parseMs(raw: String?): Long {
        val parts = raw?.trim()?.takeIf { it.isNotEmpty() }?.split(':')?.map { it.trim() } ?: return 0L
        if (parts.size > 3 || parts.any { !NUMBER.matches(it) }) return 0L
        // Hours and minutes are whole numbers; only the seconds may carry a fraction.
        if (parts.dropLast(1).any { it.contains('.') }) return 0L
        return try {
            val seconds = (parts.last().toDouble() * 1000).roundToLong()
            val minutes = parts.dropLast(1).fold(0L) { total, part -> Math.addExact(Math.multiplyExact(total, 60L), part.toLong()) }
            Math.addExact(Math.multiplyExact(minutes, 60_000L), seconds).takeIf { it in 0 until Long.MAX_VALUE } ?: 0L
        } catch (e: ArithmeticException) {
            0L
        } catch (e: NumberFormatException) {
            0L
        }
    }
}
