package com.podparar.core.data

import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DefaultDownloadRepository with a fake store and a fake AudioDownloader. */
class DefaultDownloadRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** A clock the test moves by hand. */
    private class MutableClock(var millis: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun millis(): Long = millis
    }

    private val start = 1_727_863_200_000L
    private val clock = MutableClock(start)
    private val store = FakePodcastStore()
    private val downloader = FakeAudioDownloader()
    private val repository = DefaultDownloadRepository(store, downloader, clock)

    private val url = "https://a.example.com/rss"
    private val ep = episode(url, "1")
    private val other = episode(url, "2")

    init {
        store.seed(podcast(url), listOf(ep, other))
    }

    private fun target(): File = File(tmp.root, "episodes/one.audio").absoluteFile.also { it.parentFile!!.mkdirs() }

    private fun row(id: String, state: DownloadState, path: String? = null, bytes: Long = 0, total: Long = 0, at: Long = start) =
        Download(id, state, path, bytes, total, at)

    private suspend fun expectError(expected: LoadError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected PodcastException($expected)")
        } catch (e: PodcastException) {
            assertEquals(expected, e.error)
        }
    }

    // --- markQueued ---

    @Test
    fun `markQueued writes a QUEUED row with no bytes`() = runTest {
        repository.markQueued(ep.id)

        val d = repository.get(ep.id)!!
        assertEquals(ep.id, d.episodeId)
        assertEquals(DownloadState.QUEUED, d.state)
        assertEquals(0L, d.bytesDownloaded)
        assertNull(d.filePath)
        assertEquals(start, d.updatedAt)
    }

    @Test
    fun `markQueued after a failure queues it again`() = runTest {
        store.seedDownload(row(ep.id, DownloadState.FAILED, bytes = 300, total = 1_000))
        clock.millis = start + 5_000

        repository.markQueued(ep.id)

        val d = repository.get(ep.id)!!
        assertEquals(DownloadState.QUEUED, d.state)
        assertEquals(0L, d.bytesDownloaded)
        assertEquals(start + 5_000, d.updatedAt)
    }

    // --- fetch ---

    @Test
    fun `fetch downloads the episode's audio into the target and ends DONE with its path`() = runTest {
        val target = target()
        clock.millis = start + 1_000

        repository.fetch(ep.id, target)

        assertEquals(listOf(ep.audioUrl to target), downloader.calls)
        assertArrayEquals(downloader.content, target.readBytes())
        val d = repository.get(ep.id)!!
        assertEquals(DownloadState.DONE, d.state)
        assertEquals(target.absolutePath, File(d.filePath!!).absolutePath)
        assertEquals(1_000L, d.bytesDownloaded)
        assertEquals(1_000L, d.totalBytes)
        assertEquals(1f, d.progress, 0.0001f)
        assertEquals(start + 1_000, d.updatedAt)
    }

    @Test
    fun `while the bytes arrive the row is RUNNING with progress`() = runTest {
        val seen = mutableListOf<Download?>()
        downloader.onStep = { _ ->
            seen += store.download(ep.id)
            clock.millis += 600
        }

        repository.fetch(ep.id, target())

        assertTrue("no row seen mid-download", seen.isNotEmpty())
        // By the last step the row has been written as RUNNING.
        assertEquals(DownloadState.RUNNING, seen.last()?.state)
        val running = store.upserts.filter { it.state == DownloadState.RUNNING }
        assertTrue("never written RUNNING", running.isNotEmpty())
        running.forEach { assertTrue(it.bytesDownloaded in 0..1_000L) }
        assertEquals(running.map { it.bytesDownloaded }.sorted(), running.map { it.bytesDownloaded })
        // RUNNING rows come before DONE, and DONE is the last write.
        assertEquals(DownloadState.DONE, store.upserts.last().state)
        val lastRunning = store.upserts.indexOfLast { it.state == DownloadState.RUNNING }
        val firstDone = store.upserts.indexOfFirst { it.state == DownloadState.DONE }
        assertTrue(lastRunning < firstDone)
    }

    @Test
    fun `progress is written at most every 500 ms`() = runTest {
        downloader.content = ByteArray(10_000) { 1 }
        downloader.chunks = 200 // all within the same instant

        repository.fetch(ep.id, target())

        val running = store.upserts.count { it.state == DownloadState.RUNNING }
        assertTrue("$running RUNNING writes for 200 progress calls in no time", running in 1..3)
        assertEquals(DownloadState.DONE, repository.get(ep.id)!!.state)
    }

    @Test
    fun `an unknown total still ends DONE`() = runTest {
        downloader.reportTotal = false

        repository.fetch(ep.id, target())

        val d = repository.get(ep.id)!!
        assertEquals(DownloadState.DONE, d.state)
        assertEquals(1f, d.progress, 0.0001f)
    }

    @Test
    fun `a failed download is marked FAILED and the error is rethrown`() = runTest {
        repository.markQueued(ep.id)
        downloader.failure = LoadError.OFFLINE

        expectError(LoadError.OFFLINE) { repository.fetch(ep.id, target()) }

        val d = repository.get(ep.id)!!
        assertEquals(DownloadState.FAILED, d.state)
        assertNull(repository.localPath(ep.id))
    }

    @Test
    fun `other download errors are rethrown as they are`() = runTest {
        for (error in listOf(LoadError.NOT_FOUND, LoadError.UNKNOWN)) {
            downloader.failure = error
            expectError(error) { repository.fetch(ep.id, target()) }
            assertEquals(DownloadState.FAILED, repository.get(ep.id)!!.state)
        }
    }

    @Test
    fun `an unknown episode is FAILED with NOT_FOUND and nothing is downloaded`() = runTest {
        val missing = "$url#missing"

        expectError(LoadError.NOT_FOUND) { repository.fetch(missing, target()) }

        assertTrue(downloader.calls.isEmpty())
        assertEquals(DownloadState.FAILED, repository.get(missing)!!.state)
    }

    @Test
    fun `a download can be retried after a failure`() = runTest {
        downloader.failure = LoadError.OFFLINE
        expectError(LoadError.OFFLINE) { repository.fetch(ep.id, target()) }

        downloader.failure = null
        repository.fetch(ep.id, target())

        assertEquals(DownloadState.DONE, repository.get(ep.id)!!.state)
        assertEquals(target().absolutePath, repository.localPath(ep.id)?.let { File(it).absolutePath })
    }

    // --- localPath ---

    @Test
    fun `localPath is the file only when DONE`() = runTest {
        assertNull(repository.localPath(ep.id))

        for (state in listOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.FAILED)) {
            store.seedDownload(row(ep.id, state, path = null, bytes = 10, total = 100))
            assertNull(state.name, repository.localPath(ep.id))
        }

        store.seedDownload(row(ep.id, DownloadState.DONE, path = "/data/episodes/x.audio", bytes = 100, total = 100))
        assertEquals("/data/episodes/x.audio", repository.localPath(ep.id))
    }

    // --- remove, get, all, downloads ---

    @Test
    fun `remove deletes the row but not the file`() = runTest {
        val target = target()
        repository.fetch(ep.id, target)

        repository.remove(ep.id)

        assertNull(repository.get(ep.id))
        assertNull(repository.localPath(ep.id))
        assertTrue("the file is the caller's to delete", target.exists())
    }

    @Test
    fun `removing a download that does not exist is fine`() = runTest {
        repository.remove("$url#nothing")
        assertEquals(emptyList<Download>(), repository.all())
    }

    @Test
    fun `get, all and downloads come from the store`() = runTest {
        assertNull(repository.get(ep.id))
        assertEquals(emptyList<Download>(), repository.all())
        assertEquals(emptyMap<String, Download>(), repository.downloads.first())

        val a = row(ep.id, DownloadState.DONE, "/x", 5, 5)
        val b = row(other.id, DownloadState.QUEUED)
        store.seedDownload(a)
        store.seedDownload(b)

        assertEquals(a, repository.get(ep.id))
        assertEquals(setOf(a, b), repository.all().toSet())
        assertEquals(mapOf(ep.id to a, other.id to b), repository.downloads.first())
    }

    @Test
    fun `downloads follows markQueued and remove`() = runTest {
        repository.markQueued(ep.id)
        assertEquals(setOf(ep.id), repository.downloads.first().keys)
        assertEquals(DownloadState.QUEUED, repository.downloads.first()[ep.id]?.state)

        repository.remove(ep.id)
        assertEquals(emptyMap<String, Download>(), repository.downloads.first())
    }
}
