package com.podparar.core.downloads

import com.podparar.core.model.Download
import kotlinx.coroutines.flow.Flow

/** Episode downloads for the screens: what's downloaded, and asking for or removing one. */
interface EpisodeDownloads {
    /** Every download row, keyed by episode id. */
    val downloads: Flow<Map<String, Download>>

    /** Queues a download (unique work "download-<id>", KEEP), on unmetered networks when wifiOnly, else any network. */
    fun download(episodeId: String)

    /** Cancels any work, deletes the file and the row. */
    fun remove(episodeId: String)

    /** Deletes the files of every download of that show (call before unsubscribing). */
    suspend fun removeAllFor(feedUrl: String, episodeIds: List<String>)
}
