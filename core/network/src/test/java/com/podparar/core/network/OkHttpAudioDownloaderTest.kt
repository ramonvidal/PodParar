package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** OkHttpAudioDownloader against a local MockWebServer: bytes on disk, progress, HTTP outcomes, no partial files. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OkHttpAudioDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val downloader: AudioDownloader = OkHttpAudioDownloader(client)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (e: Exception) {
            // Already shut down by the test.
        }
    }

    private fun audio(size: Int): ByteArray = ByteArray(size) { (it * 31 + 7).toByte() }

    private fun target(): File = File(tmp.root, "episodes/abc.audio").also { it.parentFile!!.mkdirs() }

    private suspend fun downloadError(url: String, target: File): LoadError = try {
        downloader.download(url, target) { _, _ -> }
        fail("expected PodcastException")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    @Test
    fun `writes the bytes to the target`() = runTest {
        val bytes = audio(256 * 1024)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(bytes)))
        val target = target()

        downloader.download(server.url("/ep.mp3").toString(), target) { _, _ -> }

        assertArrayEquals(bytes, target.readBytes())
        assertEquals("/ep.mp3", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun `overwrites a file already at the target`() = runTest {
        val target = target()
        target.writeBytes(audio(500_000))
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))

        downloader.download(server.url("/ep.mp3").toString(), target) { _, _ -> }

        assertArrayEquals(bytes, target.readBytes())
    }

    @Test
    fun `progress grows and ends at the total`() = runTest {
        val bytes = audio(300 * 1024)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
        val calls = mutableListOf<Pair<Long, Long>>()

        downloader.download(server.url("/ep.mp3").toString(), target()) { done, total -> calls += done to total }

        assertTrue("no progress reported", calls.isNotEmpty())
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), calls.last())
        assertEquals(calls.map { it.first }.sorted(), calls.map { it.first })
        calls.forEach { (done, total) ->
            assertEquals(bytes.size.toLong(), total)
            assertTrue(done in 0..bytes.size.toLong())
        }
    }

    @Test
    fun `progress is not reported for every chunk`() = runTest {
        // 2 MB from a local server arrives in well under 500 ms, in many reads.
        val bytes = audio(2 * 1024 * 1024)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
        val calls = mutableListOf<Pair<Long, Long>>()

        downloader.download(server.url("/ep.mp3").toString(), target()) { done, total -> calls += done to total }

        assertTrue("${calls.size} progress calls", calls.size <= 10)
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), calls.last())
    }

    @Test
    fun `an unknown length reports 0 as the total`() = runTest {
        val bytes = audio(64 * 1024)
        server.enqueue(MockResponse().setResponseCode(200).setChunkedBody(Buffer().write(bytes), 4096))
        val calls = mutableListOf<Pair<Long, Long>>()
        val target = target()

        downloader.download(server.url("/ep.mp3").toString(), target) { done, total -> calls += done to total }

        assertArrayEquals(bytes, target.readBytes())
        assertEquals(bytes.size.toLong() to 0L, calls.last())
    }

    @Test
    fun `404 is NOT_FOUND`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        assertEquals(LoadError.NOT_FOUND, downloadError(server.url("/gone.mp3").toString(), target()))
    }

    @Test
    fun `410 is NOT_FOUND`() = runTest {
        server.enqueue(MockResponse().setResponseCode(410).setBody("Gone"))
        assertEquals(LoadError.NOT_FOUND, downloadError(server.url("/gone.mp3").toString(), target()))
    }

    @Test
    fun `500 is UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        assertEquals(LoadError.UNKNOWN, downloadError(server.url("/ep.mp3").toString(), target()))
    }

    @Test
    fun `403 is UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        assertEquals(LoadError.UNKNOWN, downloadError(server.url("/ep.mp3").toString(), target()))
    }

    @Test
    fun `an error page is not saved as audio`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not Found</html>"))
        val target = target()

        downloadError(server.url("/gone.mp3").toString(), target)

        assertFalse(target.exists() && target.readText().contains("Not Found"))
    }

    @Test
    fun `disconnect before a response is OFFLINE`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val target = target()

        assertEquals(LoadError.OFFLINE, downloadError(server.url("/ep.mp3").toString(), target))
        assertFalse("partial file left behind", target.exists())
    }

    @Test
    fun `disconnect during the body is OFFLINE and leaves no partial file`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(audio(512 * 1024)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val target = target()

        assertEquals(LoadError.OFFLINE, downloadError(server.url("/ep.mp3").toString(), target))
        assertFalse("partial file left behind", target.exists())
    }

    @Test
    fun `server not reachable is OFFLINE`() = runTest {
        val url = server.url("/ep.mp3").toString()
        server.shutdown()
        val target = target()

        assertEquals(LoadError.OFFLINE, downloadError(url, target))
        assertFalse(target.exists())
    }

    @Test
    fun `cancelling stops the transfer and deletes the partial file`() = runBlocking {
        // 1 MB sent 16 KB every 100 ms: takes several seconds, so it is cancelled part way.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(audio(1024 * 1024)))
                .throttleBody(16 * 1024, 100, TimeUnit.MILLISECONDS),
        )
        val target = target()
        val started = CompletableDeferred<Unit>()

        val job = async(Dispatchers.Default) {
            downloader.download(server.url("/ep.mp3").toString(), target) { done, _ ->
                if (done > 0) started.complete(Unit)
            }
        }
        // Progress may only be reported every 500 ms, so wait for the first bytes or the partial file.
        withTimeout(10_000) {
            while (!started.isCompleted && !(target.exists() && target.length() > 0)) {
                withContext(Dispatchers.Default) { Thread.sleep(20) }
            }
        }
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse("partial file left behind", target.exists())
    }
}
