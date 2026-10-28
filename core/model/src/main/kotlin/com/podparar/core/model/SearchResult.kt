package com.podparar.core.model

/** A show found in the directory, not necessarily subscribed. */
data class SearchResult(
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String?,
)
