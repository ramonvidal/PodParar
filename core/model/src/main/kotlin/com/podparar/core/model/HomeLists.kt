package com.podparar.core.model

/** What Home shows in each section, so an episode appears in only one of them. */
object HomeLists {
    /** [episodes] in their order, without any whose episode id is in [queue] (Up next). */
    fun withoutQueued(
        episodes: List<EpisodeWithPodcast>,
        queue: List<EpisodeWithPodcast>,
    ): List<EpisodeWithPodcast> {
        if (queue.isEmpty()) return episodes
        val queued = queue.mapTo(HashSet()) { it.episode.id }
        return episodes.filterNot { it.episode.id in queued }
    }
}
