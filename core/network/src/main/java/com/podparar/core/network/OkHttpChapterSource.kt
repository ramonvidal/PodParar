package com.podparar.core.network

import com.podparar.core.model.Chapter
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Downloads a chapters file (at most 1 MB) with OkHttp and parses it with [ChapterJson]. */
internal class OkHttpChapterSource(private val client: OkHttpClient) : ChapterSource {

    override suspend fun fetch(url: String): List<Chapter> = withContext(Dispatchers.IO) {
        // HttpUrl only accepts http and https.
        val httpUrl = url.trim().toHttpUrlOrNull() ?: throw PodcastException(LoadError.UNKNOWN)
        val request = Request.Builder().url(httpUrl).get().build()
        try {
            client.newCall(request).await().use { response ->
                when {
                    response.code == 404 || response.code == 410 -> throw PodcastException(LoadError.NOT_FOUND)
                    !response.isSuccessful -> throw PodcastException(LoadError.UNKNOWN)
                }
                val body = response.body ?: throw PodcastException(LoadError.NOT_A_FEED)
                if (body.contentLength() > MAX_BYTES) throw PodcastException(LoadError.UNKNOWN)
                val source = body.source()
                // Ask for one byte more than the limit so a body that is too big is detected without reading it all.
                source.request(MAX_BYTES + 1)
                if (source.buffer.size > MAX_BYTES) throw PodcastException(LoadError.UNKNOWN)
                ChapterJson.parse(source.readUtf8())
            }
        } catch (e: PodcastException) {
            throw e
        } catch (e: IOException) {
            throw PodcastException(LoadError.OFFLINE, e)
        }
    }

    private companion object {
        const val MAX_BYTES = 1_000_000L
    }
}
