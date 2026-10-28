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
