package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.DownloadLimit
import com.podparar.core.model.HomeLimit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** DataStoreDownloadSettings.maxDownloads on a real preferences file in a temp folder. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreMaxDownloadsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()

    private fun newDataStore(): Pair<DataStore<Preferences>, Job> {
        val job = SupervisorJob()
        jobs += job
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        return dataStore to job
    }

    private fun newSettings(): DownloadSettings = DataStoreDownloadSettings(newDataStore().first)

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `defaults to the download limit default`() = runTest {
        val settings = newSettings()
        assertEquals(DownloadLimit.DEFAULT, settings.maxDownloads.first())
        assertEquals(20, settings.maxDownloads.first())
    }

    @Test
    fun `setMaxDownloads stores the value`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(500)
        settings.setMaxDownloads(50)
        assertEquals(50, settings.maxDownloads.first())
        settings.setMaxDownloads(5)
        assertEquals(5, settings.maxDownloads.first())
    }

    @Test
    fun `values below the minimum are clamped up`() = runTest {
        val settings = newSettings()
        settings.setMaxDownloads(0)
        assertEquals(DownloadLimit.MIN, settings.maxDownloads.first())
        assertEquals(1, settings.maxDownloads.first())
        settings.setMaxDownloads(-7)
        assertEquals(1, settings.maxDownloads.first())
    }

    @Test
    fun `values above the maximum are clamped below the default home limit`() = runTest {
        val settings = newSettings()
        settings.setMaxDownloads(1000)
        // Home defaults to 50, so downloads stay under it: 49.
        assertEquals(HomeLimit.maxDownloadsFor(HomeLimit.DEFAULT), settings.maxDownloads.first())
        assertEquals(49, settings.maxDownloads.first())
    }

    @Test
    fun `values above the maximum are clamped to the maximum when home is large enough`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(HomeLimit.MAX)
        settings.setMaxDownloads(1000)
        assertEquals(DownloadLimit.MAX, settings.maxDownloads.first())
        assertEquals(200, settings.maxDownloads.first())
    }

    @Test
    fun `the limit is read back by a new instance on the same file`() = runTest {
        val (store, job) = newDataStore()
        DataStoreDownloadSettings(store).setMaxDownloads(30)
        job.cancelAndJoin()

        assertEquals(30, newSettings().maxDownloads.first())
    }

    @Test
    fun `the limit does not disturb the other choices`() = runTest {
        val settings = newSettings()
        settings.setMaxDownloads(10)
        settings.setWifiOnly(false)
        assertEquals(10, settings.maxDownloads.first())
        assertEquals(false, settings.wifiOnly.first())
        assertEquals(true, settings.newEpisodeAlerts.first())
    }
}
