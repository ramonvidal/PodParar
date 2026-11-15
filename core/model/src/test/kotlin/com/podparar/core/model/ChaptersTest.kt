package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChaptersTest {
    private fun ch(startMs: Long, title: String = "Chapter at $startMs") = Chapter(startMs, title)

    // Starts: 0, 60 s, 120 s, 300 s.
    private val list = listOf(ch(0), ch(60_000), ch(120_000), ch(300_000))

    // --- normalize ---

    @Test
    fun `normalize of empty is empty`() {
        assertEquals(emptyList<Chapter>(), Chapters.normalize(emptyList()))
    }

    @Test
    fun `normalize drops blank titles`() {
        val result = Chapters.normalize(listOf(ch(0, "Intro"), ch(1_000, ""), ch(2_000, "   "), ch(3_000, "Main")))
        assertEquals(listOf("Intro", "Main"), result.map { it.title })
    }

    @Test
    fun `normalize drops negative starts but keeps zero`() {
        val result = Chapters.normalize(listOf(ch(-1, "Before"), ch(0, "Zero"), ch(-5_000, "Way before"), ch(10, "Ten")))
        assertEquals(listOf(0L, 10L), result.map { it.startMs })
    }

    @Test
    fun `normalize sorts by start`() {
        val result = Chapters.normalize(listOf(ch(300_000), ch(0), ch(120_000), ch(60_000)))
        assertEquals(listOf(0L, 60_000L, 120_000L, 300_000L), result.map { it.startMs })
    }

    @Test
    fun `normalize keeps the first of equal starts`() {
        val result = Chapters.normalize(listOf(ch(0, "A"), ch(5_000, "First"), ch(5_000, "Second"), ch(9_000, "Z")))
        assertEquals(listOf("A", "First", "Z"), result.map { it.title })
    }

    @Test
    fun `normalize keeps image and url`() {
        val full = Chapter(1_000, "With extras", imageUrl = "https://img.example/1.jpg", url = "https://example.com/1")
        assertEquals(listOf(full), Chapters.normalize(listOf(full)))
    }

    @Test
    fun `normalize of clean input is unchanged`() {
        assertEquals(list, Chapters.normalize(list))
    }

    // --- indexAt ---

    @Test
    fun `indexAt of empty is null`() {
        assertNull(Chapters.indexAt(emptyList(), 1_000))
    }

    @Test
    fun `indexAt before the first chapter is null`() {
        val late = listOf(ch(10_000), ch(20_000))
        assertNull(Chapters.indexAt(late, 9_999))
        assertNull(Chapters.indexAt(late, 0))
    }

    @Test
    fun `indexAt at an exact start is that chapter`() {
        assertEquals(0, Chapters.indexAt(list, 0))
        assertEquals(1, Chapters.indexAt(list, 60_000))
        assertEquals(3, Chapters.indexAt(list, 300_000))
    }

    @Test
    fun `indexAt one millisecond before a start is the previous chapter`() {
        assertEquals(0, Chapters.indexAt(list, 59_999))
        assertEquals(2, Chapters.indexAt(list, 299_999))
    }

    @Test
    fun `indexAt between chapters`() {
        assertEquals(1, Chapters.indexAt(list, 90_000))
        assertEquals(2, Chapters.indexAt(list, 200_000))
    }

    @Test
    fun `indexAt after the last chapter is the last`() {
        assertEquals(3, Chapters.indexAt(list, 10_000_000))
    }

    // --- nextStartMs ---

    @Test
    fun `nextStartMs of empty is null`() {
        assertNull(Chapters.nextStartMs(emptyList(), 5_000))
    }

    @Test
    fun `nextStartMs inside a chapter is the following start`() {
        assertEquals(60_000L, Chapters.nextStartMs(list, 0))
        assertEquals(120_000L, Chapters.nextStartMs(list, 60_000))
        assertEquals(300_000L, Chapters.nextStartMs(list, 250_000))
    }

    @Test
    fun `nextStartMs in the last chapter is null`() {
        assertNull(Chapters.nextStartMs(list, 300_000))
        assertNull(Chapters.nextStartMs(list, 9_000_000))
    }

    @Test
    fun `nextStartMs before the first chapter is the first chapter`() {
        val late = listOf(ch(10_000), ch(20_000))
        assertEquals(10_000L, Chapters.nextStartMs(late, 0))
        assertEquals(10_000L, Chapters.nextStartMs(late, 9_999))
    }

    @Test
    fun `nextStartMs with a single chapter`() {
        assertNull(Chapters.nextStartMs(listOf(ch(0)), 1_000))
    }

    // --- previousStartMs ---

    @Test
    fun `previousStartMs of empty is null`() {
        assertNull(Chapters.previousStartMs(emptyList(), 5_000))
    }

    @Test
    fun `previousStartMs more than 3 s into a chapter restarts it`() {
        assertEquals(60_000L, Chapters.previousStartMs(list, 63_001))
        assertEquals(60_000L, Chapters.previousStartMs(list, 100_000))
        assertEquals(300_000L, Chapters.previousStartMs(list, 400_000))
    }

    @Test
    fun `previousStartMs exactly 3 s into a chapter goes to the previous one`() {
        assertEquals(0L, Chapters.previousStartMs(list, 63_000))
    }

    @Test
    fun `previousStartMs within 3 s of a chapter start goes to the previous chapter`() {
        assertEquals(60_000L, Chapters.previousStartMs(list, 121_000))
        assertEquals(120_000L, Chapters.previousStartMs(list, 300_000))
        assertEquals(0L, Chapters.previousStartMs(list, 60_000))
    }

    @Test
    fun `previousStartMs at the first chapter within 3 s is zero`() {
        assertEquals(0L, Chapters.previousStartMs(list, 0))
        assertEquals(0L, Chapters.previousStartMs(list, 2_999))
        assertEquals(0L, Chapters.previousStartMs(list, 3_000))
    }

    @Test
    fun `previousStartMs more than 3 s into the first chapter restarts it`() {
        assertEquals(0L, Chapters.previousStartMs(list, 3_001))
        assertEquals(0L, Chapters.previousStartMs(list, 59_000))
    }

    @Test
    fun `previousStartMs more than 3 s into a first chapter that starts late goes to its start`() {
        val late = listOf(ch(10_000), ch(20_000))
        assertEquals(10_000L, Chapters.previousStartMs(late, 15_000))
    }

    @Test
    fun `previousStartMs before the first chapter is zero`() {
        val late = listOf(ch(10_000), ch(20_000))
        assertEquals(0L, Chapters.previousStartMs(late, 5_000))
        assertEquals(0L, Chapters.previousStartMs(late, 0))
    }

    @Test
    fun `previousStartMs just inside the first late chapter is zero`() {
        val late = listOf(ch(10_000), ch(20_000))
        assertEquals(0L, Chapters.previousStartMs(late, 11_000))
    }

    @Test
    fun `Episode chaptersUrl defaults to null`() {
        val e = Episode(
            id = "f#1",
            podcastUrl = "f",
            title = "t",
            description = "",
            audioUrl = "https://a/1.mp3",
            imageUrl = null,
            pubDate = 0L,
            durationMs = 0L,
        )
        assertNull(e.chaptersUrl)
    }
}
