package com.podparar.core.data

import com.podparar.core.database.PodcastStore
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.network.AudioDownloader
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefaultDownloadRepository @Inject constructor(
    private val store: PodcastStore,
    private val downloader: AudioDownloader,
    private val clock: Clock,
) : DownloadRepository {

    override val downloads: Flow<Map<String, Download>> = store.observeDownloads()

    override suspend fun get(episodeId: String): Download? = store.download(episodeId)

    override suspend fun all(): List<Download> = store.downloads()

    override suspend fun markQueued(episodeId: String) {
        store.upsertDownload(row(episodeId, DownloadState.QUEUED, bytes = 0L, total = 0L))
    }

    override suspend fun fetch(episodeId: String, target: File) {
        val episode = store.episode(episodeId)
        if (episode == null) {
            store.upsertDownload(row(episodeId, DownloadState.FAILED, bytes = 0L, total = 0L))
            throw PodcastException(LoadError.NOT_FOUND)
        }
        var bytes = 0L
        var total = 0L
        try {
            store.upsertDownload(row(episodeId, DownloadState.RUNNING, bytes = 0L, total = 0L))
            coroutineScope {
                // onProgress isn't a suspend function, so progress goes through a conflated channel to a writer
                // coroutine; the downloader already calls it at most every 500 ms.
                val progress = Channel<Pair<Long, Long>>(Channel.CONFLATED)
                val writer = launch {
                    for ((soFar, size) in progress) {
                        store.upsertDownload(row(episodeId, DownloadState.RUNNING, bytes = soFar, total = size))
                    }
                }
                try {
                    downloader.download(episode.audioUrl, target) { soFar, size ->
                        bytes = soFar
                        total = size
                        progress.trySend(soFar to size)
                    }
                } finally {
                    progress.close()
                }
                writer.join()
            }
            val size = if (total > 0L) total else bytes
            store.upsertDownload(
                row(episodeId, DownloadState.DONE, bytes = size, total = size, filePath = target.absolutePath),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) {
                store.upsertDownload(row(episodeId, DownloadState.FAILED, bytes = bytes, total = total))
            }
            throw e
        }
    }

    override suspend fun remove(episodeId: String) {
        store.deleteDownload(episodeId)
    }

    override suspend fun localPath(episodeId: String): String? {
        val download = store.download(episodeId) ?: return null
        return if (download.state == DownloadState.DONE) download.filePath else null
    }

    private fun row(
        episodeId: String,
        state: DownloadState,
        bytes: Long,
        total: Long,
        filePath: String? = null,
    ): Download = Download(
        episodeId = episodeId,
        state = state,
        filePath = filePath,
        bytesDownloaded = bytes,
        totalBytes = total,
        updatedAt = clock.millis(),
    )
}
