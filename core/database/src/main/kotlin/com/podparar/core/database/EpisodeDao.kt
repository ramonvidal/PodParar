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

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE podcastUrl = :feedUrl")
    suspend fun deleteFor(feedUrl: String)
}
