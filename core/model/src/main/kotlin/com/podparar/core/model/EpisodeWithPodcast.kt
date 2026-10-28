package com.podparar.core.model

/** An episode with the show it belongs to, for lists that mix shows (Home, Up next). */
data class EpisodeWithPodcast(
    val episode: Episode,
    val podcastTitle: String,
    val podcastArtworkUrl: String?,
) {
    /** episode.imageUrl, else the show's artwork. A blank URL counts as missing. */
    val artworkUrl: String?
        get() = episode.imageUrl?.takeIf { it.isNotBlank() }
            ?: podcastArtworkUrl?.takeIf { it.isNotBlank() }
}
