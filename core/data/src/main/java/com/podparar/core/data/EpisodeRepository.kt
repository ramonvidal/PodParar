package com.podparar.core.data

import com.podparar.core.model.Episode
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.Flow

/** One episode's playback state: where the listener is and whether it's played. */
interface EpisodeRepository {
    /** The stored episode; null while it isn't stored. */
    fun episode(id: String): Flow<Episode?>

    suspend fun getEpisode(id: String): Episode?

    suspend fun getPodcast(feedUrl: String): Podcast?

    /**
     * Saves where playback is. If PlaybackRules.isPlayed(positionMs, durationMs) the episode is marked played
     * (and a known duration is stored first); otherwise the position (never below 0) is stored with the current time.
     */
    suspend fun savePosition(id: String, positionMs: Long, durationMs: Long)

    /** Marks played or unplayed; either way the position goes back to 0. */
    suspend fun setPlayed(id: String, played: Boolean)
}
