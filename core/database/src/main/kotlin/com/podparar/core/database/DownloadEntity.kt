package com.podparar.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState

/** One episode's download; [state] is a DownloadState name. */
@Entity(tableName = "downloads")
internal data class DownloadEntity(
    @PrimaryKey val episodeId: String,
    val state: String,
    val filePath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val updatedAt: Long,
)

internal fun DownloadEntity.toModel(): Download = Download(
    episodeId = episodeId,
    // An unknown name (never written by this app) reads as a failed download rather than crashing.
    state = DownloadState.entries.firstOrNull { it.name == state } ?: DownloadState.FAILED,
    filePath = filePath,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    updatedAt = updatedAt,
)

internal fun Download.toEntity(): DownloadEntity = DownloadEntity(
    episodeId = episodeId,
    state = state.name,
    filePath = filePath,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    updatedAt = updatedAt,
)
