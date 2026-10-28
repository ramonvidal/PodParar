package com.podparar.core.model

object DownloadRules {
    /** How long a played episode's download is kept before clean-up removes it. */
    const val KEEP_PLAYED_MS: Long = 24 * 60 * 60 * 1000L

    /**
     * A DONE download is removed once its episode is played and the episode's lastPlayedAt (or, if 0, the
     * download's updatedAt) is at least [KEEP_PLAYED_MS] before [now]. Not played, or not DONE -> false.
     */
    fun shouldDelete(episode: Episode, download: Download, now: Long): Boolean {
        if (download.state != DownloadState.DONE || !episode.played) return false
        val since = if (episode.lastPlayedAt > 0) episode.lastPlayedAt else download.updatedAt
        return now - since >= KEEP_PLAYED_MS
    }

    /**
     * For auto-download: from [newEpisodes], the single newest (by pubDate) of each show in [autoDownloadShows].
     * Shows come out in the order they first appear in [newEpisodes]; on a pubDate tie the first one listed wins.
     */
    fun autoDownloadPicks(newEpisodes: List<Episode>, autoDownloadShows: Set<String>): List<Episode> =
        newEpisodes
            .filter { it.podcastUrl in autoDownloadShows }
            .groupBy { it.podcastUrl }
            .values
            .map { episodes -> episodes.maxBy { it.pubDate } }

    /** Notification text for a refresh: null when [count] <= 0; "1 new episode"; "N new episodes". */
    fun newEpisodesTitle(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "1 new episode"
        else -> "$count new episodes"
    }
}
