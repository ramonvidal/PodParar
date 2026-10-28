package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.PlaybackSpeeds
import com.podparar.core.model.SkipLengths
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

/** Phase 5 playback settings (skip silence, skip lengths, shake to extend) on a real preferences file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStorePlaybackSettingsComfortTest {
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

    // defaults

    @Test
    fun `the new settings have their defaults until set`() = runTest {
        val settings = newSettings()
        assertFalse(settings.skipSilence.first())
        assertEquals(SkipLengths.DEFAULT_BACK_MS, settings.skipBackMs.first())
        assertEquals(10_000L, settings.skipBackMs.first())
        assertEquals(SkipLengths.DEFAULT_FORWARD_MS, settings.skipForwardMs.first())
        assertEquals(30_000L, settings.skipForwardMs.first())
        assertTrue(settings.shakeToExtend.first())
    }

    // setters

    @Test
    fun `setSkipSilence turns it on and off`() = runTest {
        val settings = newSettings()
        settings.setSkipSilence(true)
        assertTrue(settings.skipSilence.first())
        settings.setSkipSilence(false)
        assertFalse(settings.skipSilence.first())
    }

    @Test
    fun `setShakeToExtend turns it off and on`() = runTest {
        val settings = newSettings()
        settings.setShakeToExtend(false)
        assertFalse(settings.shakeToExtend.first())
        settings.setShakeToExtend(true)
        assertTrue(settings.shakeToExtend.first())
    }

    @Test
    fun `setSkipBackMs stores each option`() = runTest {
        val settings = newSettings()
        for (ms in SkipLengths.backOptionsMs) {
            settings.setSkipBackMs(ms)
            assertEquals(ms, settings.skipBackMs.first())
        }
    }

    @Test
    fun `setSkipForwardMs stores each option`() = runTest {
        val settings = newSettings()
        for (ms in SkipLengths.forwardOptionsMs) {
            settings.setSkipForwardMs(ms)
            assertEquals(ms, settings.skipForwardMs.first())
        }
    }

    @Test
    fun `setSkipBackMs stores the nearest option`() = runTest {
        val settings = newSettings()
        settings.setSkipBackMs(12_000L)
        assertEquals(10_000L, settings.skipBackMs.first())
        settings.setSkipBackMs(26_000L)
        assertEquals(30_000L, settings.skipBackMs.first())
        settings.setSkipBackMs(7_500L)
        assertEquals(5_000L, settings.skipBackMs.first())
        settings.setSkipBackMs(0L)
        assertEquals(5_000L, settings.skipBackMs.first())
        settings.setSkipBackMs(600_000L)
        assertEquals(30_000L, settings.skipBackMs.first())
    }

    @Test
    fun `setSkipForwardMs stores the nearest option`() = runTest {
        val settings = newSettings()
        settings.setSkipForwardMs(4_000L)
        assertEquals(5_000L, settings.skipForwardMs.first())
        settings.setSkipForwardMs(20_000L)
        assertEquals(10_000L, settings.skipForwardMs.first())
        settings.setSkipForwardMs(-1L)
        assertEquals(5_000L, settings.skipForwardMs.first())
        settings.setSkipForwardMs(90_000L)
        assertEquals(30_000L, settings.skipForwardMs.first())
    }

    @Test
    fun `skip back and forward are stored separately`() = runTest {
        val settings = newSettings()
        settings.setSkipBackMs(30_000L)
        settings.setSkipForwardMs(5_000L)
        assertEquals(30_000L, settings.skipBackMs.first())
        assertEquals(5_000L, settings.skipForwardMs.first())
    }

    @Test
    fun `the new settings do not change the speed`() = runTest {
        val settings = newSettings()
        settings.setSpeed(1.5f)
        settings.setSkipSilence(true)
        settings.setSkipBackMs(5_000L)
        settings.setSkipForwardMs(10_000L)
        settings.setShakeToExtend(false)
        assertEquals(1.5f, settings.speed.first(), 0.0001f)

        val fresh = newSettingsOnOtherFile()
        assertEquals(PlaybackSpeeds.DEFAULT, fresh.speed.first(), 0.0001f)
    }

    private fun newSettingsOnOtherFile(): PlaybackSettings {
        val job = SupervisorJob()
        jobs += job
        return DataStorePlaybackSettings(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = { File(tmp.root, "other.preferences_pb") },
            ),
        )
    }

    // persistence

    @Test
    fun `the new settings are read back by a new instance on the same file`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).apply {
            setSkipSilence(true)
            setSkipBackMs(5_000L)
            setSkipForwardMs(10_000L)
            setShakeToExtend(false)
            setSpeed(2.0f)
        }
        firstJob.cancelAndJoin()

        val reopened = newSettings()
        assertTrue(reopened.skipSilence.first())
        assertEquals(5_000L, reopened.skipBackMs.first())
        assertEquals(10_000L, reopened.skipForwardMs.first())
        assertFalse(reopened.shakeToExtend.first())
        assertEquals(2.0f, reopened.speed.first(), 0.0001f)
    }

    @Test
    fun `the nearest option is what is persisted`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).apply {
            setSkipBackMs(14_000L)
            setSkipForwardMs(100_000L)
        }
        firstJob.cancelAndJoin()

        val (raw, _) = newDataStore()
        val prefs = raw.data.first()
        assertEquals(10_000L, prefs[longPreferencesKey("skip_back_ms")])
        assertEquals(30_000L, prefs[longPreferencesKey("skip_forward_ms")])
    }

    @Test
    fun `the settings use the agreed keys`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).apply {
            setSkipSilence(true)
            setShakeToExtend(false)
            setSkipBackMs(30_000L)
            setSkipForwardMs(5_000L)
        }
        firstJob.cancelAndJoin()

        val (raw, _) = newDataStore()
        val prefs = raw.data.first()
        assertEquals(true, prefs[booleanPreferencesKey("skip_silence")])
        assertEquals(false, prefs[booleanPreferencesKey("shake_to_extend")])
        assertEquals(30_000L, prefs[longPreferencesKey("skip_back_ms")])
        assertEquals(5_000L, prefs[longPreferencesKey("skip_forward_ms")])
    }

    @Test
    fun `stored values written under the keys are read`() = runTest {
        val (raw, rawJob) = newDataStore()
        raw.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[booleanPreferencesKey("skip_silence")] = true
                this[booleanPreferencesKey("shake_to_extend")] = false
                this[longPreferencesKey("skip_back_ms")] = 30_000L
                this[longPreferencesKey("skip_forward_ms")] = 10_000L
            }
        }
        rawJob.cancelAndJoin()

        val settings = newSettings()
        assertTrue(settings.skipSilence.first())
        assertFalse(settings.shakeToExtend.first())
        assertEquals(30_000L, settings.skipBackMs.first())
        assertEquals(10_000L, settings.skipForwardMs.first())
    }
}
