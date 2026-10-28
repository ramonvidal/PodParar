package com.podparar.core.downloads

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.podparar.core.data.DownloadRepository
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.model.DownloadState
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** Downloads one episode's audio, in the foreground with a progress notification. Input: [KEY_EPISODE_ID]. */
@HiltWorker
internal class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloads: DownloadRepository,
    private val episodes: EpisodeRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        val target = EpisodeFiles.fileFor(applicationContext, episodeId)
        target.parentFile?.mkdirs()
        val title = episodes.getEpisode(episodeId)?.title.orEmpty()
        val notificationId = NOTIFICATION_ID_BASE + (episodeId.hashCode() and 0xFFFF)
        Notifications.createDownloadsChannel(applicationContext)
        showProgress(notificationId, title, percent = 0, indeterminate = true)

        return try {
            coroutineScope {
                // The repository writes progress to the row; the notification follows it, once per percent.
                val progress = launch {
                    downloads.downloads
                        .mapNotNull { it[episodeId] }
                        .filter { it.state == DownloadState.RUNNING }
                        .map { (it.progress * 100).toInt() to (it.totalBytes <= 0) }
                        .distinctUntilChanged()
                        .collect { (percent, unknown) -> showProgress(notificationId, title, percent, unknown) }
                }
                try {
                    downloads.fetch(episodeId, target)
                } finally {
                    progress.cancel()
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PodcastException) {
            target.delete()
            if (e.error == LoadError.OFFLINE && runAttemptCount < MAX_ATTEMPTS - 1) {
                // Waiting to try again, not failed yet.
                downloads.markQueued(episodeId)
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            target.delete()
            Result.failure()
        }
    }

    private suspend fun showProgress(notificationId: Int, title: String, percent: Int, indeterminate: Boolean) {
        val context = applicationContext
        val downloading = context.getString(R.string.downloads_downloading)
        val notification = NotificationCompat.Builder(context, Notifications.DOWNLOADS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_podparar)
            .setContentTitle(title.ifEmpty { downloading })
            .setContentText(downloading)
            .setProgress(PROGRESS_MAX, percent.coerceIn(0, PROGRESS_MAX), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(Notifications.openAppIntent(context))
            .build()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses a foreground service started from the background
            // (ForegroundServiceStartNotAllowedException); the download carries on as ordinary work.
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episodeId"
        private const val MAX_ATTEMPTS = 3
        private const val PROGRESS_MAX = 100
        private const val NOTIFICATION_ID_BASE = 10_000
    }
}
