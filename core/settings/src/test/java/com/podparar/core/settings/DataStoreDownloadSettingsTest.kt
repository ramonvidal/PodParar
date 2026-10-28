package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** DataStoreDownloadSettings on a real preferences file in a temp folder. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreDownloadSettingsTest {
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
    fun `wifi only is on by default`() = runTest {
        assertTrue(newSettings().wifiOnly.first())
    }

    @Test
    fun `new episode alerts are on by default`() = runTest {
        assertTrue(newSettings().newEpisodeAlerts.first())
    }

    @Test
    fun `setWifiOnly stores the choice`() = runTest {
        val settings = newSettings()

        settings.setWifiOnly(false)
        assertFalse(settings.wifiOnly.first())

        settings.setWifiOnly(true)
        assertTrue(settings.wifiOnly.first())
    }

    @Test
    fun `setNewEpisodeAlerts stores the choice`() = runTest {
        val settings = newSettings()

        settings.setNewEpisodeAlerts(false)
        assertFalse(settings.newEpisodeAlerts.first())

        settings.setNewEpisodeAlerts(true)
        assertTrue(settings.newEpisodeAlerts.first())
    }

    @Test
    fun `the two choices are independent`() = runTest {
        val settings = newSettings()

        settings.setWifiOnly(false)
        assertTrue(settings.newEpisodeAlerts.first())

        settings.setNewEpisodeAlerts(false)
        assertFalse(settings.wifiOnly.first())

        settings.setWifiOnly(true)
        assertFalse(settings.newEpisodeAlerts.first())
    }

    @Test
    fun `the choices are read back by a new instance on the same file`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        val first = DataStoreDownloadSettings(firstStore)
        first.setWifiOnly(false)
        first.setNewEpisodeAlerts(false)
        firstJob.cancelAndJoin()

        val reopened = newSettings()
        assertFalse(reopened.wifiOnly.first())
        assertFalse(reopened.newEpisodeAlerts.first())
    }

    @Test
    fun `uses the keys in the contract`() = runTest {
        val (dataStore, _) = newDataStore()
        val settings = DataStoreDownloadSettings(dataStore)

        settings.setWifiOnly(false)
        settings.setNewEpisodeAlerts(false)

        val prefs = dataStore.data.first()
        assertEquals(false, prefs[booleanPreferencesKey("downloads_wifi_only")])
        assertEquals(false, prefs[booleanPreferencesKey("new_episode_alerts")])

        dataStore.edit { it[booleanPreferencesKey("downloads_wifi_only")] = true }
        assertTrue(settings.wifiOnly.first())
    }

    @Test
    fun `sharing the file with playback settings keeps both`() = runTest {
        val (dataStore, _) = newDataStore()
        val downloads = DataStoreDownloadSettings(dataStore)
        val playback = DataStorePlaybackSettings(dataStore)

        playback.setSpeed(1.5f)
        downloads.setWifiOnly(false)

        assertEquals(1.5f, playback.speed.first(), 0.0001f)
        assertFalse(downloads.wifiOnly.first())
        assertTrue(downloads.newEpisodeAlerts.first())
    }
}
