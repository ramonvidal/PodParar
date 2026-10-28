package com.podparar.core.model

/**
 * A show the listener follows, identified by its feed URL. [lastRefreshed] is epoch ms, 0 = never.
 * [autoDownload]: the newest episode found by each refresh is downloaded automatically.
 * [speed]: the show's own playback speed; null = use the last speed used.
 */
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val lastRefreshed: Long = 0L,
    val autoDownload: Boolean = false,
    val speed: Float? = null,
)
