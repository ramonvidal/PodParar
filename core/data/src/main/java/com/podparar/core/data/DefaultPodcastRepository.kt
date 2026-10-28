package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.Episode
import com.podparar.core.model.FeedUrls
import com.podparar.core.model.LoadError
import com.podparar.core.model.Podcast
import com.podparar.core.model.PodcastException
import com.podparar.core.model.RefreshResult
import com.podparar.core.model.SearchResult
import com.podparar.core.network.FeedSource
import com.podparar.core.network.PodcastDirectory
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DefaultPodcastRepository @Inject constructor(
    private val feeds: FeedSource,
    private val directory: PodcastDirectory,
    private val store: PodcastStore,
) : PodcastRepository {

    override val podcasts: Flow<List<Podcast>> = store.observePodcasts()

    override val subscribedFeedUrls: Flow<Set<String>> =
        store.observePodcasts()
            .map { list -> list.mapTo(LinkedHashSet()) { it.feedUrl } }
            .distinctUntilChanged()

    override fun podcast(feedUrl: String): Flow<Podcast?> = store.observePodcast(feedUrl)

    override fun episodes(feedUrl: String): Flow<List<Episode>> = store.observeEpisodes(feedUrl)

    override suspend fun search(term: String): List<SearchResult> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        return directory.search(trimmed)
    }

    override suspend fun subscribe(link: String): Podcast {
        val feedUrl = FeedUrls.normalize(link) ?: throw PodcastException(LoadError.INVALID_LINK)
        val feed = feeds.fetch(feedUrl)
        store.saveFeed(feed)
        return store.observePodcast(feed.podcast.feedUrl).first() ?: feed.podcast
    }

    override suspend fun refresh(feedUrl: String): List<Episode> = store.saveFeed(feeds.fetch(feedUrl))

    override suspend fun refreshAll(): RefreshResult {
        val urls = store.podcastUrls()
        if (urls.isEmpty()) return RefreshResult(failedFeeds = 0, newEpisodes = emptyList())
        val permits = Semaphore(MAX_PARALLEL_REFRESHES)
        // null = that feed failed.
        val results: List<List<Episode>?> = coroutineScope {
            urls.map { url ->
                async {
                    permits.withPermit {
                        try {
                            refresh(url)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        return RefreshResult(
            failedFeeds = results.count { it == null },
            newEpisodes = results.flatMap { it.orEmpty() },
        )
    }

    override suspend fun setAutoDownload(feedUrl: String, enabled: Boolean) {
        store.setAutoDownload(feedUrl, enabled)
    }

    override suspend fun unsubscribe(feedUrl: String) {
        store.deletePodcast(feedUrl)
    }

    private companion object {
        const val MAX_PARALLEL_REFRESHES = 4
    }
}
