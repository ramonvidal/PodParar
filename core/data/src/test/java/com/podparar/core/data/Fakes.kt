package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.Episode
import com.podparar.core.model.FeedMerge
import com.podparar.core.model.LoadError
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
import com.podparar.core.network.FeedSource
import com.podparar.core.network.PodcastDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal fun podcast(feedUrl: String, title: String = "Show at $feedUrl") = Podcast(
    feedUrl = feedUrl,
    title = title,
    author = "Author",
    description = "About the show",
    artworkUrl = null,
    lastRefreshed = 1_000L,
)

internal fun episode(feedUrl: String, guid: String, pubDate: Long = 1_000L) = Episode(
    id = "$feedUrl#$guid",
    podcastUrl = feedUrl,
    title = "Episode $guid",
    description = "",
    audioUrl = "https://cdn.example.com/$guid.mp3",
    imageUrl = null,
    pubDate = pubDate,
    durationMs = 60_000L,
)

/** Returns a feed for any URL (its podcast's feedUrl is the URL asked for), unless told to fail for it. */
internal class FakeFeedSource : FeedSource {
    val fetched = mutableListOf<String>()
    val failures = mutableMapOf<String, LoadError>()
    val feeds = mutableMapOf<String, ParsedFeed>()
    var delayMs = 0L
    var inFlight = 0
        private set
    var maxInFlight = 0
        private set

    override suspend fun fetch(feedUrl: String): ParsedFeed {
        synchronized(this) {
            fetched += feedUrl
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
        }
        try {
            if (delayMs > 0) delay(delayMs)
            failures[feedUrl]?.let { throw PodcastException(it) }
            return feeds[feedUrl] ?: ParsedFeed(podcast(feedUrl), listOf(episode(feedUrl, "1")))
        } finally {
            synchronized(this) { inFlight-- }
        }
    }
}

internal class FakePodcastDirectory : PodcastDirectory {
    val terms = mutableListOf<String>()
    var results: List<SearchResult> = emptyList()
    var failure: LoadError? = null

    override suspend fun search(term: String): List<SearchResult> {
        terms += term
        failure?.let { throw PodcastException(it) }
        return results
    }
}

internal class FakePodcastStore : PodcastStore {
    private val podcasts = MutableStateFlow<Map<String, Podcast>>(emptyMap())
    private val episodes = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())
    val saved = mutableListOf<ParsedFeed>()
    val deleted = mutableListOf<String>()

    /** Puts a show in the store without counting it as a save. */
    fun seed(podcast: Podcast, episodes: List<Episode> = emptyList()) {
        podcasts.value = podcasts.value + (podcast.feedUrl to podcast)
        this.episodes.value = this.episodes.value + (podcast.feedUrl to episodes)
    }

    override fun observePodcasts(): Flow<List<Podcast>> =
        podcasts.map { map -> map.values.sortedBy { it.title.lowercase() } }

    override fun observePodcast(feedUrl: String): Flow<Podcast?> = podcasts.map { it[feedUrl] }

    override fun observeEpisodes(feedUrl: String): Flow<List<Episode>> =
        episodes.map { map -> map[feedUrl].orEmpty().sortedByDescending { it.pubDate } }

    override suspend fun podcastUrls(): List<String> = podcasts.value.keys.toList()

    override suspend fun episodes(feedUrl: String): List<Episode> = episodes.value[feedUrl].orEmpty()

    override suspend fun saveFeed(feed: ParsedFeed) {
        synchronized(this) {
            saved += feed
            val url = feed.podcast.feedUrl
            podcasts.value = podcasts.value + (url to feed.podcast)
            episodes.value = episodes.value + (url to FeedMerge.merge(episodes.value[url].orEmpty(), feed.episodes))
        }
    }

    override suspend fun deletePodcast(feedUrl: String) {
        synchronized(this) {
            deleted += feedUrl
            podcasts.value = podcasts.value - feedUrl
            episodes.value = episodes.value - feedUrl
        }
    }
}
