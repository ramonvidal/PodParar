package com.podparar.core.database

import com.podparar.core.model.Episode
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

    /** Removes the podcast and all its episodes. */
    suspend fun deletePodcast(feedUrl: String)

    /** One episode by id; null while it isn't stored. */
    fun observeEpisode(id: String): Flow<Episode?>

    suspend fun episode(id: String): Episode?

    suspend fun podcast(feedUrl: String): Podcast?

    /** Updates positionMs and lastPlayedAt; durationMs only when [durationMs] > 0. Unknown id: no-op. */
    suspend fun savePosition(id: String, positionMs: Long, durationMs: Long, playedAt: Long)

    /** Sets played and resets positionMs to 0 (both ways). Unknown id: no-op. */
    suspend fun setPlayed(id: String, played: Boolean)
}
