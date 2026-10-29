package com.podparar.core.model

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

/** One show in an OPML file. */
data class OpmlEntry(val title: String, val feedUrl: String)

/** What an OPML import did. */
data class ImportResult(val added: Int, val alreadySubscribed: Int, val failed: Int)

/** Writes and reads OPML subscription lists, so shows move between phones. */
object Opml {

    /**
     * OPML 2.0 titled "PodOuvir subscriptions", one `<outline type="rss">` per podcast sorted by
     * title (case-insensitive), XML escaped.
     */
    fun write(podcasts: List<Podcast>): String {
        val sorted = podcasts.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("  <head>\n")
            append("    <title>PodOuvir subscriptions</title>\n")
            append("  </head>\n")
            append("  <body>\n")
            for (podcast in sorted) {
                val title = escape(podcast.title)
                append("    <outline type=\"rss\" text=\"")
                append(title)
                append("\" title=\"")
                append(title)
                append("\" xmlUrl=\"")
                append(escape(podcast.feedUrl))
                append("\"/>\n")
            }
            append("  </body>\n")
            append("</opml>\n")
        }
    }

    /**
     * Every outline (any depth) with a non-blank xmlUrl, in document order, duplicates dropped;
     * title = text, else title, else the url. Not XML / no `<opml>` root -> IllegalArgumentException.
     */
    fun parse(xml: String): List<OpmlEntry> {
        val document = try {
            newFactory().newDocumentBuilder().apply {
                setErrorHandler(ThrowingErrorHandler)
                // Never fetch anything external, even if a protection above wasn't supported.
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
            }.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid OPML file", e)
        }
        val root = document.documentElement
        if (root == null || localName(root) != "opml") {
            throw IllegalArgumentException("Not an OPML file")
        }
        val seen = HashSet<String>()
        val entries = ArrayList<OpmlEntry>()
        collect(root, seen, entries)
        return entries
    }

    private fun collect(element: Element, seen: MutableSet<String>, out: MutableList<OpmlEntry>) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            child as Element
            if (localName(child) == "outline") {
                val url = child.getAttribute("xmlUrl").trim()
                if (url.isNotEmpty() && seen.add(url)) {
                    val text = child.getAttribute("text").trim()
                    val title = child.getAttribute("title").trim()
                    out += OpmlEntry(
                        title = text.ifEmpty { title.ifEmpty { url } },
                        feedUrl = url,
                    )
                }
            }
            collect(child, seen, out)
        }
    }

    private fun localName(element: Element): String =
        (element.localName ?: element.tagName).substringAfter(':')

    private fun newFactory(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        // Android's parser may not support some of these; each failure is ignored and the others still apply.
        trySet { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        trySet { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        trySet { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        trySet { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        trySet { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        trySet { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        trySet { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        trySet { factory.isXIncludeAware = false }
        trySet { factory.isExpandEntityReferences = false }
        factory.isNamespaceAware = false
        factory.isValidating = false
        return factory
    }

    private inline fun trySet(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        } catch (_: AbstractMethodError) {
        }
    }

    /** Fails on errors instead of printing them to stderr; warnings are ignored. */
    private object ThrowingErrorHandler : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit
        override fun error(exception: SAXParseException): Unit = throw exception
        override fun fatalError(exception: SAXParseException): Unit = throw exception
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // Control characters other than tab / newline / CR aren't allowed in XML 1.0.
                else -> if (c >= ' ' || c == '\t' || c == '\n' || c == '\r') append(c)
            }
        }
    }
}
