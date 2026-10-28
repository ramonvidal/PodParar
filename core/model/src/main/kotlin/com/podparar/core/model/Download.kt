package com.podparar.core.model

/** Where an episode's download stands. */
enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

/**
 * An episode's download. [filePath] is set once it is [DownloadState.DONE]; [totalBytes] is 0 when the server
 * didn't say; [updatedAt] is epoch ms.
 */
data class Download(
    val episodeId: String,
    val state: DownloadState,
    val filePath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val updatedAt: Long,
) {
    /** [bytesDownloaded] / [totalBytes] clamped to 0..1; 0 when [totalBytes] is unknown; 1 when DONE. */
    val progress: Float
        get() = when {
            state == DownloadState.DONE -> 1f
            totalBytes <= 0 -> 0f
            else -> (bytesDownloaded.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
        }
}
