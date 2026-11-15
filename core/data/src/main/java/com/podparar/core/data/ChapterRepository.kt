package com.podparar.core.data

import com.podparar.core.model.Chapter
import kotlinx.coroutines.flow.Flow

/** Chapters (Podcasting 2.0) of episodes: stored on this phone, fetched from the feed's chapters file once. */
interface ChapterRepository {
    fun chapters(episodeId: String): Flow<List<Chapter>>

    /**
     * If the episode has a chaptersUrl and no chapters are stored, downloads and stores them. Errors are swallowed
     * (CancellationException is rethrown). True when chapters are stored afterwards.
     */
    suspend fun ensureChapters(episodeId: String): Boolean
}
