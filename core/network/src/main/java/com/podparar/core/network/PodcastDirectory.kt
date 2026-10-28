package com.podparar.core.network

import com.podparar.core.model.SearchResult

/** A directory of shows to search (Apple Podcasts). */
interface PodcastDirectory {
    /**
     * Apple Podcasts search. Blank term -> empty list without a request.
     * Throws [com.podparar.core.model.PodcastException] (OFFLINE / UNKNOWN).
     */
    suspend fun search(term: String): List<SearchResult>
}
