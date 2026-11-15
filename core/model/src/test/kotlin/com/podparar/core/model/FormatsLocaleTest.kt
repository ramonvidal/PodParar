package com.podparar.core.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsLocaleTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val utc = ZoneOffset.UTC
    private val ptBr = Locale.forLanguageTag("pt-BR")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `defaults keep the English output`() {
        val now = at(madrid, 2026, 10, 28)
        assertEquals("Today", Formats.pubDateLabel(now, now, madrid))
        assertEquals("Yesterday", Formats.pubDateLabel(at(madrid, 2026, 10, 27), now, madrid))
        assertEquals("12 Mar", Formats.pubDateLabel(at(madrid, 2026, 3, 12), now, madrid))
        assertEquals("12 Mar 2023", Formats.pubDateLabel(at(madrid, 2023, 3, 12), now, madrid))
        assertEquals("", Formats.pubDateLabel(0L, now, madrid))
    }

    @Test
    fun `the given words replace Today and Yesterday`() {
        val now = at(madrid, 2026, 10, 28)
        assertEquals(
            "Hoje",
            Formats.pubDateLabel(now, now, madrid, locale = ptBr, today = "Hoje", yesterday = "Ontem"),
        )
        assertEquals(
            "Ontem",
            Formats.pubDateLabel(at(madrid, 2026, 10, 27), now, madrid, locale = ptBr, today = "Hoje", yesterday = "Ontem"),
        )
    }

    @Test
    fun `the month text follows the locale`() {
        val now = at(madrid, 2026, 10, 28)
        val date = LocalDateTime.of(2026, 3, 12, 12, 0)
        assertEquals(
            DateTimeFormatter.ofPattern("d MMM", ptBr).format(date),
            Formats.pubDateLabel(at(madrid, 2026, 3, 12), now, madrid, locale = ptBr, today = "Hoje", yesterday = "Ontem"),
        )
        val old = LocalDateTime.of(2023, 3, 12, 12, 0)
        assertEquals(
            DateTimeFormatter.ofPattern("d MMM yyyy", ptBr).format(old),
            Formats.pubDateLabel(at(madrid, 2023, 3, 12), now, madrid, locale = ptBr, today = "Hoje", yesterday = "Ontem"),
        )
    }

    @Test
    fun `zone boundaries are unchanged with a locale`() {
        val now = at(madrid, 2026, 3, 12, 0, 30)
        val pub = at(madrid, 2026, 3, 11, 23, 30)
        assertEquals("Ontem", Formats.pubDateLabel(pub, now, madrid, ptBr, "Hoje", "Ontem"))
        assertEquals("Hoje", Formats.pubDateLabel(pub, now, utc, ptBr, "Hoje", "Ontem"))
    }
}
