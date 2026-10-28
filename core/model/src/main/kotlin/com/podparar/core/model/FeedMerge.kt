package com.podparar.core.model

/** Folding a refreshed feed into the episodes already stored. */
object FeedMerge {
    /**
     * Merges a fresh download onto what's stored, so a refresh never loses progress.
     * - Fresh episodes with a new id are added as parsed.
     * - Known ids take the fresh title, description, audioUrl, imageUrl and pubDate, and the fresh durationMs unless it is 0
     *   (then the stored one stays); they keep the stored positionMs, played and lastPlayedAt.
     * - Stored episodes missing from the fresh feed are kept unchanged.
     * - Duplicate ids in [fresh]: the first wins.
     * Result: newest first by pubDate; ties keep fresh-feed order, then stored order.
     */
    fun merge(stored: List<Episode>, fresh: List<Episode>): List<Episode> {
        val storedById = LinkedHashMap<String, Episode>()
        stored.forEach { storedById.putIfAbsent(it.id, it) }
        val freshById = LinkedHashMap<String, Episode>()
        fresh.forEach { freshById.putIfAbsent(it.id, it) }

        val updated = freshById.values.map { new ->
            val old = storedById[new.id] ?: return@map new
            old.copy(
                title = new.title,
                description = new.description,
                audioUrl = new.audioUrl,
                imageUrl = new.imageUrl,
                pubDate = new.pubDate,
                durationMs = if (new.durationMs != 0L) new.durationMs else old.durationMs,
            )
        }
        val kept = storedById.values.filter { it.id !in freshById }
        // sortedByDescending is stable, so ties keep this fresh-then-stored order.
        return (updated + kept).sortedByDescending { it.pubDate }
    }
}
