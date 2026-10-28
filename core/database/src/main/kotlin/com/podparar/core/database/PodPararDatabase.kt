package com.podparar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** Schemas are exported to core/database/schemas; every version bump needs a migration and a migration test. */
@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueEntity::class, DownloadEntity::class],
    version = 4,
    exportSchema = true,
)
internal abstract class PodPararDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "podparar.db"
    }
}
