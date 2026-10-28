package com.podparar.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2 adds the "Up next" queue table. The SQL matches what Room generates for QueueEntity. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `queue` (`episodeId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`episodeId`))",
        )
    }
}

/**
 * Version 3 adds the downloads table and the podcasts' auto-download flag (off for every existing show).
 * The SQL matches what Room generates for DownloadEntity and PodcastEntity.autoDownload.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloads` (`episodeId` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                "`filePath` TEXT, `bytesDownloaded` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`episodeId`))",
        )
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `autoDownload` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 4 adds each show's own playback speed (null for every existing show). Matches Room for PodcastEntity.speed. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `speed` REAL")
    }
}
