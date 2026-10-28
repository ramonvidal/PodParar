package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
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

/** ItunesDirectory against a local MockWebServer standing in for itunes.apple.com. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ItunesDirectoryTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: ItunesDirectory
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = ItunesDirectory(client, server.url("/"))
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (e: Exception) {
            // Already shut down by the test.
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("itunes/$name")) { "missing fixture itunes/$name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "text/javascript; charset=utf-8").setBody(body)

    private suspend fun searchError(term: String): LoadError = try {
        directory.search(term)
        fail("expected PodcastException")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    @Test
    fun `search sends media, entity and term to the search endpoint`() = runTest {
        server.enqueue(json(fixture("search.json")))

        directory.search("the daily")

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        val url = request.requestUrl!!
        assertEquals("GET", request.method)
        assertEquals("/search", url.encodedPath)
        assertEquals("podcast", url.queryParameter("media"))
        assertEquals("podcast", url.queryParameter("entity"))
        assertEquals("the daily", url.queryParameter("term"))
    }

    @Test
    fun `term with reserved characters is encoded and arrives intact`() = runTest {
        server.enqueue(json("""{"resultCount":0,"results":[]}"""))

        directory.search("Rock & Roll #1 café?")

        val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("Rock & Roll #1 café?", url.queryParameter("term"))
        assertEquals("podcast", url.queryParameter("media"))
    }

    @Test
    fun `results are parsed`() = runTest {
        server.enqueue(json(fixture("search.json")))

        val results = directory.search("daily")

        assertEquals(3, results.size)
        assertEquals(
            SearchResult(
                title = "The Daily",
                author = "The New York Times",
                feedUrl = "https://feeds.simplecast.com/54nAGcIl",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts/daily/600x600bb.jpg",
            ),
            results[0],
        )
    }

    @Test
    fun `blank term returns nothing without a request`() = runTest {
        assertEquals(emptyList<SearchResult>(), directory.search(""))
        assertEquals(emptyList<SearchResult>(), directory.search("   "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server error is UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        assertEquals(LoadError.UNKNOWN, searchError("daily"))
    }

    @Test
    fun `malformed body is UNKNOWN`() = runTest {
        server.enqueue(json("<html><body>Oops</body></html>"))
        assertEquals(LoadError.UNKNOWN, searchError("daily"))
    }

    @Test
    fun `disconnect before a response is OFFLINE`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        assertEquals(LoadError.OFFLINE, searchError("daily"))
    }

    @Test
    fun `server not reachable is OFFLINE`() = runTest {
        server.shutdown()
        assertEquals(LoadError.OFFLINE, searchError("daily"))
    }
}
