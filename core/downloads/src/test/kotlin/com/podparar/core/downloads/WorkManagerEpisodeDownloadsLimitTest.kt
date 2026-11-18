package com.podparar.core.downloads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.podparar.core.data.DownloadRepository
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.settings.DownloadSettings
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The download limit in WorkManagerEpisodeDownloads, with fake rows and settings and a test WorkManager. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WorkManagerEpisodeDownloadsLimitTest {
    private lateinit var context: Context
    private val repository = FakeDownloadRepository()
    private val settings = FakeDownloadSettings()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun newDownloads() = WorkManagerEpisodeDownloads(context, repository, settings)

    private fun row(id: String, state: DownloadState) =
        Download(id, state, filePath = null, bytesDownloaded = 0, totalBytes = 0, updatedAt = 0)

    private fun seed(vararg rows: Pair<String, DownloadState>) =
        rows.forEach { (id, state) -> repository.rows[id] = row(id, state) }

    @Test
    fun `a new episode under the limit is queued`() = runTest {
        settings.limit = 3
        seed("a" to DownloadState.QUEUED, "b" to DownloadState.DONE)
        val downloads = newDownloads()

        downloads.download("c")

        assertEquals(DownloadState.QUEUED, repository.rows["c"]?.state)
    }

    @Test
    fun `a user download at the limit is refused and reports the limit`() = runTest(UnconfinedTestDispatcher()) {
        settings.limit = 3
        seed("a" to DownloadState.QUEUED, "b" to DownloadState.RUNNING, "c" to DownloadState.DONE)
        val downloads = newDownloads()
        val emitted = mutableListOf<Int>()
        val job = launch { downloads.limitReached.toList(emitted) }

        downloads.download("d")

        assertNull(repository.rows["d"])
        assertEquals(listOf(3), emitted)
        job.cancel()
    }

    @Test
    fun `failed rows do not count towards the limit`() = runTest {
        settings.limit = 2
        seed("a" to DownloadState.FAILED, "b" to DownloadState.FAILED, "c" to DownloadState.QUEUED)
        val downloads = newDownloads()

        downloads.download("d")

        assertEquals(DownloadState.QUEUED, repository.rows["d"]?.state)
    }

    @Test
    fun `asking again for a queued or running episode is not refused at the limit`() = runTest(UnconfinedTestDispatcher()) {
        settings.limit = 2
        seed("a" to DownloadState.QUEUED, "b" to DownloadState.RUNNING)
        val downloads = newDownloads()
        val emitted = mutableListOf<Int>()
        val job = launch { downloads.limitReached.toList(emitted) }

        downloads.download("a")
        downloads.download("b")

        assertTrue(emitted.isEmpty())
        assertEquals(DownloadState.QUEUED, repository.rows["a"]?.state)
        assertEquals(DownloadState.RUNNING, repository.rows["b"]?.state)
        assertEquals(2, repository.rows.size)
        job.cancel()
    }

    @Test
    fun `a failed episode is checked against the limit like a new one`() = runTest(UnconfinedTestDispatcher()) {
        settings.limit = 2
        seed("a" to DownloadState.QUEUED, "b" to DownloadState.DONE, "x" to DownloadState.FAILED)
        val downloads = newDownloads()
        val emitted = mutableListOf<Int>()
        val job = launch { downloads.limitReached.toList(emitted) }

        downloads.download("x")

        assertEquals(DownloadState.FAILED, repository.rows["x"]?.state)
        assertEquals(listOf(2), emitted)

        settings.limit = 3
        downloads.download("x")

        assertEquals(DownloadState.QUEUED, repository.rows["x"]?.state)
        assertEquals(listOf(2), emitted)
        job.cancel()
    }

    @Test
    fun `auto-download is refused silently at the limit`() = runTest(UnconfinedTestDispatcher()) {
        settings.limit = 1
        seed("a" to DownloadState.DONE)
        val downloads = newDownloads()
        val emitted = mutableListOf<Int>()
        val job = launch { downloads.limitReached.toList(emitted) }

        downloads.enqueue("b")

        assertNull(repository.rows["b"])
        assertTrue(emitted.isEmpty())
        job.cancel()
    }

    @Test
    fun `auto-download under the limit is queued`() = runTest {
        settings.limit = 2
        seed("a" to DownloadState.DONE)
        val downloads = newDownloads()

        downloads.enqueue("b")

        assertEquals(DownloadState.QUEUED, repository.rows["b"]?.state)
    }

    @Test
    fun `lowering the limit below the count deletes nothing`() = runTest {
        settings.limit = 5
        seed("a" to DownloadState.QUEUED, "b" to DownloadState.RUNNING, "c" to DownloadState.DONE)
        val downloads = newDownloads()

        settings.limit = 1
        downloads.download("d")
        downloads.enqueue("e")

        assertEquals(setOf("a", "b", "c"), repository.rows.keys)
        assertEquals(DownloadState.DONE, repository.rows["c"]?.state)
        assertTrue(repository.removed.isEmpty())
    }

    private class FakeDownloadSettings : DownloadSettings {
        var limit: Int = 20
        override val wifiOnly: Flow<Boolean> get() = MutableStateFlow(false)
        override val newEpisodeAlerts: Flow<Boolean> get() = MutableStateFlow(true)
        override val maxDownloads: Flow<Int> get() = MutableStateFlow(limit)
        override suspend fun setWifiOnly(enabled: Boolean) {}
        override suspend fun setNewEpisodeAlerts(enabled: Boolean) {}
        override suspend fun setMaxDownloads(value: Int) { limit = value }
    }

    private class FakeDownloadRepository : DownloadRepository {
        val rows = linkedMapOf<String, Download>()
        val removed = mutableListOf<String>()
        private val flow = MutableStateFlow<Map<String, Download>>(emptyMap())
        override val downloads: Flow<Map<String, Download>> get() = flow

        override suspend fun get(episodeId: String): Download? = rows[episodeId]
        override suspend fun all(): List<Download> = rows.values.toList()
        override suspend fun markQueued(episodeId: String) {
            rows[episodeId] = Download(episodeId, DownloadState.QUEUED, null, 0, 0, 0)
            flow.update { rows.toMap() }
        }
        override suspend fun fetch(episodeId: String, target: File) {}
        override suspend fun remove(episodeId: String) {
            rows.remove(episodeId)
            removed += episodeId
        }
        override suspend fun localPath(episodeId: String): String? = null
    }
}
