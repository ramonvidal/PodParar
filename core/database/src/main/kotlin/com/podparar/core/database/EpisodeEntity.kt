package com.podparar.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.podparar.core.model.Episode

@Entity(
    tableName = "episodes",
    indices = [Index("podcastUrl"), Index("pubDate")],
)
internal data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastUrl: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val pubDate: Long,
    val durationMs: Long,
    val positionMs: Long,
    val played: Boolean,
    val lastPlayedAt: Long,
)

internal fun EpisodeEntity.toModel(): Episode = Episode(
    id = id,
    podcastUrl = podcastUrl,
    title = title,
    description = description,
    audioUrl = audioUrl,
    imageUrl = imageUrl,
    pubDate = pubDate,
    durationMs = durationMs,
    positionMs = positionMs,
    played = played,
    lastPlayedAt = lastPlayedAt,
)

internal fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    podcastUrl = podcastUrl,
    title = title,
    description = description,
    audioUrl = audioUrl,
    imageUrl = imageUrl,
    pubDate = pubDate,
    durationMs = durationMs,
    positionMs = positionMs,
    played = played,
    lastPlayedAt = lastPlayedAt,
)
