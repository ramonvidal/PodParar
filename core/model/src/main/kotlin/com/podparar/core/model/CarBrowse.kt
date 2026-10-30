package com.podparar.core.model

/** The Android Auto browse tree's ids. Episode ids are used as they are for playable items. */
object CarBrowse {
    const val ROOT = "root"
    const val CONTINUE = "continue"
    const val UP_NEXT = "up_next"
    const val NEW = "new"
    const val SHOWS = "shows"
    const val SHOW_PREFIX = "show:"

    /** At most this many episodes in a list (cars scroll slowly). */
    const val MAX_ITEMS: Int = 50

    private val FOLDERS = setOf(ROOT, CONTINUE, UP_NEXT, NEW, SHOWS)

    /** "show:" + feedUrl. */
    fun showId(feedUrl: String): String = SHOW_PREFIX + feedUrl

    /** The feed URL of a show id, or null if [id] isn't one ("show:" alone -> null). */
    fun feedUrlOf(id: String): String? =
        if (id.startsWith(SHOW_PREFIX)) id.substring(SHOW_PREFIX.length).takeIf { it.isNotEmpty() } else null

    /** Whether [id] is one of the folders above (ROOT, CONTINUE, UP_NEXT, NEW, SHOWS) or a show id. */
    fun isFolder(id: String): Boolean = id in FOLDERS || feedUrlOf(id) != null
}
