package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** OkHttpFeedSource against a local MockWebServer: HTTP outcomes map to LoadError. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OkHttpFeedSourceTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val source = OkHttpFeedSource(client)

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

    private fun fixtureBytes(name: String): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("feeds/$name")) { "missing fixture feeds/$name" }
            .use { it.readBytes() }

    private fun feedResponse(name: String, contentType: String = "application/rss+xml") =
        MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(Buffer().write(fixtureBytes(name)))

    private suspend fun fetchError(url: String): LoadError = try {
        source.fetch(url)
        fail("expected PodcastException")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    @Test
    fun `200 with a feed is parsed`() = runTest {
        server.enqueue(feedResponse("typical.xml"))
        val url = server.url("/workshop.xml").toString()

        val feed = source.fetch(url)

        assertEquals(url, feed.podcast.feedUrl)
        assertEquals("The Workshop Hour", feed.podcast.title)
        assertEquals(3, feed.episodes.size)
        assertEquals("$url#workshop-ep-42", feed.episodes[0].id)
        assertEquals("/workshop.xml", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun `iso-8859-1 feed served without a charset is decoded from its xml declaration`() = runTest {
        server.enqueue(feedResponse("latin1.xml", contentType = "application/xml"))
        val feed = source.fetch(server.url("/cafe.xml").toString())
        assertEquals("Café Crème", feed.podcast.title)
    }

    @Test
    fun `404 is NOT_FOUND`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        assertEquals(LoadError.NOT_FOUND, fetchError(server.url("/gone.xml").toString()))
    }

    @Test
    fun `410 is NOT_FOUND`() = runTest {
        server.enqueue(MockResponse().setResponseCode(410).setBody("Gone"))
        assertEquals(LoadError.NOT_FOUND, fetchError(server.url("/gone.xml").toString()))
    }

    @Test
    fun `500 is UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        assertEquals(LoadError.UNKNOWN, fetchError(server.url("/feed.xml").toString()))
    }

    @Test
    fun `403 is UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        assertEquals(LoadError.UNKNOWN, fetchError(server.url("/feed.xml").toString()))
    }

    @Test
    fun `html page is NOT_A_FEED`() = runTest {
        server.enqueue(feedResponse("not_a_feed.html", contentType = "text/html; charset=utf-8"))
        assertEquals(LoadError.NOT_A_FEED, fetchError(server.url("/").toString()))
    }

    @Test
    fun `malformed xml is NOT_A_FEED`() = runTest {
        server.enqueue(feedResponse("malformed.xml"))
        assertEquals(LoadError.NOT_A_FEED, fetchError(server.url("/broken.xml").toString()))
    }

    @Test
    fun `disconnect before a response is OFFLINE`() = runTest {
        // OkHttp may retry a failed connection once, so fail every attempt.
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        assertEquals(LoadError.OFFLINE, fetchError(server.url("/feed.xml").toString()))
    }

    @Test
    fun `server not reachable is OFFLINE`() = runTest {
        val url = server.url("/feed.xml").toString()
        server.shutdown()
        assertEquals(LoadError.OFFLINE, fetchError(url))
    }
}
