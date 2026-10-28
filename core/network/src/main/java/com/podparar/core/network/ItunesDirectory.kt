package com.podparar.core.network

import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Apple Podcasts search through the iTunes Search API. */
internal class ItunesDirectory(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://itunes.apple.com/".toHttpUrl(),
) : PodcastDirectory {

    override suspend fun search(term: String): List<SearchResult> {
        val query = term.trim()
        if (query.isEmpty()) return emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "40")
            .addQueryParameter("term", query)
            .build()
        val request = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) throw PodcastException(LoadError.UNKNOWN)
                    ItunesJson.parse(response.body!!.string())
                }
            } catch (e: PodcastException) {
                throw e
            } catch (e: IOException) {
                throw PodcastException(LoadError.OFFLINE, e)
            }
        }
    }
}
