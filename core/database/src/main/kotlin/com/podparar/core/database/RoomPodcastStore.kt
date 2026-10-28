package com.podparar.core.database

import androidx.room.withTransaction
import com.podparar.core.model.Download
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.FeedMerge
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomPodcastStore(private val db: PodPararDatabase) : PodcastStore {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()
    private val queueDao = db.queueDao()
    private val downloadDao = db.downloadDao()

    override fun observePodcasts(): Flow<List<Podcast>> =
        podcastDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observePodcast(feedUrl: String): Flow<Podcast?> =
        podcastDao.observe(feedUrl).map { it?.toModel() }

    override fun observeEpisodes(feedUrl: String): Flow<List<Episode>> =
        episodeDao.observeFor(feedUrl).map { list -> list.map { it.toModel() } }

    override suspend fun podcastUrls(): List<String> = podcastDao.feedUrls()

    override suspend fun episodes(feedUrl: String): List<Episode> =
        episodeDao.episodesFor(feedUrl).map { it.toModel() }

    override suspend fun saveFeed(feed: ParsedFeed): List<Episode> {
        val feedUrl = feed.podcast.feedUrl
        return db.withTransaction {
            // A parsed feed never knows this phone's auto-download choice, so the stored one wins.
            val autoDownload = podcastDao.podcast(feedUrl)?.autoDownload ?: false
            podcastDao.upsert(feed.podcast.toEntity().copy(autoDownload = autoDownload))
            val stored = episodeDao.episodesFor(feedUrl).map { it.toModel() }
            val storedIds = stored.mapTo(HashSet()) { it.id }
            // Episodes are always stored under the podcast they were saved with.
            val fresh = feed.episodes.map { if (it.podcastUrl == feedUrl) it else it.copy(podcastUrl = feedUrl) }
            val merged = FeedMerge.merge(stored, fresh)
            if (merged.isNotEmpty()) episodeDao.upsertAll(merged.map { it.toEntity() })
            merged.filter { it.id !in storedIds }
        }
    }

    override suspend fun setAutoDownload(feedUrl: String, enabled: Boolean) {
        podcastDao.setAutoDownload(feedUrl, enabled)
    }

    override suspend fun deletePodcast(feedUrl: String) {
        db.withTransaction {
            queueDao.deleteForPodcast(feedUrl)
            downloadDao.deleteForPodcast(feedUrl)
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

    override fun observeQueue(): Flow<List<EpisodeWithPodcast>> =
        queueDao.observe().map { rows -> rows.map { it.toModel() } }

    override suspend fun queueIds(): List<String> = queueDao.ids()

    override suspend fun setQueue(ids: List<String>) {
        // The table's key is the episode id, so a repeated id keeps its first place.
        val entries = ids.distinct().mapIndexed { index, id -> QueueEntity(episodeId = id, position = index) }
        db.withTransaction {
            queueDao.clear()
            if (entries.isNotEmpty()) queueDao.insertAll(entries)
        }
    }

    override fun observeInProgress(limit: Int): Flow<List<EpisodeWithPodcast>> =
        episodeDao.observeInProgress(limit).map { rows -> rows.map { it.toModel() } }

    override fun observeNewEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>> =
        episodeDao.observeNew(limit).map { rows -> rows.map { it.toModel() } }

    override suspend fun markAllPlayed(feedUrl: String) {
        episodeDao.markAllPlayed(feedUrl)
    }

    override fun observeDownloads(): Flow<Map<String, Download>> =
        downloadDao.observeAll().map { rows -> rows.associate { it.episodeId to it.toModel() } }

    override suspend fun download(episodeId: String): Download? = downloadDao.download(episodeId)?.toModel()

    override suspend fun downloads(): List<Download> = downloadDao.all().map { it.toModel() }

    override suspend fun upsertDownload(download: Download) {
        downloadDao.upsert(download.toEntity())
    }

    override suspend fun deleteDownload(episodeId: String) {
        downloadDao.delete(episodeId)
    }
}
