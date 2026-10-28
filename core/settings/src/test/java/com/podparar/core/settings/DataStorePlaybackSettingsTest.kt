package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.PlaybackSpeeds
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

/** DataStorePlaybackSettings on a real preferences file in a temp folder. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStorePlaybackSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val delta = 0.0001f
    private val jobs = mutableListOf<Job>()

    /** A DataStore on the test's file, in its own scope so it can be closed before opening the file again. */
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
    fun `speed is the default until set`() = runTest {
        val settings = newSettings()
        assertEquals(PlaybackSpeeds.DEFAULT, settings.speed.first(), delta)
        assertEquals(1.0f, settings.speed.first(), delta)
    }

    @Test
    fun `setSpeed stores the speed`() = runTest {
        val settings = newSettings()

        settings.setSpeed(1.5f)
        assertEquals(1.5f, settings.speed.first(), delta)

        settings.setSpeed(2.5f)
        assertEquals(2.5f, settings.speed.first(), delta)

        settings.setSpeed(1.0f)
        assertEquals(1.0f, settings.speed.first(), delta)
    }

    @Test
    fun `setSpeed keeps an in-range value that is not an option`() = runTest {
        val settings = newSettings()
        settings.setSpeed(1.3f)
        assertEquals(1.3f, settings.speed.first(), delta)
    }

    @Test
    fun `setSpeed clamps too fast to 3`() = runTest {
        val settings = newSettings()
        settings.setSpeed(5f)
        assertEquals(3.0f, settings.speed.first(), delta)
    }

    @Test
    fun `setSpeed clamps too slow to 0_8`() = runTest {
        val settings = newSettings()
        settings.setSpeed(0.25f)
        assertEquals(0.8f, settings.speed.first(), delta)
    }

    @Test
    fun `setSpeed with NaN stores the default`() = runTest {
        val settings = newSettings()
        settings.setSpeed(2.0f)
        settings.setSpeed(Float.NaN)
        assertEquals(PlaybackSpeeds.DEFAULT, settings.speed.first(), delta)
    }

    @Test
    fun `the stored speed is read back by a new instance on the same file`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).setSpeed(1.8f)
        // Close the first DataStore: only one may be active per file.
        firstJob.cancelAndJoin()

        val reopened = newSettings()
        assertEquals(1.8f, reopened.speed.first(), delta)
    }

    @Test
    fun `a clamped value is what is persisted`() = runTest {
        val (firstStore, firstJob) = newDataStore()
        DataStorePlaybackSettings(firstStore).setSpeed(9f)
        firstJob.cancelAndJoin()

        assertEquals(3.0f, newSettings().speed.first(), delta)
    }
}
