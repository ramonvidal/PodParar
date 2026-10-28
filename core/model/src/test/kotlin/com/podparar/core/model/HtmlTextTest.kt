package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {
    private fun plain(html: String) = HtmlText.toPlain(html)

    @Test
    fun `empty stays empty`() {
        assertEquals("", plain(""))
        assertEquals("", plain("   \n\n  "))
    }

    @Test
    fun `plain text passes through`() {
        assertEquals("Just a sentence, nothing else.", plain("Just a sentence, nothing else."))
        assertEquals("Line one\nLine two", plain("Line one\nLine two"))
    }

    @Test
    fun `plain text still gets the whitespace rules`() {
        assertEquals("a b\nc\n\nd", plain("  a  \t b  \n  c \n\n\n\n d  "))
    }

    @Test
    fun `br in every spelling is a newline`() {
        assertEquals("a\nb\nc\nd", plain("a<br>b<br/>c<br />d"))
        assertEquals("a\nb", plain("a<BR>b"))
        assertEquals("a\nb", plain("a<Br />b"))
    }

    @Test
    fun `closing block tags end a line`() {
        assertEquals("One\nTwo", plain("<p>One</p><p>Two</p>"))
        assertEquals("One\nTwo", plain("<div>One</div><div>Two</div>"))
        assertEquals("One\nTwo", plain("<P>One</P><P>Two</P>"))
    }

    @Test
    fun `closing headings end a line`() {
        assertEquals("Title\nBody", plain("<h1>Title</h1>Body"))
        assertEquals("Title\nBody", plain("<h2>Title</h2>Body"))
        assertEquals("Title\nBody", plain("<h3>Title</h3>Body"))
        assertEquals("Title\nBody", plain("<h4>Title</h4>Body"))
        assertEquals("Title\nBody", plain("<h5>Title</h5>Body"))
        assertEquals("Title\nBody", plain("<h6>Title</h6>Body"))
        assertEquals("Title\nBody", plain("<H2>Title</H2>Body"))
    }

    @Test
    fun `list items become dashes on their own lines`() {
        assertEquals("- One\n- Two", plain("<ul><li>One</li><li>Two</li></ul>"))
        assertEquals("Links:\n- One\n- Two", plain("<p>Links:</p><ol><LI>One</LI><li>Two</li></ol>"))
    }

    @Test
    fun `other tags are removed keeping their text`() {
        assertEquals("Bold and a link here", plain("<b>Bold</b> and <a href=\"https://x.com/?a=1&b=2\">a link</a> here"))
        assertEquals("Hi there", plain("<SPAN class='x'>Hi</SPAN> <em>there</em>"))
        assertEquals("text", plain("<img src=\"a.jpg\" alt=\"pic\"/>text"))
    }

    @Test
    fun `script and style are removed with their content`() {
        assertEquals("ab", plain("a<script>var x = '<b>hi</b>';</script>b"))
        assertEquals("ab", plain("a<SCRIPT type=\"text/javascript\">alert(1)</SCRIPT>b"))
        assertEquals("ab", plain("a<style>p { color: red; }</style>b"))
        assertEquals("ab", plain("a<Style media=\"all\">.x{}</Style>b"))
    }

    @Test
    fun `script spanning lines is removed`() {
        assertEquals("before\nafter", plain("before<br><script>\nline1\nline2\n</script>after"))
    }

    @Test
    fun `named entities are decoded`() {
        assertEquals("Tom & Jerry", plain("Tom &amp; Jerry"))
        assertEquals("1 < 2 > 0", plain("1 &lt; 2 &gt; 0"))
        assertEquals("say \"hi\"", plain("say &quot;hi&quot;"))
        assertEquals("it's Bob's", plain("it&#39;s Bob&apos;s"))
    }

    @Test
    fun `escaped markup becomes text, not removed as a tag`() {
        assertEquals("use <b> for bold", plain("use &lt;b&gt; for bold"))
    }

    @Test
    fun `nbsp is a space and collapses with spaces`() {
        assertEquals("a b", plain("a&nbsp;b"))
        assertEquals("a b", plain("a&nbsp;&nbsp; b"))
        assertEquals("a", plain("&nbsp;a&nbsp;"))
    }

    @Test
    fun `numeric entities are decoded`() {
        assertEquals("A", plain("&#65;"))
        assertEquals("it’s", plain("it&#8217;s"))
        assertEquals("it’s", plain("it&#x2019;s"))
        assertEquals("été", plain("&#xe9;t&#233;"))
    }

    @Test
    fun `unknown entities and bare ampersands are kept`() {
        assertEquals("&foo; stays", plain("&foo; stays"))
        assertEquals("AT&T and R&D", plain("AT&T and R&D"))
    }

    @Test
    fun `entities are decoded only once`() {
        assertEquals("&lt;", plain("&amp;lt;"))
        assertEquals("&amp;", plain("&amp;amp;"))
    }

    @Test
    fun `runs of spaces and tabs become one space`() {
        assertEquals("a b c", plain("a    b\t\t c"))
    }

    @Test
    fun `each line is trimmed`() {
        assertEquals("one\ntwo", plain("<p>  one  </p><p>\ttwo </p>"))
    }

    @Test
    fun `three or more newlines become one blank line`() {
        assertEquals("a\n\nb", plain("a\n\n\nb"))
        assertEquals("a\n\nb", plain("a\n\n\n\n\n\nb"))
        assertEquals("a\n\nb", plain("<p>a</p><p></p><p></p><p>b</p>"))
        assertEquals("a\n\nb", plain("a<br><br><br><br>b"))
    }

    @Test
    fun `lines that only had spaces count as empty`() {
        assertEquals("a\n\nb", plain("a\n   \n \t \n   \nb"))
    }

    @Test
    fun `one blank line is kept`() {
        assertEquals("a\n\nb", plain("a\n\nb"))
        assertEquals("a\n\nb", plain("a<br><br>b"))
    }

    @Test
    fun `result is trimmed`() {
        assertEquals("Hello", plain("<p></p><br>\n  <p>Hello</p><br><br>\n"))
    }

    @Test
    fun `typical show notes`() {
        val html = """
            <p>This week we talk about <strong>coffee</strong> &amp; tea.</p>
            <p>Links:</p>
            <ul>
              <li><a href="https://example.com">Example</a></li>
              <li>Another&nbsp;one</li>
            </ul>
        """.trimIndent()
        assertEquals(
            "This week we talk about coffee & tea.\n\nLinks:\n\n- Example\n\n- Another one",
            plain(html),
        )
    }
}
