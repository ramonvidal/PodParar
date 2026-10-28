package com.podparar

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.podparar.core.downloads.BackgroundWork
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PodPararApp : Application(), Configuration.Provider {
    // Lets background workers (downloads, feed refresh) get their dependencies from Hilt.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        BackgroundWork.schedule(this)
    }
}
