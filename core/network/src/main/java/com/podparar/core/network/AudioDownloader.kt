package com.podparar.core.network

import java.io.File

/** Downloads episode audio files. */
interface AudioDownloader {
    /**
     * Streams [url] into [target] (overwriting), calling [onProgress](bytesSoFar, totalBytes or 0) at most every
     * 500 ms and at the end. Throws [com.podparar.core.model.PodcastException]: OFFLINE (connection or I/O failure),
     * NOT_FOUND (HTTP 404/410), UNKNOWN (other non-2xx), INVALID_LINK (not an http(s) URL).
     * Cancellation stops the transfer and deletes the partial file.
     */
    suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit)
}
