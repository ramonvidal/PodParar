package com.podparar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastUrl = :feedUrl ORDER BY pubDate DESC")
    fun observeFor(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastUrl = :feedUrl ORDER BY pubDate DESC")
    suspend fun episodesFor(feedUrl: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observe(id: String): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun episode(id: String): EpisodeEntity?

    /** Duration is only overwritten when a known (> 0) one is passed. */
    @Query(
        "UPDATE episodes SET positionMs = :positionMs, lastPlayedAt = :playedAt, " +
            "durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END WHERE id = :id",
    )
    suspend fun savePosition(id: String, positionMs: Long, durationMs: Long, playedAt: Long)

    @Query("UPDATE episodes SET played = :played, positionMs = 0 WHERE id = :id")
    suspend fun setPlayed(id: String, played: Boolean)

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE podcastUrl = :feedUrl")
    suspend fun deleteFor(feedUrl: String)
}
