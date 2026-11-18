package com.podparar.core.data

import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
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

    /** In-progress episodes for "Continue listening" (store.observeInProgress(10)). */
    val continueListening: Flow<List<EpisodeWithPodcast>>

    /**
     * Unplayed, unstarted episodes for "New episodes" with the default size (HomeLimit.DEFAULT, 50);
     * same as observeNewEpisodes(HomeLimit.DEFAULT). Used by Android Auto.
     */
    val newEpisodes: Flow<List<EpisodeWithPodcast>>

    /**
     * Unplayed, unstarted episodes for "New episodes", at most [limit]: downloaded ones always show, the
     * remaining slots go to the newest non-downloaded ones; older ones are dropped. pubDate DESC, id ASC.
     */
    fun observeNewEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>>

    /** Marks every episode of that show played (positions back to 0). */
    suspend fun markAllPlayed(feedUrl: String)
}
