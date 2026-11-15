package com.podparar.core.network

import com.podparar.core.model.Chapter

/** Downloads chapter files. */
interface ChapterSource {
    /**
     * Downloads and parses a chapters JSON file. Throws [com.podparar.core.model.PodcastException]: OFFLINE (IOException),
     * NOT_FOUND (404/410), NOT_A_FEED (not JSON / no chapters array), UNKNOWN (other HTTP errors, > 1 MB, not an http(s) link).
     * The result is [com.podparar.core.model.Chapters.normalize]d.
     */
    suspend fun fetch(url: String): List<Chapter>
}
