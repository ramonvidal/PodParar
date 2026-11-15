package com.podparar.core.network

import com.podparar.core.model.Chapter
import com.podparar.core.model.Chapters
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToLong

/** Reads a Podcasting 2.0 chapters file. */
internal object ChapterJson {

    /**
     * chapters[] -> Chapter(startTime seconds * 1000 rounded, title, img, url), normalized.
     * Dropped: entries with "toc": false, without a numeric startTime or a non-blank title. img/url that aren't http(s) become null.
     * Malformed JSON or no "chapters" array -> PodcastException(NOT_A_FEED).
     */
    fun parse(json: String): List<Chapter> {
        try {
            val array = JSONObject(json).optJSONArray("chapters") ?: throw PodcastException(LoadError.NOT_A_FEED)
            val out = ArrayList<Chapter>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (!item.isNull("toc") && !item.optBoolean("toc", true)) continue
                val seconds = item.opt("startTime") as? Number ?: continue
                val value = seconds.toDouble()
                if (value.isNaN() || value.isInfinite()) continue
                val title = item.text("title") ?: continue
                out += Chapter(
                    startMs = (value * 1000.0).roundToLong(),
                    title = title,
                    imageUrl = item.text("img")?.takeIf { it.isWebUrl() },
                    url = item.text("url")?.takeIf { it.isWebUrl() },
                )
            }
            return Chapters.normalize(out)
        } catch (e: JSONException) {
            throw PodcastException(LoadError.NOT_A_FEED, e)
        }
    }

    private fun String.isWebUrl() = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
