package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListsTest {
    private fun item(
        n: Int,
        showTitle: String = "Show",
        showArtwork: String? = "https://img.example/show.jpg",
        title: String = "Episode $n",
    ) = EpisodeWithPodcast(
        Episode(
            id = "https://example.com/feed.xml#$n",
            podcastUrl = "https://example.com/feed.xml",
            title = title,
            description = "",
            audioUrl = "https://example.com/$n.mp3",
            imageUrl = null,
            pubDate = n.toLong(),
            durationMs = 60_000L,
        ),
        showTitle,
        showArtwork,
    )

    private fun ids(list: List<EpisodeWithPodcast>) = list.map { it.episode.id }

    @Test
    fun `empty queue keeps every episode in order`() {
        val episodes = listOf(item(3), item(1), item(2))
        assertEquals(episodes, HomeLists.withoutQueued(episodes, emptyList()))
    }

    @Test
    fun `queued episodes are removed and the rest keep their order`() {
        val episodes = listOf(item(5), item(4), item(3), item(2), item(1))
        val result = HomeLists.withoutQueued(episodes, listOf(item(2), item(4)))
        assertEquals(listOf(item(5), item(3), item(1)), result)
    }

    @Test
    fun `order of the queue does not change the order of the result`() {
        val episodes = listOf(item(1), item(2), item(3), item(4))
        val result = HomeLists.withoutQueued(episodes, listOf(item(3), item(1)))
        assertEquals(ids(listOf(item(2), item(4))), ids(result))
    }

    @Test
    fun `matching is by episode id, not by the rest of the item`() {
        val episodes = listOf(item(1), item(2))
        val queuedCopy = item(1, showTitle = "Renamed show", showArtwork = null, title = "Changed title")
        val result = HomeLists.withoutQueued(episodes, listOf(queuedCopy))
        assertEquals(listOf(item(2)), result)
    }

    @Test
    fun `episodes with different ids are kept even if they look alike`() {
        val a = item(1, title = "Same")
        val b = item(2, title = "Same")
        val result = HomeLists.withoutQueued(listOf(a, b), listOf(item(3, title = "Same")))
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun `no episodes gives an empty list`() {
        assertTrue(HomeLists.withoutQueued(emptyList(), listOf(item(1))).isEmpty())
        assertTrue(HomeLists.withoutQueued(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `everything queued gives an empty list`() {
        val episodes = listOf(item(1), item(2), item(3))
        assertTrue(HomeLists.withoutQueued(episodes, listOf(item(3), item(2), item(1))).isEmpty())
    }

    @Test
    fun `queue items that are not in the list do not matter`() {
        val episodes = listOf(item(1), item(2))
        val result = HomeLists.withoutQueued(episodes, listOf(item(7), item(8), item(2)))
        assertEquals(listOf(item(1)), result)
        assertEquals(episodes, HomeLists.withoutQueued(episodes, listOf(item(9))))
    }

    @Test
    fun `duplicates in the list are all removed when that episode is queued`() {
        val episodes = listOf(item(1), item(2), item(1), item(3))
        val result = HomeLists.withoutQueued(episodes, listOf(item(1)))
        assertEquals(listOf(item(2), item(3)), result)
    }

    @Test
    fun `duplicates in the list are kept when that episode is not queued`() {
        val episodes = listOf(item(1), item(2), item(1))
        val result = HomeLists.withoutQueued(episodes, listOf(item(2)))
        assertEquals(listOf(item(1), item(1)), result)
    }
}
