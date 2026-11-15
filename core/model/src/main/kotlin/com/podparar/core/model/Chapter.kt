package com.podparar.core.model

/** One chapter of an episode (Podcasting 2.0). [startMs] is the offset from the start of the audio. */
data class Chapter(
    val startMs: Long,
    val title: String,
    val imageUrl: String? = null,
    val url: String? = null,
)
