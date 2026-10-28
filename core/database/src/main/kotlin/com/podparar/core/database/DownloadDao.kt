package com.podparar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DownloadDao {
    @Query("SELECT * FROM downloads")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    suspend fun download(episodeId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)

    @Query("DELETE FROM downloads WHERE episodeId IN (SELECT id FROM episodes WHERE podcastUrl = :feedUrl)")
    suspend fun deleteForPodcast(feedUrl: String)
}
