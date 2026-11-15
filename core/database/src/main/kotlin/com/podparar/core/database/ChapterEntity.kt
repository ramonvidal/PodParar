package com.podparar.core.database

import androidx.room.Entity
import com.podparar.core.model.Chapter

@Entity(tableName = "chapters", primaryKeys = ["episodeId", "position"])
internal data class ChapterEntity(
    val episodeId: String,
    val position: Int,
    val startMs: Long,
    val title: String,
    val imageUrl: String?,
    val url: String?,
)

internal fun ChapterEntity.toModel(): Chapter = Chapter(startMs = startMs, title = title, imageUrl = imageUrl, url = url)

internal fun Chapter.toEntity(episodeId: String, position: Int): ChapterEntity = ChapterEntity(
    episodeId = episodeId,
    position = position,
    startMs = startMs,
    title = title,
    imageUrl = imageUrl,
    url = url,
)
