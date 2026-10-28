package com.podparar.core.database

import androidx.room.Embedded
import com.podparar.core.model.EpisodeWithPodcast

/** An episode row joined with its show's title and artwork (see the joined queries in EpisodeDao and QueueDao). */
internal data class EpisodeWithPodcastRow(
    @Embedded val episode: EpisodeEntity,
    val podcastTitle: String,
    val podcastArtworkUrl: String?,
)

internal fun EpisodeWithPodcastRow.toModel(): EpisodeWithPodcast = EpisodeWithPodcast(
    episode = episode.toModel(),
    podcastTitle = podcastTitle,
    podcastArtworkUrl = podcastArtworkUrl,
)
