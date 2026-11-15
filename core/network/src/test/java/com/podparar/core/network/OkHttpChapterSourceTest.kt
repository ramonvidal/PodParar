package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Chapter
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * ChapterSource against a local MockWebServer. The implementation class name OkHttpChapterSource(client) is assumed
 * (same pattern as OkHttpFeedSource).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OkHttpChapterSourceTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val source: ChapterSource = OkHttpChapterSource(client)

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

    private suspend fun fetchError(url: String): LoadError = try {
        source.fetch(url)
        fail("expected PodcastException")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json+chapters").setBody(body)

    @Test
    fun `200 with chapters json is parsed and normalized`() = runTest {
        server.enqueue(json("""{"version":"1.2.0","chapters":[{"startTime":60,"title":"Second"},{"startTime":0,"title":"First"},{"startTime":30,"title":"Hidden","toc":false}]}"""))
        val url = server.url("/chapters.json").toString()

        val chapters = source.fetch(url)

        assertEquals(listOf(Chapter(0, "First"), Chapter(60_000, "Second")), chapters)
        assertEquals("/chapters.json", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun `404 is NOT_FOUND`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        assertEquals(LoadError.NOT_FOUND, fetchError(server.url("/c.json").toString()))
    }

    @Test
    fun `410 is NOT_FOUND`() = runTest {
        server.enqueue(MockResponse().setResponseCode(410).setBody("Gone"))
        assertEquals(LoadError.NOT_FOUND, fetchError(server.url("/c.json").toString()))
    }

    @Test
    fun `500 is UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        assertEquals(LoadError.UNKNOWN, fetchError(server.url("/c.json").toString()))
    }

    @Test
    fun `non-json body is NOT_A_FEED`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody("<html>hello</html>"))
        assertEquals(LoadError.NOT_A_FEED, fetchError(server.url("/c.json").toString()))
    }

    @Test
    fun `json without a chapters array is NOT_A_FEED`() = runTest {
        server.enqueue(json("""{"version":"1.2.0"}"""))
        assertEquals(LoadError.NOT_A_FEED, fetchError(server.url("/c.json").toString()))
    }

    @Test
    fun `disconnect before a response is OFFLINE`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        assertEquals(LoadError.OFFLINE, fetchError(server.url("/c.json").toString()))
    }

    @Test
    fun `server not reachable is OFFLINE`() = runTest {
        val url = server.url("/c.json").toString()
        server.shutdown()
        assertEquals(LoadError.OFFLINE, fetchError(url))
    }

    @Test
    fun `a body over 1 MB is UNKNOWN`() = runTest {
        val padding = "x".repeat(1_100_000)
        server.enqueue(json("""{"chapters":[{"startTime":0,"title":"$padding"}]}"""))
        assertEquals(LoadError.UNKNOWN, fetchError(server.url("/big.json").toString()))
    }
}
