package com.podparar.core.data

import com.podparar.core.model.Download
import java.io.File
import kotlinx.coroutines.flow.Flow

/** Episode downloads as stored on this phone. Moving files around is the caller's job; this keeps the rows. */
interface DownloadRepository {
    /** Every download, keyed by episodeId. */
    val downloads: Flow<Map<String, Download>>

    suspend fun get(episodeId: String): Download?

    suspend fun all(): List<Download>

    /** Marks QUEUED (bytes 0) — called when a download is requested. */
    suspend fun markQueued(episodeId: String)

    /**
     * Downloads the episode's audio into [target] via AudioDownloader, updating the row RUNNING with progress (at most
     * every 500 ms), then DONE with filePath; on failure FAILED and rethrows. Unknown episode: FAILED,
     * PodcastException(NOT_FOUND).
     */
    suspend fun fetch(episodeId: String, target: File)

    /** Removes the row (file deletion is the caller's job). */
    suspend fun remove(episodeId: String)

    /** The local file path if the download is DONE, else null. */
    suspend fun localPath(episodeId: String): String?
}
