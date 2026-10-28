package com.podparar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE ASC, feedUrl ASC")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    fun observe(feedUrl: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun podcast(feedUrl: String): PodcastEntity?

    @Query("SELECT feedUrl FROM podcasts ORDER BY title COLLATE NOCASE ASC, feedUrl ASC")
    suspend fun feedUrls(): List<String>

    @Query("UPDATE podcasts SET autoDownload = :enabled WHERE feedUrl = :feedUrl")
    suspend fun setAutoDownload(feedUrl: String, enabled: Boolean)

    @Query("UPDATE podcasts SET speed = :speed WHERE feedUrl = :feedUrl")
    suspend fun setSpeed(feedUrl: String, speed: Float?)

    @Upsert
    suspend fun upsert(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun delete(feedUrl: String)
}
