package com.podparar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface QueueDao {
    /** Inner joins: ids whose episode (or show) no longer exists are left out. */
    @Query(
        "SELECT episodes.*, podcasts.title AS podcastTitle, podcasts.artworkUrl AS podcastArtworkUrl " +
            "FROM queue " +
            "INNER JOIN episodes ON episodes.id = queue.episodeId " +
            "INNER JOIN podcasts ON podcasts.feedUrl = episodes.podcastUrl " +
            "ORDER BY queue.position ASC",
    )
    fun observe(): Flow<List<EpisodeWithPodcastRow>>

    @Query("SELECT episodeId FROM queue ORDER BY position ASC")
    suspend fun ids(): List<String>

    @Insert
    suspend fun insertAll(entries: List<QueueEntity>)

    @Query("DELETE FROM queue")
    suspend fun clear()

    @Query("DELETE FROM queue WHERE episodeId IN (SELECT id FROM episodes WHERE podcastUrl = :feedUrl)")
    suspend fun deleteForPodcast(feedUrl: String)
}
