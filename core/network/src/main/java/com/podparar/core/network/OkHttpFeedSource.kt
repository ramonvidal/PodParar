package com.podparar.core.network

import com.podparar.core.model.LoadError
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Downloads a feed with OkHttp and parses it with [FeedParser]. */
internal class OkHttpFeedSource(private val client: OkHttpClient) : FeedSource {

    override suspend fun fetch(feedUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val url = feedUrl.toHttpUrlOrNull() ?: throw PodcastException(LoadError.INVALID_LINK)
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).await().use { response ->
                when {
                    response.code == 404 || response.code == 410 -> throw PodcastException(LoadError.NOT_FOUND)
                    !response.isSuccessful -> throw PodcastException(LoadError.UNKNOWN)
                }
                FeedParser.parse(response.body!!.byteStream(), feedUrl)
            }
        } catch (e: PodcastException) {
            throw e
        } catch (e: IOException) {
            throw PodcastException(LoadError.OFFLINE, e)
        }
    }
}
