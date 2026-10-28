package com.podparar.core.network

import android.util.Xml
import com.podparar.core.model.Episode
import com.podparar.core.model.LoadError
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import com.podparar.core.model.PodcastException
import com.podparar.core.model.parse.DateParser
import com.podparar.core.model.parse.DurationParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/** RSS 2.0 + iTunes podcast feed parser. */
internal object FeedParser {
    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val UNTITLED = "Untitled episode"

    /**
     * RSS 2.0 + iTunes tags. Throws PodcastException(NOT_A_FEED) if the root isn't <rss>
     * (or <channel> is missing) or the XML is malformed.
     */
    fun parse(input: InputStream, feedUrl: String, now: Long = System.currentTimeMillis()): ParsedFeed {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            // Null encoding: taken from the XML declaration (or the byte order mark), UTF-8 otherwise.
            parser.setInput(input, null)
            return parseDocument(parser, feedUrl, now)
        } catch (e: PodcastException) {
            throw e
        } catch (e: XmlPullParserException) {
            throw PodcastException(LoadError.NOT_A_FEED, e)
        } catch (e: RuntimeException) {
            // The pull parser can fail with runtime errors on badly broken input; it's still not a feed.
            throw PodcastException(LoadError.NOT_A_FEED, e)
        }
    }

    private fun parseDocument(parser: XmlPullParser, feedUrl: String, now: Long): ParsedFeed {
        var event = parser.next()
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) event = parser.next()
        if (event != XmlPullParser.START_TAG || !parser.isPlain("rss")) notAFeed()

        var feed: ParsedFeed? = null
        // Children of <rss>: the first <channel> is the show; anything else is skipped.
        while (parser.next().also { event = it } != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.END_DOCUMENT) notAFeed()
            if (event != XmlPullParser.START_TAG) continue
            if (feed == null && parser.isPlain("channel")) feed = parseChannel(parser, feedUrl, now) else parser.skip()
        }
        // Read to the end so malformed XML after the channel is caught too.
        while (parser.next() != XmlPullParser.END_DOCUMENT) Unit
        return feed ?: notAFeed()
    }

    private fun parseChannel(parser: XmlPullParser, feedUrl: String, now: Long): ParsedFeed {
        var title: String? = null
        var author: String? = null
        var description: String? = null
        var summary: String? = null
        var itunesImage: String? = null
        var rssImage: String? = null
        val items = mutableListOf<RawItem>()

        forEachChild(parser) {
            when {
                parser.isPlain("title") -> parser.readText().let { if (title.isNullOrEmpty()) title = it }
                parser.isPlain("description") -> parser.readText().let { if (description.isNullOrEmpty()) description = it }
                parser.isPlain("image") -> parseImageUrl(parser).let { if (rssImage == null) rssImage = it }
                parser.isPlain("item") -> items += parseItem(parser)
                parser.isIn(ITUNES, "author") -> parser.readText().let { if (author.isNullOrEmpty()) author = it }
                parser.isIn(ITUNES, "summary") -> parser.readText().let { if (summary.isNullOrEmpty()) summary = it }
                parser.isIn(ITUNES, "image") -> {
                    val href = parser.href()
                    if (itunesImage == null) itunesImage = href
                    parser.skip()
                }
                else -> parser.skip()
            }
        }

        val artwork = itunesImage ?: rssImage
        val podcast = Podcast(
            feedUrl = feedUrl,
            title = title?.takeIf { it.isNotEmpty() } ?: feedUrl,
            author = author.orEmpty(),
            description = description?.takeIf { it.isNotEmpty() } ?: summary.orEmpty(),
            artworkUrl = artwork,
            lastRefreshed = now,
        )
        val seen = HashSet<String>()
        val episodes = items.mapNotNull { it.toEpisode(feedUrl, artwork) }.filter { seen.add(it.id) }
        return ParsedFeed(podcast, episodes)
    }

    /** The <url> inside a channel's <image>, or null. */
    private fun parseImageUrl(parser: XmlPullParser): String? {
        var url: String? = null
        forEachChild(parser) {
            if (parser.isPlain("url")) parser.readText().let { if (url == null && it.isNotEmpty()) url = it } else parser.skip()
        }
        return url
    }

    private fun parseItem(parser: XmlPullParser): RawItem {
        val item = RawItem()
        forEachChild(parser) {
            when {
                parser.isPlain("title") -> parser.readText().let { if (item.title.isNullOrEmpty()) item.title = it }
                parser.isPlain("guid") -> parser.readText().let { if (item.guid == null) item.guid = it }
                parser.isPlain("pubDate") -> parser.readText().let { if (item.pubDate == null) item.pubDate = it }
                parser.isPlain("description") -> parser.readText().let { if (item.description.isNullOrEmpty()) item.description = it }
                parser.isPlain("enclosure") -> {
                    val url = parser.getAttributeValue(null, "url")?.trim()
                    if (!url.isNullOrEmpty()) item.enclosures += Enclosure(url, parser.getAttributeValue(null, "type")?.trim().orEmpty())
                    parser.skip()
                }
                parser.isIn(CONTENT, "encoded") -> parser.readText().let { if (item.content.isNullOrEmpty()) item.content = it }
                parser.isIn(ITUNES, "summary") -> parser.readText().let { if (item.summary.isNullOrEmpty()) item.summary = it }
                parser.isIn(ITUNES, "duration") -> parser.readText().let { if (item.duration == null) item.duration = it }
                parser.isIn(ITUNES, "image") -> {
                    val href = parser.href()
                    if (item.image == null) item.image = href
                    parser.skip()
                }
                else -> parser.skip()
            }
        }
        return item
    }

    private class Enclosure(val url: String, val type: String)

    private class RawItem {
        var title: String? = null
        var guid: String? = null
        var pubDate: String? = null
        var description: String? = null
        var content: String? = null
        var summary: String? = null
        var duration: String? = null
        var image: String? = null
        val enclosures = mutableListOf<Enclosure>()

        fun toEpisode(feedUrl: String, artwork: String?): Episode? {
            val enclosure = enclosures.firstOrNull { it.type.startsWith("audio/", ignoreCase = true) }
                ?: enclosures.firstOrNull()
                ?: return null
            val audioUrl = enclosure.url
            return Episode(
                id = Episode.idFor(feedUrl, guid, audioUrl),
                podcastUrl = feedUrl,
                title = title?.takeIf { it.isNotEmpty() } ?: UNTITLED,
                description = content?.takeIf { it.isNotEmpty() }
                    ?: description?.takeIf { it.isNotEmpty() }
                    ?: summary.orEmpty(),
                audioUrl = audioUrl,
                imageUrl = image ?: artwork,
                pubDate = DateParser.parseEpochMs(pubDate),
                durationMs = DurationParser.parseMs(duration),
                positionMs = 0L,
                played = false,
            )
        }
    }

    /** Calls [block] at the START_TAG of each direct child of the current element; [block] must consume it. */
    private inline fun forEachChild(parser: XmlPullParser, block: () -> Unit) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> block()
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> notAFeed()
            }
        }
    }

    /** Skips the current element and everything inside it. */
    private fun XmlPullParser.skip() {
        var depth = 1
        while (depth > 0) {
            when (next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> notAFeed()
            }
        }
    }

    /** All text inside the current element (CDATA and the text of nested tags included), trimmed. */
    private fun XmlPullParser.readText(): String {
        val text = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (next()) {
                XmlPullParser.TEXT -> text.append(this.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> notAFeed()
            }
        }
        return text.toString().trim()
    }

    /** The trimmed href attribute, or null if missing or blank. */
    private fun XmlPullParser.href(): String? = getAttributeValue(null, "href")?.trim()?.takeIf { it.isNotEmpty() }

    private fun XmlPullParser.isPlain(tag: String) = name == tag && namespace.isNullOrEmpty()

    private fun XmlPullParser.isIn(ns: String, tag: String) = name == tag && namespace == ns

    private fun notAFeed(): Nothing = throw PodcastException(LoadError.NOT_A_FEED)
}
