package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Episode
import com.podparar.core.model.ParsedFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** podcast:chapters on items (src/test/resources/feeds/chapters.xml) becomes Episode.chaptersUrl. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FeedParserChaptersTest {
    private val feedUrl = "https://feeds.example.com/chapters"

    private val feed: ParsedFeed by lazy {
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("feeds/chapters.xml")) { "missing fixture" }
            .use { FeedParser.parse(it, feedUrl, 1_730_000_000_000L) }
    }

    private fun episode(guid: String): Episode =
        feed.episodes.firstOrNull { it.id == "$feedUrl#$guid" } ?: throw AssertionError("no episode $guid in ${feed.episodes.map { it.id }}")

    @Test
    fun `all items are parsed`() {
        assertEquals(6, feed.episodes.size)
    }

    @Test
    fun `chapters in the podcastindex namespace`() {
        assertEquals("https://cdn.example.com/ch-new-chapters.json", episode("ch-new").chaptersUrl)
    }

    @Test
    fun `chapters in the older github namespace`() {
        assertEquals("http://cdn.example.com/ch-old-chapters.json", episode("ch-old").chaptersUrl)
    }

    @Test
    fun `item without chapters has no url`() {
        assertNull(episode("ch-none").chaptersUrl)
    }

    @Test
    fun `blank url is null`() {
        assertNull(episode("ch-blank").chaptersUrl)
    }

    @Test
    fun `non http url is null`() {
        assertNull(episode("ch-ftp").chaptersUrl)
    }

    @Test
    fun `element without a url attribute is null`() {
        assertNull(episode("ch-nourl").chaptersUrl)
    }

    @Test
    fun `feeds without chapters leave chaptersUrl null`() {
        val typical = requireNotNull(javaClass.classLoader!!.getResourceAsStream("feeds/typical.xml")) { "missing fixture" }
            .use { FeedParser.parse(it, "https://feeds.example.com/workshop", 1L) }
        assertEquals(true, typical.episodes.all { it.chaptersUrl == null })
    }
}
