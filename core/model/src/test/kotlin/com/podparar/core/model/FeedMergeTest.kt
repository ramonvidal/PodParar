package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedMergeTest {
    private val feed = "https://example.com/feed.xml"
    private val day = 86_400_000L

    private fun ep(
        guid: String,
        pubDate: Long = 0L,
        title: String = "Episode $guid",
        description: String = "Notes $guid",
        audioUrl: String = "https://cdn.example.com/$guid.mp3",
        imageUrl: String? = null,
        durationMs: Long = 1_800_000L,
        positionMs: Long = 0L,
        played: Boolean = false,
        lastPlayedAt: Long = 0L,
    ) = Episode(
        id = "$feed#$guid",
        podcastUrl = feed,
        title = title,
        description = description,
        audioUrl = audioUrl,
        imageUrl = imageUrl,
        pubDate = pubDate,
        durationMs = durationMs,
        positionMs = positionMs,
        played = played,
        lastPlayedAt = lastPlayedAt,
    )

    private fun ids(list: List<Episode>) = list.map { it.id.substringAfter('#') }

    @Test
    fun `nothing stored gives the fresh episodes as parsed`() {
        val fresh = listOf(ep("b", pubDate = 2 * day), ep("a", pubDate = day))
        assertEquals(fresh, FeedMerge.merge(emptyList(), fresh))
    }

    @Test
    fun `both empty gives empty`() {
        assertEquals(emptyList<Episode>(), FeedMerge.merge(emptyList(), emptyList()))
    }

    @Test
    fun `new episodes are added as parsed next to stored ones`() {
        val stored = listOf(ep("a", pubDate = day, positionMs = 60_000L))
        val newOne = ep("b", pubDate = 2 * day, durationMs = 999_000L, imageUrl = "https://img/b.jpg")
        val result = FeedMerge.merge(stored, listOf(newOne, ep("a", pubDate = day)))
        assertEquals(listOf("b", "a"), ids(result))
        assertEquals(newOne, result[0])
    }

    @Test
    fun `known episode keeps position, played and last played`() {
        val stored = listOf(ep("a", pubDate = day, positionMs = 1_234_000L, played = true, lastPlayedAt = 5_000L))
        val result = FeedMerge.merge(stored, listOf(ep("a", pubDate = day)))
        assertEquals(1, result.size)
        assertEquals(1_234_000L, result[0].positionMs)
        assertTrue(result[0].played)
        assertEquals(5_000L, result[0].lastPlayedAt)
    }

    @Test
    fun `known episode keeps progress even when the fresh copy says played false and position 0`() {
        val stored = listOf(ep("a", positionMs = 42_000L, played = false, lastPlayedAt = 7L))
        val fresh = listOf(ep("a", positionMs = 0L, played = false, lastPlayedAt = 0L))
        val merged = FeedMerge.merge(stored, fresh).single()
        assertEquals(42_000L, merged.positionMs)
        assertEquals(7L, merged.lastPlayedAt)
    }

    @Test
    fun `known episode takes fresh metadata`() {
        val stored = listOf(
            ep(
                "a",
                pubDate = day,
                title = "Old title",
                description = "Old notes",
                audioUrl = "https://old/a.mp3",
                imageUrl = "https://old/a.jpg",
                durationMs = 1_000_000L,
                positionMs = 300_000L,
            ),
        )
        val fresh = listOf(
            ep(
                "a",
                pubDate = 3 * day,
                title = "New title",
                description = "New notes",
                audioUrl = "https://new/a.mp3",
                imageUrl = "https://new/a.jpg",
                durationMs = 2_000_000L,
            ),
        )
        val merged = FeedMerge.merge(stored, fresh).single()
        assertEquals(
            ep(
                "a",
                pubDate = 3 * day,
                title = "New title",
                description = "New notes",
                audioUrl = "https://new/a.mp3",
                imageUrl = "https://new/a.jpg",
                durationMs = 2_000_000L,
                positionMs = 300_000L,
            ),
            merged,
        )
    }

    @Test
    fun `fresh image null replaces the stored image`() {
        val stored = listOf(ep("a", imageUrl = "https://old/a.jpg"))
        val merged = FeedMerge.merge(stored, listOf(ep("a", imageUrl = null))).single()
        assertEquals(null, merged.imageUrl)
    }

    @Test
    fun `fresh duration 0 keeps the stored duration`() {
        val stored = listOf(ep("a", durationMs = 1_800_000L, positionMs = 10_000L))
        val merged = FeedMerge.merge(stored, listOf(ep("a", durationMs = 0L, title = "Renamed"))).single()
        assertEquals(1_800_000L, merged.durationMs)
        assertEquals("Renamed", merged.title)
    }

    @Test
    fun `fresh non zero duration replaces the stored one`() {
        val stored = listOf(ep("a", durationMs = 1_800_000L))
        val merged = FeedMerge.merge(stored, listOf(ep("a", durationMs = 1L))).single()
        assertEquals(1L, merged.durationMs)
    }

    @Test
    fun `stored episodes missing from the fresh feed are kept unchanged`() {
        val old = ep("old", pubDate = day, positionMs = 99_000L, played = true, lastPlayedAt = 3L, title = "Gone from feed")
        val result = FeedMerge.merge(listOf(old), listOf(ep("new", pubDate = 2 * day)))
        assertEquals(listOf("new", "old"), ids(result))
        assertEquals(old, result[1])
    }

    @Test
    fun `empty fresh feed keeps everything stored`() {
        val stored = listOf(ep("b", pubDate = 2 * day, positionMs = 5L), ep("a", pubDate = day, played = true))
        assertEquals(stored, FeedMerge.merge(stored, emptyList()))
    }

    @Test
    fun `duplicate ids in the fresh feed keep the first`() {
        val first = ep("a", pubDate = day, title = "First")
        val second = ep("a", pubDate = 2 * day, title = "Second")
        val result = FeedMerge.merge(emptyList(), listOf(first, second))
        assertEquals(listOf(first), result)
    }

    @Test
    fun `duplicate ids in the fresh feed keep the first when merging onto stored`() {
        val stored = listOf(ep("a", positionMs = 77_000L))
        val result = FeedMerge.merge(stored, listOf(ep("a", title = "First"), ep("a", title = "Second")))
        val merged = result.single()
        assertEquals("First", merged.title)
        assertEquals(77_000L, merged.positionMs)
    }

    @Test
    fun `no id appears twice in the result`() {
        val stored = listOf(ep("a", pubDate = day), ep("b", pubDate = 2 * day))
        val fresh = listOf(ep("b", pubDate = 2 * day), ep("c", pubDate = 3 * day), ep("a", pubDate = day))
        val result = FeedMerge.merge(stored, fresh)
        assertEquals(listOf("c", "b", "a"), ids(result))
    }

    @Test
    fun `result is newest first by pub date`() {
        val fresh = listOf(ep("mid", pubDate = 2 * day), ep("old", pubDate = day), ep("new", pubDate = 3 * day))
        val stored = listOf(ep("oldest", pubDate = 0L), ep("newest", pubDate = 10 * day))
        assertEquals(listOf("newest", "new", "mid", "old", "oldest"), ids(FeedMerge.merge(stored, fresh)))
    }

    @Test
    fun `sort uses the fresh pub date of known episodes`() {
        val stored = listOf(ep("a", pubDate = 5 * day), ep("b", pubDate = day))
        val fresh = listOf(ep("a", pubDate = 0L), ep("b", pubDate = day))
        assertEquals(listOf("b", "a"), ids(FeedMerge.merge(stored, fresh)))
    }

    @Test
    fun `ties keep fresh feed order`() {
        val fresh = listOf(ep("x", pubDate = day), ep("y", pubDate = day), ep("z", pubDate = day))
        assertEquals(listOf("x", "y", "z"), ids(FeedMerge.merge(emptyList(), fresh)))
        val reversed = listOf(ep("z", pubDate = day), ep("y", pubDate = day), ep("x", pubDate = day))
        assertEquals(listOf("z", "y", "x"), ids(FeedMerge.merge(emptyList(), reversed)))
    }

    @Test
    fun `ties put fresh episodes before stored only ones, which keep stored order`() {
        val stored = listOf(ep("s2", pubDate = day), ep("s1", pubDate = day))
        val fresh = listOf(ep("f1", pubDate = day), ep("f2", pubDate = day))
        assertEquals(listOf("f1", "f2", "s2", "s1"), ids(FeedMerge.merge(stored, fresh)))
    }

    @Test
    fun `known episodes tie in fresh feed order, not stored order`() {
        val stored = listOf(ep("a", pubDate = day), ep("b", pubDate = day))
        val fresh = listOf(ep("b", pubDate = day), ep("a", pubDate = day))
        assertEquals(listOf("b", "a"), ids(FeedMerge.merge(stored, fresh)))
    }

    @Test
    fun `unknown pub dates sort last`() {
        val fresh = listOf(ep("undated", pubDate = 0L), ep("dated", pubDate = day))
        assertEquals(listOf("dated", "undated"), ids(FeedMerge.merge(emptyList(), fresh)))
    }
}
