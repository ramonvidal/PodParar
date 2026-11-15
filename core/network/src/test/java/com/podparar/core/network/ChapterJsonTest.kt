package com.podparar.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Chapter
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** ChapterJson: Podcasting 2.0 chapters JSON to normalized chapters. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChapterJsonTest {
    private fun parseError(json: String): LoadError = try {
        ChapterJson.parse(json)
        fail("expected PodcastException(NOT_A_FEED)")
        throw AssertionError()
    } catch (e: PodcastException) {
        e.error
    }

    @Test
    fun `integer seconds become milliseconds`() {
        val json = """{"version":"1.2.0","chapters":[{"startTime":0,"title":"Intro"},{"startTime":90,"title":"Main"}]}"""
        assertEquals(listOf(Chapter(0, "Intro"), Chapter(90_000, "Main")), ChapterJson.parse(json))
    }

    @Test
    fun `float seconds are rounded to milliseconds`() {
        val json = """{"chapters":[{"startTime":12.5,"title":"A"},{"startTime":61.2345,"title":"B"}]}"""
        val result = ChapterJson.parse(json)
        assertEquals(listOf(12_500L, 61_235L), result.map { it.startMs })
    }

    @Test
    fun `toc false entries are dropped, toc true and missing toc are kept`() {
        val json = """{"chapters":[
            {"startTime":0,"title":"Intro"},
            {"startTime":10,"title":"Hidden","toc":false},
            {"startTime":20,"title":"Shown","toc":true}
        ]}"""
        assertEquals(listOf("Intro", "Shown"), ChapterJson.parse(json).map { it.title })
    }

    @Test
    fun `entries without a title are dropped`() {
        val json = """{"chapters":[{"startTime":0,"title":"Intro"},{"startTime":10},{"startTime":20,"title":""}]}"""
        assertEquals(listOf("Intro"), ChapterJson.parse(json).map { it.title })
    }

    @Test
    fun `chapters are sorted by start`() {
        val json = """{"chapters":[{"startTime":300,"title":"C"},{"startTime":0,"title":"A"},{"startTime":60,"title":"B"}]}"""
        assertEquals(listOf("A", "B", "C"), ChapterJson.parse(json).map { it.title })
    }

    @Test
    fun `img and url are kept`() {
        val json = """{"chapters":[{"startTime":5,"title":"Link","img":"https://img.example/1.jpg","url":"https://example.com/1"}]}"""
        assertEquals(
            listOf(Chapter(5_000, "Link", imageUrl = "https://img.example/1.jpg", url = "https://example.com/1")),
            ChapterJson.parse(json),
        )
    }

    @Test
    fun `other fields are ignored`() {
        val json = """{"version":"1.2.0","title":"Ep","chapters":[{"startTime":0,"endTime":30,"title":"A","location":{"name":"Paris"}}]}"""
        assertEquals(listOf(Chapter(0, "A")), ChapterJson.parse(json))
    }

    @Test
    fun `empty chapters array is an empty list`() {
        assertEquals(emptyList<Chapter>(), ChapterJson.parse("""{"chapters":[]}"""))
    }

    @Test
    fun `invalid json is NOT_A_FEED`() {
        assertEquals(LoadError.NOT_A_FEED, parseError("this is not json {"))
        assertEquals(LoadError.NOT_A_FEED, parseError(""))
        assertEquals(LoadError.NOT_A_FEED, parseError("<html><body>Hello</body></html>"))
    }

    @Test
    fun `missing chapters array is NOT_A_FEED`() {
        assertEquals(LoadError.NOT_A_FEED, parseError("""{"version":"1.2.0"}"""))
        assertEquals(LoadError.NOT_A_FEED, parseError("""{"chapters":"nope"}"""))
    }
}
