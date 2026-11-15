package com.podparar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY position ASC")
    fun observe(episodeId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY position ASC")
    suspend fun chapters(episodeId: String): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)

    @Query("DELETE FROM chapters WHERE episodeId IN (SELECT id FROM episodes WHERE podcastUrl = :feedUrl)")
    suspend fun deleteForPodcast(feedUrl: String)
}
