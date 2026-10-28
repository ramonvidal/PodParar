package com.podparar.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One "Up next" entry; [position] is its place in the queue, 0 first. */
@Entity(tableName = "queue")
internal data class QueueEntity(
    @PrimaryKey val episodeId: String,
    val position: Int,
)
