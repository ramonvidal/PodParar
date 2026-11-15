package com.podparar.core.model

/** Rules for a list of [Chapter]s. Lists passed to the lookups are expected to be [normalize]d. */
object Chapters {
    private const val RESTART_THRESHOLD_MS = 3000L

    /** Drops blank titles and negative starts, sorts by startMs, keeps the first of equal starts. */
    fun normalize(chapters: List<Chapter>): List<Chapter> =
        chapters
            .filter { it.title.isNotBlank() && it.startMs >= 0 }
            .sortedBy { it.startMs } // stable: equal starts keep their original order
            .distinctBy { it.startMs }

    /** Index of the chapter containing [positionMs]: the last one whose startMs <= positionMs; null if the list is empty or the position is before the first. */
    fun indexAt(chapters: List<Chapter>, positionMs: Long): Int? {
        var found: Int? = null
        for (i in chapters.indices) {
            if (chapters[i].startMs <= positionMs) found = i else break
        }
        return found
    }

    /** Start of the chapter after the current one (the first chapter if the position is before it); null when there is no next chapter. */
    fun nextStartMs(chapters: List<Chapter>, positionMs: Long): Long? {
        val index = indexAt(chapters, positionMs) ?: return chapters.firstOrNull()?.startMs
        return chapters.getOrNull(index + 1)?.startMs
    }

    /** More than 3000 ms into the current chapter -> its start; else the previous chapter's start; at the first chapter (or before it) -> 0; empty list -> null. */
    fun previousStartMs(chapters: List<Chapter>, positionMs: Long): Long? {
        if (chapters.isEmpty()) return null
        val index = indexAt(chapters, positionMs) ?: return 0L
        val start = chapters[index].startMs
        return when {
            positionMs - start > RESTART_THRESHOLD_MS -> start
            index == 0 -> 0L
            else -> chapters[index - 1].startMs
        }
    }
}
