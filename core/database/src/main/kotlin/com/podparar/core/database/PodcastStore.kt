package com.podparar.core.database

import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.Flow

/** The subscribed shows and their episodes as stored on this phone. Only model types cross this boundary. */
interface PodcastStore {
    /** Sorted by title, case-insensitive. */
    fun observePodcasts(): Flow<List<Podcast>>

    fun observePodcast(feedUrl: String): Flow<Podcast?>

    /** Newest first by pubDate. */
    fun observeEpisodes(feedUrl: String): Flow<List<Episode>>

    suspend fun podcastUrls(): List<String>

    suspend fun episodes(feedUrl: String): List<Episode>

    /** In one transaction: upserts the podcast and stores FeedMerge.merge(stored episodes, [episodes]). */
    suspend fun saveFeed(feed: ParsedFeed)

    /** Removes the podcast, all its episodes and their queue entries. */
    suspend fun deletePodcast(feedUrl: String)

    /** One episode by id; null while it isn't stored. */
    fun observeEpisode(id: String): Flow<Episode?>

    suspend fun episode(id: String): Episode?

    suspend fun podcast(feedUrl: String): Podcast?

    /** Updates positionMs and lastPlayedAt; durationMs only when [durationMs] > 0. Unknown id: no-op. */
    suspend fun savePosition(id: String, positionMs: Long, durationMs: Long, playedAt: Long)

    /** Sets played and resets positionMs to 0 (both ways). Unknown id: no-op. */
    suspend fun setPlayed(id: String, played: Boolean)

    /** Up next, in order, with each episode's show. Ids whose episode no longer exists are left out. */
    fun observeQueue(): Flow<List<EpisodeWithPodcast>>

    suspend fun queueIds(): List<String>

    /** Replaces the whole queue with [ids] in this order (positions 0..n-1), in one transaction. */
    suspend fun setQueue(ids: List<String>)

    /** played = 0 AND positionMs > 0, by lastPlayedAt DESC, at most [limit]. */
    fun observeInProgress(limit: Int): Flow<List<EpisodeWithPodcast>>

    /** played = 0 AND positionMs = 0, by pubDate DESC, at most [limit]. */
    fun observeNewEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>>

    /** Every episode of that show: played = 1, positionMs = 0. */
    suspend fun markAllPlayed(feedUrl: String)
}
