package com.podparar.core.model

/** What's loaded in the player, for the UI. Positions in ms; durationMs 0 = unknown. */
data class NowPlaying(
    val episodeId: String,
    val podcastUrl: String,
    val title: String,
    val podcastTitle: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
)
