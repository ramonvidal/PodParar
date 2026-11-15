package com.podparar.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Chapter
import com.podparar.core.model.Episode
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** RoomPodcastStore chapters: stored in order, replaced as a whole, removed with the show; episode chaptersUrl kept. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreChaptersTest {
    private lateinit var db: PodPararDatabase
    private lateinit var store: PodcastStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PodPararDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomPodcastStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val feedA = "https://a.example/feed"
    private val feedB = "https://b.example/feed"

    private fun podcast(feedUrl: String) = Podcast(
        feedUrl = feedUrl,
        title = "Show $feedUrl",
        author = "Author",
        description = "About",
        artworkUrl = null,
        lastRefreshed = 1_000L,
    )

    private fun episode(feedUrl: String, guid: String, chaptersUrl: String? = null, positionMs: Long = 0L) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = "Episode $guid",
        description = "Notes",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = null,
        pubDate = 1_000L,
        durationMs = 1_800_000L,
        positionMs = positionMs,
        chaptersUrl = chaptersUrl,
    )

    private val three = listOf(
        Chapter(0, "Intro"),
        Chapter(60_000, "Main", imageUrl = "https://img.example/main.jpg", url = "https://example.com/main"),
        Chapter(300_000, "Outro"),
    )

    private suspend fun seed() {
        store.saveFeed(ParsedFeed(podcast(feedA), listOf(episode(feedA, "1"), episode(feedA, "2"))))
        store.saveFeed(ParsedFeed(podcast(feedB), listOf(episode(feedB, "1"))))
    }

    @Test
    fun `no chapters stored gives an empty list`() = runTest {
        seed()
        assertEquals(emptyList<Chapter>(), store.chapters("$feedA#1"))
        assertEquals(emptyList<Chapter>(), store.observeChapters("$feedA#1").first())
        assertEquals(emptyList<Chapter>(), store.chapters("unknown"))
    }

    @Test
    fun `saved chapters come back in order with all fields`() = runTest {
        seed()
        store.saveChapters("$feedA#1", three)

        assertEquals(three, store.chapters("$feedA#1"))
        assertEquals(three, store.observeChapters("$feedA#1").first())
    }

    @Test
    fun `chapters keep the order they were saved in`() = runTest {
        seed()
        val ordered = listOf(Chapter(0, "Zebra"), Chapter(1_000, "Apple"), Chapter(2_000, "Mango"))
        store.saveChapters("$feedA#1", ordered)
        assertEquals(listOf("Zebra", "Apple", "Mango"), store.chapters("$feedA#1").map { it.title })
    }

    @Test
    fun `saving again replaces all the previous chapters`() = runTest {
        seed()
        store.saveChapters("$feedA#1", three)
        val replacement = listOf(Chapter(0, "Only one"))

        store.saveChapters("$feedA#1", replacement)

        assertEquals(replacement, store.chapters("$feedA#1"))
    }

    @Test
    fun `saving an empty list clears the chapters`() = runTest {
        seed()
        store.saveChapters("$feedA#1", three)

        store.saveChapters("$feedA#1", emptyList())

        assertEquals(emptyList<Chapter>(), store.chapters("$feedA#1"))
    }

    @Test
    fun `chapters of one episode do not touch another's`() = runTest {
        seed()
        store.saveChapters("$feedA#1", three)
        store.saveChapters("$feedA#2", listOf(Chapter(0, "Other")))

        store.saveChapters("$feedA#1", emptyList())

        assertEquals(listOf(Chapter(0, "Other")), store.chapters("$feedA#2"))
    }

    @Test
    fun `observeChapters follows changes`() = runTest {
        seed()
        assertEquals(emptyList<Chapter>(), store.observeChapters("$feedA#1").first())
        store.saveChapters("$feedA#1", three)
        assertEquals(three, store.observeChapters("$feedA#1").first())
        store.saveChapters("$feedA#1", emptyList())
        assertEquals(emptyList<Chapter>(), store.observeChapters("$feedA#1").first())
    }

    @Test
    fun `deleting a show removes its episodes' chapters but not other shows'`() = runTest {
        seed()
        store.saveChapters("$feedA#1", three)
        store.saveChapters("$feedA#2", three)
        store.saveChapters("$feedB#1", listOf(Chapter(0, "Keep me")))

        store.deletePodcast(feedA)

        assertEquals(emptyList<Chapter>(), store.chapters("$feedA#1"))
        assertEquals(emptyList<Chapter>(), store.chapters("$feedA#2"))
        assertEquals(listOf(Chapter(0, "Keep me")), store.chapters("$feedB#1"))
    }

    @Test
    fun `episode chaptersUrl round trips`() = runTest {
        store.saveFeed(
            ParsedFeed(
                podcast(feedA),
                listOf(episode(feedA, "1", chaptersUrl = "https://a.example/1-chapters.json"), episode(feedA, "2")),
            ),
        )

        assertEquals("https://a.example/1-chapters.json", store.episode("$feedA#1")!!.chaptersUrl)
        assertNull(store.episode("$feedA#2")!!.chaptersUrl)
        assertEquals(
            listOf("https://a.example/1-chapters.json", null).toSet(),
            store.episodes(feedA).map { it.chaptersUrl }.toSet(),
        )
        assertEquals("https://a.example/1-chapters.json", store.observeEpisode("$feedA#1").first()!!.chaptersUrl)
    }

    @Test
    fun `a refresh takes a fresh chaptersUrl and keeps progress`() = runTest {
        store.saveFeed(ParsedFeed(podcast(feedA), listOf(episode(feedA, "1", chaptersUrl = "https://a.example/old.json"))))
        store.savePosition("$feedA#1", 123_000L, 1_800_000L, 9_000L)

        store.saveFeed(ParsedFeed(podcast(feedA), listOf(episode(feedA, "1", chaptersUrl = "https://a.example/new.json"))))

        val stored = store.episode("$feedA#1")!!
        assertEquals("https://a.example/new.json", stored.chaptersUrl)
        assertEquals(123_000L, stored.positionMs)
    }

    @Test
    fun `a refresh without a chaptersUrl keeps the stored one`() = runTest {
        store.saveFeed(ParsedFeed(podcast(feedA), listOf(episode(feedA, "1", chaptersUrl = "https://a.example/old.json"))))

        store.saveFeed(ParsedFeed(podcast(feedA), listOf(episode(feedA, "1", chaptersUrl = null))))

        assertEquals("https://a.example/old.json", store.episode("$feedA#1")!!.chaptersUrl)
    }

    @Test
    fun `a refresh keeps the stored chapters`() = runTest {
        seed()
        store.saveChapters("$feedA#1", three)

        store.saveFeed(ParsedFeed(podcast(feedA), listOf(episode(feedA, "1", chaptersUrl = "https://a.example/new.json"))))

        assertEquals(three, store.chapters("$feedA#1"))
    }
}
