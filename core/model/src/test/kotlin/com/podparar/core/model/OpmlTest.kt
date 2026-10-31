package com.podparar.core.model

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

class OpmlTest {
    private fun show(title: String, feedUrl: String = "https://${title.lowercase().filter { it.isLetterOrDigit() }}.example/feed") =
        Podcast(
            feedUrl = feedUrl,
            title = title,
            author = "Author",
            description = "About",
            artworkUrl = null,
            lastRefreshed = 1_000L,
        )

    private fun expectInvalid(xml: String) {
        try {
            Opml.parse(xml)
            fail("Expected IllegalArgumentException for: $xml")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    /** Parses with the JDK, independently of Opml.parse, to check write's output is well-formed XML. */
    private fun outlines(xml: String): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = doc.getElementsByTagName("outline")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun opml(body: String) =
        """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>Mine</title></head><body>$body</body></opml>"""

    // write

    @Test
    fun `write produces an OPML 2 document with a UTF-8 declaration and the title`() {
        val xml = Opml.write(listOf(show("Alpha")))
        assertTrue(xml, xml.trimStart().startsWith("<?xml"))
        assertTrue(xml, xml.substringBefore("?>").contains("utf-8", ignoreCase = true))
        assertTrue(xml, xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml, xml.contains("<title>Escutaê subscriptions</title>"))

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        assertEquals("opml", doc.documentElement.tagName)
        assertEquals("2.0", doc.documentElement.getAttribute("version"))
        assertEquals(1, doc.getElementsByTagName("head").length)
        assertEquals(1, doc.getElementsByTagName("body").length)
    }

    @Test
    fun `write has one rss outline per podcast with text, title and xmlUrl`() {
        val xml = Opml.write(listOf(show("Alpha", "https://a.example/feed"), show("Beta", "https://b.example/rss")))
        val outlines = outlines(xml)
        assertEquals(2, outlines.size)
        for (o in outlines) assertEquals("rss", o.getAttribute("type"))
        assertEquals(listOf("Alpha", "Beta"), outlines.map { it.getAttribute("text") })
        assertEquals(listOf("Alpha", "Beta"), outlines.map { it.getAttribute("title") })
        assertEquals(listOf("https://a.example/feed", "https://b.example/rss"), outlines.map { it.getAttribute("xmlUrl") })
        // Outlines sit directly in body.
        for (o in outlines) assertEquals("body", (o.parentNode as Element).tagName)
    }

    @Test
    fun `write sorts by title ignoring case`() {
        val xml = Opml.write(listOf(show("gamma"), show("Beta"), show("alpha"), show("Delta")))
        assertEquals(listOf("alpha", "Beta", "Delta", "gamma"), outlines(xml).map { it.getAttribute("text") })
    }

    @Test
    fun `write escapes special characters in titles and urls`() {
        val title = "Tom & Jerry's \"<Show>\""
        val url = "https://x.example/feed?a=1&b=<2>"
        val xml = Opml.write(listOf(show(title, url)))

        assertTrue(xml, xml.contains("Tom &amp; Jerry"))
        assertTrue(xml, xml.contains("&quot;&lt;Show&gt;&quot;"))
        assertTrue(xml, xml.contains("a=1&amp;b=&lt;2&gt;"))
        assertTrue(xml, xml.contains("Jerry&apos;s") || xml.contains("Jerry&#39;s") || xml.contains("Jerry&#x27;s"))
        assertFalse(xml, xml.contains("Tom & Jerry"))
        assertFalse(xml, xml.contains("<Show>"))

        val outline = outlines(xml).single()
        assertEquals(title, outline.getAttribute("text"))
        assertEquals(title, outline.getAttribute("title"))
        assertEquals(url, outline.getAttribute("xmlUrl"))
    }

    @Test
    fun `write keeps non-ASCII titles`() {
        val title = "Ràdio Cafè: 日本語"
        val outline = outlines(Opml.write(listOf(show(title, "https://r.example/feed")))).single()
        assertEquals(title, outline.getAttribute("text"))
    }

    @Test
    fun `write with no podcasts is still a valid document`() {
        val xml = Opml.write(emptyList())
        assertEquals(emptyList<Element>(), outlines(xml))
        assertEquals(emptyList<OpmlEntry>(), Opml.parse(xml))
    }

    @Test
    fun `what write produces parses back in sorted order`() {
        val shows = listOf(
            show("Zed & Co", "https://z.example/feed?x=1&y=2"),
            show("apple 'pie'", "https://a.example/feed"),
            show("Middle \"quoted\" <tag>", "https://m.example/rss"),
        )
        assertEquals(
            listOf(
                OpmlEntry("apple 'pie'", "https://a.example/feed"),
                OpmlEntry("Middle \"quoted\" <tag>", "https://m.example/rss"),
                OpmlEntry("Zed & Co", "https://z.example/feed?x=1&y=2"),
            ),
            Opml.parse(Opml.write(shows)),
        )
    }

    // parse

    @Test
    fun `parse reads flat outlines in document order`() {
        val xml = opml(
            """<outline type="rss" text="B show" xmlUrl="https://b.example/feed"/>""" +
                """<outline type="rss" text="A show" xmlUrl="https://a.example/feed"/>""",
        )
        assertEquals(
            listOf(OpmlEntry("B show", "https://b.example/feed"), OpmlEntry("A show", "https://a.example/feed")),
            Opml.parse(xml),
        )
    }

    @Test
    fun `parse finds outlines nested at any depth`() {
        val xml = opml(
            """<outline text="News">""" +
                """<outline type="rss" text="One" xmlUrl="https://one.example/feed"/>""" +
                """<outline text="Deeper"><outline type="rss" text="Two" xmlUrl="https://two.example/feed"/></outline>""" +
                """</outline>""" +
                """<outline type="rss" text="Three" xmlUrl="https://three.example/feed"/>""",
        )
        assertEquals(
            listOf(
                OpmlEntry("One", "https://one.example/feed"),
                OpmlEntry("Two", "https://two.example/feed"),
                OpmlEntry("Three", "https://three.example/feed"),
            ),
            Opml.parse(xml),
        )
    }

    @Test
    fun `parse uses the title attribute when text is missing, else the url`() {
        val xml = opml(
            """<outline type="rss" title="Only title" xmlUrl="https://t.example/feed"/>""" +
                """<outline type="rss" xmlUrl="https://bare.example/feed"/>""" +
                """<outline type="rss" text="Text wins" title="Not this" xmlUrl="https://both.example/feed"/>""",
        )
        assertEquals(
            listOf(
                OpmlEntry("Only title", "https://t.example/feed"),
                OpmlEntry("https://bare.example/feed", "https://bare.example/feed"),
                OpmlEntry("Text wins", "https://both.example/feed"),
            ),
            Opml.parse(xml),
        )
    }

    @Test
    fun `parse drops later duplicates of the same xmlUrl`() {
        val xml = opml(
            """<outline type="rss" text="First" xmlUrl="https://a.example/feed"/>""" +
                """<outline type="rss" text="Other" xmlUrl="https://b.example/feed"/>""" +
                """<outline text="Folder"><outline type="rss" text="Again" xmlUrl="https://a.example/feed"/></outline>""",
        )
        assertEquals(
            listOf(OpmlEntry("First", "https://a.example/feed"), OpmlEntry("Other", "https://b.example/feed")),
            Opml.parse(xml),
        )
    }

    @Test
    fun `parse skips outlines with a missing or blank xmlUrl`() {
        val xml = opml(
            """<outline text="Folder only"/>""" +
                """<outline type="rss" text="Empty" xmlUrl=""/>""" +
                """<outline type="rss" text="Spaces" xmlUrl="   "/>""" +
                """<outline type="link" text="Site" htmlUrl="https://site.example/"/>""" +
                """<outline type="rss" text="Real" xmlUrl="https://real.example/feed"/>""",
        )
        assertEquals(listOf(OpmlEntry("Real", "https://real.example/feed")), Opml.parse(xml))
    }

    @Test
    fun `parse decodes escaped attributes`() {
        val xml = opml("""<outline type="rss" text="Tom &amp; Jerry &quot;Live&quot;" xmlUrl="https://x.example/f?a=1&amp;b=2"/>""")
        assertEquals(listOf(OpmlEntry("Tom & Jerry \"Live\"", "https://x.example/f?a=1&b=2")), Opml.parse(xml))
    }

    @Test
    fun `parse accepts a document without an XML declaration or head`() {
        val xml = """<opml version="1.0"><body><outline text="A" xmlUrl="https://a.example/feed"/></body></opml>"""
        assertEquals(listOf(OpmlEntry("A", "https://a.example/feed")), Opml.parse(xml))
    }

    @Test
    fun `parse of an OPML with no feeds is empty`() {
        assertEquals(emptyList<OpmlEntry>(), Opml.parse(opml("")))
    }

    @Test
    fun `parse rejects text that is not XML`() {
        expectInvalid("")
        expectInvalid("not xml at all")
        expectInvalid("{\"feeds\": []}")
        expectInvalid("<opml version=\"2.0\"><body><outline text=\"A\" xmlUrl=\"https://a.example/feed\"></body>")
    }

    @Test
    fun `parse rejects XML whose root is not opml`() {
        expectInvalid("""<?xml version="1.0"?><rss version="2.0"><channel><title>A feed</title></channel></rss>""")
        expectInvalid("""<html><body><outline text="A" xmlUrl="https://a.example/feed"/></body></html>""")
    }

    @Test
    fun `parse does not resolve external entities in a DOCTYPE`() {
        val secret = File.createTempFile("opml-secret", ".txt").apply {
            writeText("TOP-SECRET-CONTENT")
            deleteOnExit()
        }
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE opml [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>
            |<opml version="2.0"><head><title>&xxe;</title></head><body>
            |<outline type="rss" text="A" xmlUrl="https://a.example/feed"/>
            |</body></opml>""".trimMargin()
        try {
            val entries = Opml.parse(xml)
            for (e in entries) {
                assertFalse(e.toString(), e.title.contains("TOP-SECRET"))
                assertFalse(e.toString(), e.feedUrl.contains("TOP-SECRET"))
            }
        } catch (e: IllegalArgumentException) {
            // Refusing a document with a DOCTYPE is also fine.
            assertFalse(e.message.orEmpty().contains("TOP-SECRET"))
        }
    }

    @Test
    fun `parse does not expand internal entities into the result`() {
        // A "billion laughs" style document must not blow up into huge titles; refusing it is fine too.
        val xml = """<?xml version="1.0"?>
            |<!DOCTYPE opml [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]>
            |<opml version="2.0"><body><outline text="A" xmlUrl="https://a.example/feed"/></body></opml>""".trimMargin()
        try {
            assertEquals(listOf(OpmlEntry("A", "https://a.example/feed")), Opml.parse(xml))
        } catch (e: IllegalArgumentException) {
            // expected when DOCTYPE is disallowed
        }
    }
}
