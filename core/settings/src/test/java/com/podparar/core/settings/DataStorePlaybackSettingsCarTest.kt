package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Car phase: "next/previous skip" (car, Bluetooth and headphone buttons) on a real preferences file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStorePlaybackSettingsCarTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()

    private fun newDataStore(): Pair<DataStore<Preferences>, Job> {
        val job = SupervisorJob()
        jobs += job
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(tmp.root, "t.preferences_pb") },
        )
        return dataStore to job
    }

    private fun newSettings(): PlaybackSettings = DataStorePlaybackSettings(newDataStore().first)

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `nextPreviousSkip is on by default`() = runTest {
        assertTrue(newSettings().nextPreviousSkip.first())
    }

    @Test
    fun `setNextPreviousSkip turns it off and on`() = runTest {
        val settings = newSettings()
        settings.setNextPreviousSkip(false)
        assertFalse(settings.nextPreviousSkip.first())
        settings.setNextPreviousSkip(true)
        assertTrue(settings.nextPreviousSkip.first())
    }

    @Test
    fun `nextPreviousSkip is read back by a new instance on the same file`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).setNextPreviousSkip(false)
        firstJob.cancelAndJoin()

        assertFalse(newSettings().nextPreviousSkip.first())
    }

    @Test
    fun `nextPreviousSkip uses the agreed key`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).setNextPreviousSkip(false)
        firstJob.cancelAndJoin()

        val (raw, _) = newDataStore()
        assertEquals(false, raw.data.first()[booleanPreferencesKey("next_previous_skip")])
    }

    @Test
    fun `a value stored under the key is read`() = runTest {
        val (raw, rawJob) = newDataStore()
        raw.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[booleanPreferencesKey("next_previous_skip")] = false
            }
        }
        rawJob.cancelAndJoin()

        assertFalse(newSettings().nextPreviousSkip.first())
    }

    @Test
    fun `reading the default writes nothing`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        assertTrue(DataStorePlaybackSettings(firstStore).nextPreviousSkip.first())
        firstJob.cancelAndJoin()

        val (raw, _) = newDataStore()
        assertNull(raw.data.first()[booleanPreferencesKey("next_previous_skip")])
    }

    @Test
    fun `nextPreviousSkip does not change the other playback settings`() = runTest {
        val settings = newSettings()
        val skipSilence = settings.skipSilence.first()
        val shake = settings.shakeToExtend.first()
        val back = settings.skipBackMs.first()
        val forward = settings.skipForwardMs.first()
        val speed = settings.speed.first()

        settings.setNextPreviousSkip(false)

        assertEquals(skipSilence, settings.skipSilence.first())
        assertEquals(shake, settings.shakeToExtend.first())
        assertEquals(back, settings.skipBackMs.first())
        assertEquals(forward, settings.skipForwardMs.first())
        assertEquals(speed, settings.speed.first(), 0.0001f)
    }
}
