package com.podparar.core.database

import androidx.room.withTransaction
import com.podparar.core.model.Episode
import com.podparar.core.model.FeedMerge
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomPodcastStore(private val db: PodPararDatabase) : PodcastStore {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()

    override fun observePodcasts(): Flow<List<Podcast>> =
        podcastDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observePodcast(feedUrl: String): Flow<Podcast?> =
        podcastDao.observe(feedUrl).map { it?.toModel() }

    override fun observeEpisodes(feedUrl: String): Flow<List<Episode>> =
        episodeDao.observeFor(feedUrl).map { list -> list.map { it.toModel() } }

    override suspend fun podcastUrls(): List<String> = podcastDao.feedUrls()

    override suspend fun episodes(feedUrl: String): List<Episode> =
        episodeDao.episodesFor(feedUrl).map { it.toModel() }

    override suspend fun saveFeed(feed: ParsedFeed) {
        val feedUrl = feed.podcast.feedUrl
        db.withTransaction {
            podcastDao.upsert(feed.podcast.toEntity())
            val stored = episodeDao.episodesFor(feedUrl).map { it.toModel() }
            // Episodes are always stored under the podcast they were saved with.
            val fresh = feed.episodes.map { if (it.podcastUrl == feedUrl) it else it.copy(podcastUrl = feedUrl) }
            val merged = FeedMerge.merge(stored, fresh)
            if (merged.isNotEmpty()) episodeDao.upsertAll(merged.map { it.toEntity() })
        }
    }

    override suspend fun deletePodcast(feedUrl: String) {
        db.withTransaction {
            episodeDao.deleteFor(feedUrl)
            podcastDao.delete(feedUrl)
        }
    }

    override fun observeEpisode(id: String): Flow<Episode?> =
        episodeDao.observe(id).map { it?.toModel() }

    override suspend fun episode(id: String): Episode? = episodeDao.episode(id)?.toModel()

    override suspend fun podcast(feedUrl: String): Podcast? = podcastDao.podcast(feedUrl)?.toModel()

    override suspend fun savePosition(id: String, positionMs: Long, durationMs: Long, playedAt: Long) {
        episodeDao.savePosition(id, positionMs, durationMs, playedAt)
    }

    override suspend fun setPlayed(id: String, played: Boolean) {
        episodeDao.setPlayed(id, played)
    }
}
