package com.podparar.core.downloads

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.podparar.core.data.PodcastRepository
import com.podparar.core.model.DownloadRules
import com.podparar.core.model.Episode
import com.podparar.core.settings.DownloadSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Background refresh: every feed, one "N new episodes" notification, auto-downloads, then storage clean-up. */
@HiltWorker
internal class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcasts: PodcastRepository,
    private val settings: DownloadSettings,
    private val downloads: WorkManagerEpisodeDownloads,
    private val cleanup: StorageCleanup,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val result = podcasts.refreshAll()
            val shows = podcasts.podcasts.first()
            if (result.newEpisodes.isNotEmpty() && settings.newEpisodeAlerts.first() &&
                Notifications.canNotify(applicationContext)
            ) {
                showNewEpisodes(result.newEpisodes, shows.associate { it.feedUrl to it.title })
            }
            val autoShows = shows.filter { it.autoDownload }.mapTo(HashSet()) { it.feedUrl }
            DownloadRules.autoDownloadPicks(result.newEpisodes, autoShows).forEach { downloads.enqueue(it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanUp()
            return Result.retry()
        }
        cleanUp()
        return Result.success()
    }

    private suspend fun cleanUp() {
        try {
            cleanup.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Next refresh tries again.
        }
    }

    @Suppress("MissingPermission") // Checked by Notifications.canNotify before calling.
    private fun showNewEpisodes(newEpisodes: List<Episode>, showTitles: Map<String, String>) {
        val context = applicationContext
        val title = DownloadRules.newEpisodesTitle(newEpisodes.size) ?: return
        Notifications.createNewEpisodesChannel(context)
        val lines = newEpisodes.sortedByDescending { it.pubDate }.take(MAX_LINES).map { episode ->
            context.getString(R.string.downloads_episode_line, showTitles[episode.podcastUrl].orEmpty(), episode.title)
        }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { style.addLine(it) }
        val notification = NotificationCompat.Builder(context, Notifications.NEW_EPISODES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_podparar)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(style)
            .setNumber(newEpisodes.size)
            .setContentIntent(Notifications.openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.NEW_EPISODES_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val MAX_LINES = 3
    }
}
