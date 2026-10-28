package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Episode
import com.podparar.core.model.LoadError
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.PodcastException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

/** FeedParser against fixture feeds modelled on real ones (src/test/resources/feeds). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FeedParserTest {
    private val now = 1_730_000_000_000L

    private fun fixture(name: String): InputStream =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("feeds/$name")) { "missing fixture feeds/$name" }

    private fun parse(name: String, feedUrl: String): ParsedFeed =
        fixture(name).use { FeedParser.parse(it, feedUrl, now) }

    private fun utc(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun parseError(input: InputStream): LoadError = try {
        input.use { FeedParser.parse(it, "https://example.com/feed", now) }
        fail("expected PodcastException(NOT_A_FEED)")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    // --- A typical iTunes feed ---

    private val typicalUrl = "https://feeds.example.com/workshop"
    private val typical by lazy { parse("typical.xml", typicalUrl) }

    @Test
    fun `typical feed - podcast fields`() {
        val podcast = typical.podcast
        assertEquals(typicalUrl, podcast.feedUrl)
        assertEquals("The Workshop Hour", podcast.title)
        assertEquals("Workshop Media", podcast.author)
        assertEquals("<p>Weekly conversations about <b>making things</b> by hand.</p>", podcast.description.trim())
        assertEquals(now, podcast.lastRefreshed)
    }

    @Test
    fun `typical feed - itunes image wins over channel image url`() {
        assertEquals("https://cdn.example.com/workshop/itunes-3000.jpg", typical.podcast.artworkUrl)
    }

    @Test
    fun `typical feed - all episodes in feed order`() {
        assertEquals(
            listOf("$typicalUrl#workshop-ep-42", "$typicalUrl#workshop-ep-41", "$typicalUrl#https://workshop.example.com/episodes/40"),
            typical.episodes.map { it.id },
        )
        assertTrue(typical.episodes.all { it.podcastUrl == typicalUrl })
    }

    @Test
    fun `typical feed - first episode fields`() {
        val ep = typical.episodes[0]
        assertEquals("Ep. 42: Sharpening chisels", ep.title)
        assertEquals("https://cdn.example.com/workshop/ep42.mp3", ep.audioUrl)
        assertEquals(utc("2024-10-02T10:00:00Z"), ep.pubDate)
        assertEquals(3_723_000L, ep.durationMs)
        assertEquals(0L, ep.positionMs)
        assertFalse(ep.played)
        assertEquals(0L, ep.lastPlayedAt)
    }

    @Test
    fun `content encoded wins over description and is kept as html, trimmed`() {
        val description = typical.episodes[0].description
        assertTrue(description, description.startsWith("<p>This week: <a href=\"https://workshop.example.com/42\">"))
        assertTrue(description, description.endsWith("</ul>"))
        // Inside CDATA the entity is literal text, not decoded.
        assertTrue(description, description.contains("&amp; plane irons"))
        assertFalse(description.contains("Short notes"))
    }

    @Test
    fun `cdata description used when there is no content encoded`() {
        assertEquals("<p>All about <i>glue-ups</i>.</p>", typical.episodes[1].description)
    }

    @Test
    fun `itunes summary used when there is neither content encoded nor description`() {
        assertEquals("Only an iTunes summary here.", typical.episodes[2].description)
    }

    @Test
    fun `episode image - item itunes image, else podcast artwork`() {
        assertEquals("https://cdn.example.com/workshop/itunes-3000.jpg", typical.episodes[0].imageUrl)
        assertEquals("https://cdn.example.com/workshop/ep41-art.jpg", typical.episodes[1].imageUrl)
        assertEquals("https://cdn.example.com/workshop/itunes-3000.jpg", typical.episodes[2].imageUrl)
    }

    @Test
    fun `durations in MM-SS and plain seconds`() {
        assertEquals(2_730_000L, typical.episodes[1].durationMs)
        assertEquals(95_000L, typical.episodes[2].durationMs)
    }

    // --- A messy hand-made feed ---

    private val messyUrl = "http://garageradio.example.org/feed.xml"
    private val messy by lazy { parse("messy.xml", messyUrl) }
    private fun messyEpisode(title: String): Episode =
        messy.episodes.firstOrNull { it.title == title } ?: throw AssertionError("no episode \"$title\" in ${messy.episodes.map { it.title }}")

    @Test
    fun `nested image title does not become the show title`() {
        assertEquals("Garage Radio", messy.podcast.title)
    }

    @Test
    fun `channel image url used when there is no itunes image`() {
        assertEquals("http://garageradio.example.org/logo.png", messy.podcast.artworkUrl)
    }

    @Test
    fun `missing author is empty, missing description falls back to itunes summary`() {
        assertEquals("", messy.podcast.author)
        assertEquals("A community show recorded in a garage.", messy.podcast.description.trim())
    }

    @Test
    fun `items without enclosure skipped and duplicate guids keep the first, in feed order`() {
        assertEquals(
            listOf(
                "No guid, odd date",
                "Blank guid",
                "Video first, audio second",
                "Only video enclosures",
                "Untitled episode",
                "Duplicate, first copy",
                "Garbage date",
                "Enclosure without type",
            ),
            messy.episodes.map { it.title },
        )
        assertEquals(messy.episodes.size, messy.episodes.map { it.id }.toSet().size)
    }

    @Test
    fun `duplicate guid keeps the first copy's data`() {
        val dup = messy.episodes.single { it.id == "$messyUrl#garage-dup" }
        assertEquals("http://garageradio.example.org/audio/dup-first.mp3", dup.audioUrl)
    }

    @Test
    fun `missing or blank guid falls back to the audio url`() {
        assertEquals("$messyUrl#http://garageradio.example.org/audio/2024-03-05.mp3", messyEpisode("No guid, odd date").id)
        assertEquals("$messyUrl#http://garageradio.example.org/audio/2024-03-01.mp3", messyEpisode("Blank guid").id)
    }

    @Test
    fun `guid is trimmed`() {
        assertEquals("$messyUrl#garage-video-1", messyEpisode("Video first, audio second").id)
    }

    @Test
    fun `odd dates`() {
        assertEquals(utc("2024-03-05T14:05:00Z"), messyEpisode("No guid, odd date").pubDate)
        assertEquals(utc("2024-03-01T08:00:00Z"), messyEpisode("Blank guid").pubDate)
        assertEquals(utc("2024-02-26T07:00:00Z"), messyEpisode("Video first, audio second").pubDate)
        assertEquals(0L, messyEpisode("Garbage date").pubDate)
        assertEquals(0L, messyEpisode("Enclosure without type").pubDate)
    }

    @Test
    fun `durations in seconds, H-MM-SS, HH-MM-SS, garbage and missing`() {
        assertEquals(1_834_000L, messyEpisode("No guid, odd date").durationMs)
        assertEquals(3_723_000L, messyEpisode("Blank guid").durationMs)
        assertEquals(3_599_000L, messyEpisode("Video first, audio second").durationMs)
        assertEquals(0L, messyEpisode("Untitled episode").durationMs)
        assertEquals(0L, messyEpisode("Duplicate, first copy").durationMs)
    }

    @Test
    fun `several enclosures - the audio one wins even when second`() {
        assertEquals("http://garageradio.example.org/audio/ep.m4a", messyEpisode("Video first, audio second").audioUrl)
    }

    @Test
    fun `several enclosures without audio - the first one`() {
        assertEquals("http://garageradio.example.org/video/a.mp4", messyEpisode("Only video enclosures").audioUrl)
    }

    @Test
    fun `enclosure without type still counts`() {
        assertEquals("http://garageradio.example.org/audio/notype.mp3", messyEpisode("Enclosure without type").audioUrl)
    }

    @Test
    fun `blank episode title becomes Untitled episode`() {
        assertEquals("$messyUrl#garage-blank-title", messyEpisode("Untitled episode").id)
    }

    @Test
    fun `missing description is empty`() {
        assertEquals("", messyEpisode("Blank guid").description)
    }

    @Test
    fun `nested unescaped tags in a description are read as text`() {
        val description = messyEpisode("Enclosure without type").description
        assertTrue(description, description.contains("Unescaped"))
        assertTrue(description, description.contains("HTML"))
        assertTrue(description, description.contains("notes"))
    }

    @Test
    fun `episode image falls back to the channel image url`() {
        assertEquals("http://garageradio.example.org/ep-art.jpg", messyEpisode("Video first, audio second").imageUrl)
        assertEquals("http://garageradio.example.org/logo.png", messyEpisode("Blank guid").imageUrl)
    }

    // --- Other shapes ---

    @Test
    fun `blank channel title becomes the feed url, never an item title`() {
        val url = "https://example.net/untitled.xml"
        val feed = parse("no_title.xml", url)
        assertEquals(url, feed.podcast.title)
        assertEquals("", feed.podcast.author)
        assertEquals("", feed.podcast.description)
        assertNull(feed.podcast.artworkUrl)
        assertEquals(1, feed.episodes.size)
        assertNull(feed.episodes[0].imageUrl)
    }

    @Test
    fun `iso-8859-1 feed decodes accented text from the xml declaration`() {
        val url = "https://radio-evasion.example.fr/cafe.xml"
        val feed = parse("latin1.xml", url)
        assertEquals("Café Crème", feed.podcast.title)
        assertEquals("Radio Évasion", feed.podcast.author)
        assertEquals("Les élèves parlent de cinéma.", feed.podcast.description.trim())
        val ep = feed.episodes.single()
        assertEquals("Épisode 1 : À bientôt", ep.title)
        assertEquals("$url#cafe-creme-1", ep.id)
        assertEquals(utc("2024-03-04T17:30:00Z"), ep.pubDate)
        assertEquals(754_000L, ep.durationMs)
    }

    @Test
    fun `utf-8 feed given as raw bytes keeps non-ascii text`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>Señor Ñandú — 日本</title>
            <item><title>Uno</title><guid>1</guid><enclosure url="https://x.example/1.mp3" type="audio/mpeg"/></item>
            </channel></rss>
        """.trimIndent()
        val feed = FeedParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "https://x.example/feed", now)
        assertEquals("Señor Ñandú — 日本", feed.podcast.title)
        assertEquals("https://x.example/feed#1", feed.episodes.single().id)
    }

    @Test
    fun `channel with no items gives no episodes`() {
        val xml = """<?xml version="1.0"?><rss version="2.0"><channel><title>Empty</title></channel></rss>"""
        val feed = FeedParser.parse(ByteArrayInputStream(xml.toByteArray()), "https://x.example/empty", now)
        assertEquals("Empty", feed.podcast.title)
        assertTrue(feed.episodes.isEmpty())
    }

    // --- Not feeds ---

    @Test
    fun `html page is not a feed`() {
        assertEquals(LoadError.NOT_A_FEED, parseError(fixture("not_a_feed.html")))
    }

    @Test
    fun `malformed xml is not a feed`() {
        assertEquals(LoadError.NOT_A_FEED, parseError(fixture("malformed.xml")))
    }

    @Test
    fun `atom feed is not a feed`() {
        assertEquals(LoadError.NOT_A_FEED, parseError(fixture("atom.xml")))
    }

    @Test
    fun `rss without channel is not a feed`() {
        assertEquals(LoadError.NOT_A_FEED, parseError(fixture("no_channel.xml")))
    }

    @Test
    fun `empty input is not a feed`() {
        assertEquals(LoadError.NOT_A_FEED, parseError(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `json is not a feed`() {
        assertEquals(LoadError.NOT_A_FEED, parseError(ByteArrayInputStream("""{"results":[]}""".toByteArray())))
    }
}
