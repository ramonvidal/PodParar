package com.podparar.core.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.podparar.core.data.DownloadRepository
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadLimit
import com.podparar.core.model.DownloadState
import com.podparar.core.settings.DownloadSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Downloads run as unique WorkManager work ("download-<id>"); the rows in the database say how each one is going.
 * Requests and removals run one at a time, in the order they were made.
 */
@Singleton
internal class WorkManagerEpisodeDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val settings: DownloadSettings,
) : EpisodeDownloads {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // Lazy: the app's Configuration.Provider must be ready (its worker factory injected) before WorkManager starts.
    private val workManager by lazy { WorkManager.getInstance(context) }

    override val downloads: Flow<Map<String, Download>> = repository.downloads

    private val limitReachedEvents = MutableSharedFlow<Int>(extraBuffer = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val limitReached: Flow<Int> = limitReachedEvents.asSharedFlow()

    override fun download(episodeId: String) {
        // UNDISPATCHED + a fair mutex keep calls in the order they were made (a quick download-then-remove stays removed).
        scope.launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { enqueueLocked(episodeId) } }
    }

    override fun remove(episodeId: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { removeLocked(episodeId) } }
    }

    override suspend fun removeAllFor(feedUrl: String, episodeIds: List<String>) {
        mutex.withLock {
            val prefix = "$feedUrl#"
            val ids = LinkedHashSet(episodeIds)
            repository.all().mapNotNullTo(ids) { download -> download.episodeId.takeIf { it.startsWith(prefix) } }
            ids.forEach { removeLocked(it) }
        }
    }

    /** For the refresh worker's auto-downloads: same as [download], but waits until the work is enqueued. */
    suspend fun enqueue(episodeId: String) {
        mutex.withLock { enqueueLocked(episodeId, userRequest = false) }
    }

    private suspend fun enqueueLocked(episodeId: String, userRequest: Boolean) {
        val existing = repository.get(episodeId)
        if (existing?.state == DownloadState.DONE && existing.filePath?.let { File(it).exists() } == true) return
        // Queued, running and done rows already hold a slot; a new or failed one needs a free slot.
        val holdsSlot = existing != null && existing.state != DownloadState.FAILED
        if (!holdsSlot) {
            val limit = settings.maxDownloads.first()
            val used = repository.all().count { it.state != DownloadState.FAILED }
            if (DownloadLimit.isReached(used, limit)) {
                // Block and warn: nothing is enqueued, nothing already downloaded is touched. Auto-downloads skip silently.
                if (userRequest) limitReachedEvents.tryEmit(limit)
                return
            }
        }
        // A download under way keeps its progress; KEEP below leaves its work alone.
        if (existing?.state != DownloadState.RUNNING) repository.markQueued(episodeId)
        val network = if (settings.wifiOnly.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    private suspend fun removeLocked(episodeId: String) {
        workManager.cancelUniqueWork(workName(episodeId))
        deleteLocked(episodeId)
        // Cancelling only asks the worker to stop: a progress write already on its way can bring the row back.
        scope.launch {
            delay(SETTLE_MS)
            mutex.withLock {
                val row = repository.get(episodeId)
                if (row != null && row.state != DownloadState.DONE && !hasPendingWork(episodeId)) deleteLocked(episodeId)
            }
        }
    }

    private suspend fun deleteLocked(episodeId: String) {
        EpisodeFiles.delete(context, episodeId, repository.get(episodeId)?.filePath)
        repository.remove(episodeId)
    }

    /** Whether a download of the episode was asked for again (work not finished). Runs on Dispatchers.IO. */
    private fun hasPendingWork(episodeId: String): Boolean =
        workManager.getWorkInfosForUniqueWork(workName(episodeId)).get().any { !it.state.isFinished }

    companion object {
        const val TAG = "episode-download"
        private const val RETRY_BACKOFF_SECONDS = 30L
        private const val SETTLE_MS = 3_000L

        fun workName(episodeId: String): String = "download-$episodeId"
    }
}
