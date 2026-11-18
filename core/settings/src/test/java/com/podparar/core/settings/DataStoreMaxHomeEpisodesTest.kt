package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** DataStoreDownloadSettings.maxHomeEpisodes and its link to maxDownloads, on a real preferences file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreMaxHomeEpisodesTest {
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
    fun `defaults to 50`() = runTest {
        assertEquals(HomeLimit.DEFAULT, newSettings().maxHomeEpisodes.first())
        assertEquals(50, newSettings().maxHomeEpisodes.first())
    }

    @Test
    fun `setMaxHomeEpisodes stores the value`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(200)
        assertEquals(200, settings.maxHomeEpisodes.first())
    }

    @Test
    fun `values are clamped to the home limit range`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(1)
        assertEquals(HomeLimit.MIN, settings.maxHomeEpisodes.first())
        settings.setMaxHomeEpisodes(10_000)
        assertEquals(HomeLimit.MAX, settings.maxHomeEpisodes.first())
    }

    @Test
    fun `the home limit is read back by a new instance on the same file`() = runTest {
        val (store, job) = newDataStore()
        DataStoreDownloadSettings(store).setMaxHomeEpisodes(100)
        job.cancelAndJoin()

        assertEquals(100, newSettings().maxHomeEpisodes.first())
    }

    @Test
    fun `max downloads is always strictly lower than the home limit`() = runTest {
        val settings = newSettings()
        // Default: downloads 20, home 50.
        assertEquals(20, settings.maxDownloads.first())
        settings.setMaxDownloads(100)
        assertEquals(49, settings.maxDownloads.first())
        settings.setMaxDownloads(30)
        assertEquals(30, settings.maxDownloads.first())
        settings.setMaxDownloads(50)
        assertEquals(49, settings.maxDownloads.first())
    }

    @Test
    fun `a larger home limit lets more downloads through`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(500)
        settings.setMaxDownloads(100)
        assertEquals(100, settings.maxDownloads.first())
    }

    @Test
    fun `lowering home lowers the stored max downloads in the same edit`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(500)
        settings.setMaxDownloads(100)
        settings.setMaxHomeEpisodes(30)
        assertEquals(30, settings.maxHomeEpisodes.first())
        assertEquals(29, settings.maxDownloads.first())
    }

    @Test
    fun `raising home again does not restore the old max downloads`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(500)
        settings.setMaxDownloads(100)
        settings.setMaxHomeEpisodes(30)
        settings.setMaxHomeEpisodes(500)
        assertEquals(29, settings.maxDownloads.first())
    }

    @Test
    fun `lowering home keeps a max downloads that still fits`() = runTest {
        val settings = newSettings()
        settings.setMaxHomeEpisodes(500)
        settings.setMaxDownloads(10)
        settings.setMaxHomeEpisodes(30)
        assertEquals(10, settings.maxDownloads.first())
    }

    @Test
    fun `lowered max downloads is persisted for a new instance`() = runTest {
        val (store, job) = newDataStore()
        val settings = DataStoreDownloadSettings(store)
        settings.setMaxHomeEpisodes(500)
        settings.setMaxDownloads(100)
        settings.setMaxHomeEpisodes(30)
        job.cancelAndJoin()

        val reopened = newSettings()
        assertEquals(30, reopened.maxHomeEpisodes.first())
        assertEquals(29, reopened.maxDownloads.first())
    }

    @Test
    fun `the home limit does not disturb the other choices`() = runTest {
        val settings = newSettings()
        settings.setWifiOnly(false)
        settings.setNewEpisodeAlerts(false)
        settings.setMaxHomeEpisodes(100)
        assertEquals(100, settings.maxHomeEpisodes.first())
        assertEquals(false, settings.wifiOnly.first())
        assertEquals(false, settings.newEpisodeAlerts.first())

        settings.setWifiOnly(true)
        assertEquals(100, settings.maxHomeEpisodes.first())
        assertEquals(false, settings.newEpisodeAlerts.first())
    }
}
