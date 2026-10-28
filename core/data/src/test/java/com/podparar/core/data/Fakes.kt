package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

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

    /** Every playback write, in order, e.g. "savePosition(id, 1000, 60000, 5)" or "setPlayed(id, true)". */
    val playbackCalls = mutableListOf<String>()

    private fun updateEpisode(id: String, change: (Episode) -> Episode) {
        val url = episodes.value.entries.firstOrNull { (_, list) -> list.any { it.id == id } }?.key ?: return
        episodes.value = episodes.value + (url to episodes.value.getValue(url).map { if (it.id == id) change(it) else it })
    }

    private fun findEpisode(id: String): Episode? = episodes.value.values.flatten().firstOrNull { it.id == id }

    override fun observeEpisode(id: String): Flow<Episode?> =
        episodes.map { map -> map.values.flatten().firstOrNull { it.id == id } }

    override suspend fun episode(id: String): Episode? = findEpisode(id)

    override suspend fun podcast(feedUrl: String): Podcast? = podcasts.value[feedUrl]

    override suspend fun savePosition(id: String, positionMs: Long, durationMs: Long, playedAt: Long) {
        synchronized(this) {
            playbackCalls += "savePosition($id, $positionMs, $durationMs, $playedAt)"
            updateEpisode(id) {
                it.copy(
                    positionMs = positionMs,
                    lastPlayedAt = playedAt,
                    durationMs = if (durationMs > 0) durationMs else it.durationMs,
                )
            }
        }
    }

    override suspend fun setPlayed(id: String, played: Boolean) {
        synchronized(this) {
            playbackCalls += "setPlayed($id, $played)"
            updateEpisode(id) { it.copy(played = played, positionMs = 0L) }
        }
    }

    override suspend fun deletePodcast(feedUrl: String) {
        synchronized(this) {
            deleted += feedUrl
            val gone = episodes.value[feedUrl].orEmpty().map { it.id }.toSet()
            podcasts.value = podcasts.value - feedUrl
            episodes.value = episodes.value - feedUrl
            queue.value = queue.value.filterNot { it in gone }
        }
    }

    // --- Phase 3: queue and Home lists ---

    private val queue = MutableStateFlow<List<String>>(emptyList())

    /** Every setQueue call's ids, in order. */
    val setQueueCalls = mutableListOf<List<String>>()

    /** The limits observeInProgress / observeNewEpisodes were asked for. */
    val inProgressLimits = mutableListOf<Int>()
    val newEpisodesLimits = mutableListOf<Int>()

    /** Every markAllPlayed call's feed URL. */
    val markAllPlayedCalls = mutableListOf<String>()

    /** Puts ids in the queue without counting it as a setQueue call. */
    fun seedQueue(ids: List<String>) {
        queue.value = ids
    }

    private fun withPodcast(episode: Episode, shows: Map<String, Podcast>): EpisodeWithPodcast? {
        val show = shows[episode.podcastUrl] ?: return null
        return EpisodeWithPodcast(episode, show.title, show.artworkUrl)
    }

    private fun joined(): Flow<List<EpisodeWithPodcast>> =
        combine(podcasts, episodes) { shows, eps -> eps.values.flatten().mapNotNull { withPodcast(it, shows) } }

    override fun observeQueue(): Flow<List<EpisodeWithPodcast>> =
        combine(queue, joined()) { ids, all ->
            val byId = all.associateBy { it.episode.id }
            ids.mapNotNull { byId[it] }
        }

    override suspend fun queueIds(): List<String> {
        val ids = queue.value
        // Gives other coroutines a chance to run between a read and the write that follows it,
        // so a repository that doesn't serialise its read-modify-write loses updates.
        yield()
        return ids
    }

    override suspend fun setQueue(ids: List<String>) {
        yield()
        synchronized(this) {
            setQueueCalls += ids
            queue.value = ids.toList()
        }
    }

    override fun observeInProgress(limit: Int): Flow<List<EpisodeWithPodcast>> {
        synchronized(this) { inProgressLimits += limit }
        return joined().map { all ->
            all.filter { !it.episode.played && it.episode.positionMs > 0 }
                .sortedByDescending { it.episode.lastPlayedAt }
                .take(limit)
        }
    }

    override fun observeNewEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>> {
        synchronized(this) { newEpisodesLimits += limit }
        return joined().map { all ->
            all.filter { !it.episode.played && it.episode.positionMs == 0L }
                .sortedByDescending { it.episode.pubDate }
                .take(limit)
        }
    }

    override suspend fun markAllPlayed(feedUrl: String) {
        synchronized(this) {
            markAllPlayedCalls += feedUrl
            val list = episodes.value[feedUrl] ?: return
            episodes.value = episodes.value + (feedUrl to list.map { it.copy(played = true, positionMs = 0L) })
        }
    }
}
