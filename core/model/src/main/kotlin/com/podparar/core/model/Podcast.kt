package com.podparar.core.model

/** A show the listener follows, identified by its feed URL. [lastRefreshed] is epoch ms, 0 = never. */
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val lastRefreshed: Long = 0L,
)
