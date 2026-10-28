package com.podparar.core.model.parse

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Feed dates (`<pubDate>`, `<updated>`) to epoch ms. */
object DateParser {
    /** RFC 822/1123 once the weekday is gone and the zone is a "+hhmm" offset: "2 Oct 2024 10:00[:00] +0000". */
    private val RFC_822: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d [MMMM][MMM] [yyyy][yy] H:mm[:ss] xx")
        .toFormatter(Locale.US)

    /** Named zones feeds actually use; US ones as fixed offsets, since that's what RFC 822 means by them. */
    private val ZONES = mapOf(
        "GMT" to "+0000", "UT" to "+0000", "UTC" to "+0000", "Z" to "+0000",
        "EST" to "-0500", "EDT" to "-0400",
        "CST" to "-0600", "CDT" to "-0500",
        "MST" to "-0700", "MDT" to "-0600",
        "PST" to "-0800", "PDT" to "-0700",
    )

    private val WEEKDAY = Regex("""^[A-Za-z]+\.?,?\s+(?=\d)""")
    private val OFFSET = Regex("""^(?:GMT|UTC|UT)?([+-])(\d{1,2}):?(\d{2})?$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    /**
     * RFC 822/1123 ("Wed, 02 Oct 2024 10:00:00 +0000", weekday and seconds optional, named or numeric zone)
     * or ISO 8601 ("2024-10-02T10:00:00Z", with offset or fractional seconds; "2024-10-02" is midnight UTC).
     * Null, blank or unparseable gives 0; it never throws.
     */
    fun parseEpochMs(raw: String?): Long {
        val text = raw?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() } ?: return 0L
        return try {
            (if (text.first().isDigit() && text.length >= 10 && text[4] == '-') parseIso(text) else parseRfc822(text)) ?: 0L
        } catch (e: DateTimeException) {
            0L
        } catch (e: ArithmeticException) {
            0L
        }
    }

    private fun parseIso(text: String): Long? {
        val iso = text.replace(' ', 'T')
        if (iso.length == 10) return LocalDate.parse(iso).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return try {
            OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        } catch (e: DateTimeException) {
            // No zone given: UTC is the only sensible guess.
            LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
    }

    private fun parseRfc822(text: String): Long? {
        val tokens = text.replace(WEEKDAY, "").split(' ').toMutableList()
        // "d MMM yyyy H:mm[:ss]" is four tokens; a fifth is the zone, and none means UTC.
        if (tokens.size !in 4..5) return null
        tokens[2] = tokens[2].removeSuffix(",")
        val zone = tokens.getOrNull(4)?.let(::toOffset) ?: if (tokens.size == 4) "+0000" else return null
        val normalized = tokens.take(4).joinToString(" ") + " " + zone
        return OffsetDateTime.parse(normalized, RFC_822).toInstant().toEpochMilli()
    }

    /** "EST", "GMT", "+0530", "+05:30", "GMT+2" to "+hhmm"; null if it's none of those. */
    private fun toOffset(zone: String): String? {
        ZONES[zone.uppercase(Locale.US)]?.let { return it }
        val match = OFFSET.matchEntire(zone) ?: return null
        val (sign, hours, minutes) = match.destructured
        return sign + hours.padStart(2, '0') + minutes.ifEmpty { "00" }
    }
}
