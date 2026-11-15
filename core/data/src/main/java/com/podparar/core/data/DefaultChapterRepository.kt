package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.Chapter
import com.podparar.core.network.ChapterSource
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

class DefaultChapterRepository @Inject constructor(
    private val store: PodcastStore,
    private val source: ChapterSource,
) : ChapterRepository {

    override fun chapters(episodeId: String): Flow<List<Chapter>> = store.observeChapters(episodeId)

    override suspend fun ensureChapters(episodeId: String): Boolean {
        return try {
            if (store.chapters(episodeId).isNotEmpty()) return true
            val url = store.episode(episodeId)?.chaptersUrl ?: return false
            val fetched = source.fetch(url)
            if (fetched.isEmpty()) return false
            store.saveChapters(episodeId, fetched)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
