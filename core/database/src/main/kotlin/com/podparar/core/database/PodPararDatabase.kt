package com.podparar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** Schemas are exported to core/database/schemas; every version bump needs a migration and a migration test. */
@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class PodPararDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        const val NAME = "podparar.db"
    }
}
