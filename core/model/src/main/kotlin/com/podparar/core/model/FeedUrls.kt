package com.podparar.core.model

/** Cleaning up feed links people paste. */
object FeedUrls {
    private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")
    private val WHITESPACE = Regex("""\s""")
    private val HTTPS_ALIASES = listOf("feed", "itpc", "pcast")

    /**
     * Turns what someone pasted into a feed URL, or null if it can't be one.
     * Trims. Blank -> null. "feed://x", "itpc://x", "pcast://x" -> "https://x"; "feed:https://x" / "feed:http://x" -> the inner URL.
     * No scheme -> "https://" + it. http:// and https:// kept as they are (http is not upgraded); scheme case-insensitive,
     * output scheme lower case. Any other scheme (ftp:, mailto:, javascript:) -> null.
     * After that the host (between "://" and the next '/', '?', '#' or ':') must be non-empty and contain a '.',
     * and the URL must contain no whitespace; otherwise null.
     */
    fun normalize(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val url = withScheme(text) ?: return null
        if (WHITESPACE.containsMatchIn(url)) return null
        val afterScheme = url.substringAfter("://")
        val hostEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#', ':')).let { if (it < 0) afterScheme.length else it }
        val host = afterScheme.substring(0, hostEnd)
        if (host.isEmpty() || '.' !in host) return null
        return url
    }

    /** The URL with an http(s) scheme in lower case, or null for any other scheme. */
    private fun withScheme(text: String): String? {
        val match = SCHEME.find(text)
        val scheme = match?.groupValues?.get(1)
        val rest = match?.let { text.substring(it.range.last + 1) }
        // "example.com:8080/feed" is a host and port, not a scheme.
        if (scheme == null || rest == null || ('.' in scheme && !rest.startsWith("//"))) return "https://$text"
        val lower = scheme.lowercase()
        return when {
            lower == "http" || lower == "https" -> if (rest.startsWith("//")) "$lower:$rest" else null
            lower == "feed" && !rest.startsWith("//") -> {
                val inner = SCHEME.find(rest)?.groupValues?.get(1)?.lowercase()
                if ((inner == "http" || inner == "https") && rest.substring(inner.length + 1).startsWith("//")) {
                    inner + rest.substring(inner.length)
                } else {
                    null
                }
            }
            lower in HTTPS_ALIASES && rest.startsWith("//") -> "https:$rest"
            else -> null
        }
    }
}
