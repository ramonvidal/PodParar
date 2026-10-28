package com.podparar.core.data

import com.podparar.core.model.Episode
import com.podparar.core.model.Podcast
import com.podparar.core.model.SearchResult
import kotlinx.coroutines.flow.Flow

/** Subscriptions and their episodes. The database is the source of truth; network calls only write to it. */
interface PodcastRepository {
    /** Subscribed shows, sorted by title. */
    val podcasts: Flow<List<Podcast>>

    /** Feed URLs of every subscribed show. */
    val subscribedFeedUrls: Flow<Set<String>>

    fun podcast(feedUrl: String): Flow<Podcast?>

    /** Newest first. */
    fun episodes(feedUrl: String): Flow<List<Episode>>

    /** Trims; blank -> empty list. */
    suspend fun search(term: String): List<SearchResult>

    /** FeedUrls.normalize(link) (null -> PodcastException(INVALID_LINK)), fetch, store.saveFeed; returns the stored podcast. */
    suspend fun subscribe(link: String): Podcast

    /** Fetch + saveFeed for one show; errors propagate as PodcastException. */
    suspend fun refresh(feedUrl: String)

    /** Refreshes every subscription, at most 4 at once; one failure doesn't stop the others. Returns how many failed. */
    suspend fun refreshAll(): Int

    suspend fun unsubscribe(feedUrl: String)
}
