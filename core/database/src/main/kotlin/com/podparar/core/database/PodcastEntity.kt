package com.podparar.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.podparar.core.model.Podcast

@Entity(tableName = "podcasts")
internal data class PodcastEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val lastRefreshed: Long,
)

internal fun PodcastEntity.toModel(): Podcast = Podcast(
    feedUrl = feedUrl,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    lastRefreshed = lastRefreshed,
)

internal fun Podcast.toEntity(): PodcastEntity = PodcastEntity(
    feedUrl = feedUrl,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    lastRefreshed = lastRefreshed,
)
