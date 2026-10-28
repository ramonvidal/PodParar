package com.podparar.core.downloads

import android.content.Context
import com.podparar.core.data.DownloadRepository
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.model.DownloadRules
import com.podparar.core.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject

/**
 * Frees storage: played downloads once DownloadRules.shouldDelete says so, rows whose episode is gone, and files in
 * filesDir/episodes that no row accounts for.
 */
internal class StorageCleanup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloads: DownloadRepository,
    private val episodes: EpisodeRepository,
    private val clock: Clock,
) {
    suspend fun run() {
        val now = clock.millis()
        val keep = HashSet<String>()
        for (download in downloads.all()) {
            val episode = episodes.getEpisode(download.episodeId)
            if (episode == null || DownloadRules.shouldDelete(episode, download, now)) {
                EpisodeFiles.delete(context, download.episodeId, download.filePath)
                downloads.remove(download.episodeId)
                continue
            }
            when (download.state) {
                DownloadState.DONE -> download.filePath?.let { keep += File(it).absolutePath }
                // Being written right now (or about to be): not an orphan.
                DownloadState.QUEUED, DownloadState.RUNNING ->
                    keep += EpisodeFiles.fileFor(context, download.episodeId).absolutePath
                DownloadState.FAILED -> Unit
            }
        }
        EpisodeFiles.dir(context).listFiles()?.forEach { file ->
            // A download that started after the rows were read may already be writing its file: leave fresh files alone.
            val fresh = now - file.lastModified() < FRESH_FILE_MS
            if (file.isFile && file.absolutePath !in keep && !fresh) file.delete()
        }
    }

    private companion object {
        const val FRESH_FILE_MS = 60 * 60 * 1000L
    }
}
