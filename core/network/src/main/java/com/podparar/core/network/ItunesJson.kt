package com.podparar.core.network

import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
import org.json.JSONException
import org.json.JSONObject

/** Reads an iTunes Search API response. */
internal object ItunesJson {

    /**
     * results[] -> SearchResult(collectionName, artistName, feedUrl, artworkUrl600 ?: artworkUrl100 (blank -> null)).
     * Results without a non-blank feedUrl are dropped; duplicate feedUrls keep the first.
     * Malformed JSON -> PodcastException(UNKNOWN).
     */
    fun parse(json: String): List<SearchResult> {
        try {
            val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
            val seen = HashSet<String>()
            val out = ArrayList<SearchResult>(results.length())
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val feedUrl = item.text("feedUrl") ?: continue
                if (!seen.add(feedUrl)) continue
                out += SearchResult(
                    title = item.text("collectionName").orEmpty(),
                    author = item.text("artistName").orEmpty(),
                    feedUrl = feedUrl,
                    artworkUrl = item.text("artworkUrl600") ?: item.text("artworkUrl100"),
                )
            }
            return out
        } catch (e: JSONException) {
            throw PodcastException(LoadError.UNKNOWN, e)
        }
    }

    /** The trimmed string value of [key]; null when missing, JSON null or blank. */
    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
