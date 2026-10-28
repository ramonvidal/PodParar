package com.podparar.core.data

import com.podparar.core.model.EpisodeWithPodcast
import kotlinx.coroutines.flow.Flow

/** "Up next": the episodes to play after the current one, in order. Never holds the same episode twice. */
interface QueueRepository {
    /** The queue in order, with each episode's show. */
    val queue: Flow<List<EpisodeWithPodcast>>

    /** Puts [episodeId] at the front (moving it if already queued). */
    suspend fun playNext(episodeId: String)

    /** Appends [episodeId] at the end; no change if already queued. */
    suspend fun add(episodeId: String)

    suspend fun remove(episodeId: String)

    /** Moves the item at [from] to index [to] (Queue.move rules). */
    suspend fun move(from: Int, to: Int)

    /** Removes and returns the first queued id (Queue.takeNext), or null when empty. */
    suspend fun takeNext(): String?
}
