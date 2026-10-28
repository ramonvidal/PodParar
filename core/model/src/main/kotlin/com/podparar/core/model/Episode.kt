package com.podparar.core.model

/** Where an episode stands for the listener. */
enum class EpisodeState { NEW, IN_PROGRESS, PLAYED }

/**
 * One episode of a [Podcast]. [id] is "<feedUrl>#<guid or audio url>" so it survives feed refreshes.
 * Times are epoch ms and lengths are ms; 0 means unknown (or never, for [lastPlayedAt]).
 */
data class Episode(
    val id: String,
    val podcastUrl: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val pubDate: Long,
    val durationMs: Long,
    val positionMs: Long = 0L,
    val played: Boolean = false,
    val lastPlayedAt: Long = 0L,
) {
    /** Played wins; any saved position means it's under way. */
    val state: EpisodeState
        get() = when {
            played -> EpisodeState.PLAYED
            positionMs > 0 -> EpisodeState.IN_PROGRESS
            else -> EpisodeState.NEW
        }

    companion object {
        /** "<feedUrl>#<guid>"; the trimmed guid, or the audio URL when the guid is null or blank. */
        fun idFor(feedUrl: String, guid: String?, audioUrl: String): String =
            "$feedUrl#${guid?.trim()?.takeIf { it.isNotEmpty() } ?: audioUrl}"
    }
}
