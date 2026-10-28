package com.podparar.core.network

import com.podparar.core.model.ParsedFeed

/** Downloads podcast feeds. */
interface FeedSource {
    /**
     * Downloads and parses a feed. Throws [com.podparar.core.model.PodcastException]: OFFLINE (no connection),
     * NOT_FOUND (HTTP 404/410), NOT_A_FEED (not RSS / parse failure), UNKNOWN (other HTTP errors).
     */
    suspend fun fetch(feedUrl: String): ParsedFeed
}
