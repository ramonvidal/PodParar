package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.Queue
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Each change reads the stored ids, applies a Queue rule and writes them back, one change at a time. */
class DefaultQueueRepository @Inject constructor(
    private val store: PodcastStore,
) : QueueRepository {

    private val mutex = Mutex()

    override val queue: Flow<List<EpisodeWithPodcast>> = store.observeQueue()

    override suspend fun playNext(episodeId: String) = update { Queue.playNext(it, episodeId) }

    override suspend fun add(episodeId: String) = update { Queue.add(it, episodeId) }

    override suspend fun remove(episodeId: String) = update { Queue.remove(it, episodeId) }

    override suspend fun move(from: Int, to: Int) = update { Queue.move(it, from, to) }

    override suspend fun takeNext(): String? = mutex.withLock {
        val current = store.queueIds()
        val (next, rest) = Queue.takeNext(current)
        if (rest != current) store.setQueue(rest)
        next
    }

    private suspend fun update(change: (List<String>) -> List<String>) {
        mutex.withLock {
            val current = store.queueIds()
            val updated = change(current)
            if (updated != current) store.setQueue(updated)
        }
    }
}
