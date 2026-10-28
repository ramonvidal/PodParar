package com.podparar.core.model

/** Show notes come as HTML; screens show them as plain text. */
object HtmlText {
    private val SCRIPT_OR_STYLE = Regex("""<(script|style)\b[^>]*>.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val BREAK = Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex("""</(p|div|li|h[1-6])\s*>""", RegexOption.IGNORE_CASE)
    private val LIST_ITEM = Regex("""<li\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""</?[A-Za-z][^>]*>|<![^>]*>""")
    private val ENTITY = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|[A-Za-z]+);""")
    private val SPACES = Regex("""[ \t ]+""")
    private val MANY_NEWLINES = Regex("""\n{3,}""")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    )

    /**
     * Show notes HTML -> plain text for display.
     * <br>, <br/>, <br /> -> newline; </p>, </div>, </li>, </h1>..</h6> -> newline; <li> -> "- ".
     * <script>...</script> and <style>...</style> removed with their content. All other tags removed (case-insensitive).
     * Entities: &amp; &lt; &gt; &quot; &#39; &apos; &nbsp; (-> space) and numeric &#NNN; / &#xHH; decoded; unknown entities kept as is.
     * Runs of spaces/tabs -> one space; each line trimmed; 3+ newlines -> 2 (one blank line); whole result trimmed.
     * Plain text without tags passes through (only whitespace rules applied).
     */
    fun toPlain(html: String): String {
        val text = html
            .replace("\r\n", "\n").replace('\r', '\n')
            .replace(SCRIPT_OR_STYLE, "")
            .replace(COMMENT, "")
            .replace(BREAK, "\n")
            .replace(BLOCK_END, "\n")
            .replace(LIST_ITEM, "- ")
            .replace(TAG, "")
            .let(::decodeEntities)
            .replace(SPACES, " ")
        return text.lineSequence().joinToString("\n") { it.trim() }
            .replace(MANY_NEWLINES, "\n\n")
            .trim()
    }

    private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
        val name = match.groupValues[1]
        val decoded = when {
            name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED[name]
        }
        decoded ?: match.value
    }

    /** The character for [code], or null if it isn't a valid one. */
    private fun codePoint(code: Int): String? =
        if (Character.isValidCodePoint(code) && code != 0) String(Character.toChars(code)) else null
}
