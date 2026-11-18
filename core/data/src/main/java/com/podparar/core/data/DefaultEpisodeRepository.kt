package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeAge
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.PlaybackRules
import com.podparar.core.model.Podcast
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class DefaultEpisodeRepository @Inject constructor(
    private val store: PodcastStore,
    private val clock: Clock,
) : EpisodeRepository {

    override val continueListening: Flow<List<EpisodeWithPodcast>> = store.observeInProgress(CONTINUE_LISTENING_LIMIT)

    override val newEpisodes: Flow<List<EpisodeWithPodcast>> = flow {
        // The cutoff is recomputed on every collection, so the one-month window moves with the clock.
        emitAll(store.observeNewEpisodes(NEW_EPISODES_LIMIT, EpisodeAge.cutoffMs(clock.millis())))
    }

    override fun episode(id: String): Flow<Episode?> = store.observeEpisode(id)

    override suspend fun getEpisode(id: String): Episode? = store.episode(id)

    override suspend fun getPodcast(feedUrl: String): Podcast? = store.podcast(feedUrl)

    override suspend fun savePosition(id: String, positionMs: Long, durationMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        if (PlaybackRules.isPlayed(position, durationMs)) {
            if (durationMs > 0) store.savePosition(id, position, durationMs, clock.millis())
            store.setPlayed(id, true)
        } else {
            store.savePosition(id, position, durationMs, clock.millis())
        }
    }

    override suspend fun setPlayed(id: String, played: Boolean) {
        store.setPlayed(id, played)
    }

    override suspend fun markAllPlayed(feedUrl: String) {
        store.markAllPlayed(feedUrl)
    }

    private companion object {
        const val CONTINUE_LISTENING_LIMIT = 10
        const val NEW_EPISODES_LIMIT = 50
    }
}
