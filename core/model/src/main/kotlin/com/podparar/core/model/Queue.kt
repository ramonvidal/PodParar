package com.podparar.core.model

/**
 * "Up next" is an ordered list of episode ids, not including the one playing. Pure functions; no
 * duplicates ever: every result keeps only the first occurrence of each id, so even a queue that
 * arrives with duplicates comes back clean.
 */
object Queue {
    /** [id] moved/inserted at the front. */
    fun playNext(queue: List<String>, id: String): List<String> =
        listOf(id) + queue.filter { it != id }.distinct()

    /** [id] appended at the end; unchanged if already queued. */
    fun add(queue: List<String>, id: String): List<String> {
        val clean = queue.distinct()
        return if (id in clean) clean else clean + id
    }

    fun remove(queue: List<String>, id: String): List<String> =
        queue.filter { it != id }.distinct()

    /** Moves the item at [from] to index [to] (both clamped to the list; out-of-range from -> unchanged). */
    fun move(queue: List<String>, from: Int, to: Int): List<String> {
        val clean = queue.distinct()
        if (from !in clean.indices) return clean
        val target = to.coerceIn(0, clean.lastIndex)
        if (target == from) return clean
        val result = clean.toMutableList()
        val item = result.removeAt(from)
        result.add(target, item)
        return result
    }

    /** The episode to play after the current one finishes: the first id, and the queue without it. Empty -> (null, empty). */
    fun takeNext(queue: List<String>): Pair<String?, List<String>> {
        val clean = queue.distinct()
        val next = clean.firstOrNull() ?: return null to emptyList()
        return next to clean.drop(1)
    }
}
