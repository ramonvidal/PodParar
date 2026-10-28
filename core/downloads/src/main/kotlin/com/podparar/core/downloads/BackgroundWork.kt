package com.podparar.core.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** The background feed refresh (which also posts new-episode alerts, auto-downloads and cleans up storage). */
object BackgroundWork {
    private const val REFRESH_WORK_NAME = "feed-refresh"
    private const val REFRESH_HOURS = 4L

    /** Enqueues (KEEP) the periodic refresh: every 4 h, network connected, battery not low. Called by :app at startup. */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(REFRESH_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(REFRESH_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
