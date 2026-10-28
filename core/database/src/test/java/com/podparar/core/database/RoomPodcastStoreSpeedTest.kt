package com.podparar.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Episode
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Phase 5 on an in-memory database: the per-show speed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreSpeedTest {
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

    private val delta = 0.0001f
    private val base = 1_727_863_200_000L
    private val a = "https://a.example/feed"
    private val b = "https://b.example/feed"

    private fun podcast(feedUrl: String, title: String = "Show $feedUrl") = Podcast(
        feedUrl = feedUrl,
        title = title,
        author = "Author",
        description = "About",
        artworkUrl = null,
        lastRefreshed = base,
    )

    private fun episode(feedUrl: String, guid: String, pubDate: Long = base) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = "Episode $guid",
        description = "",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = 1_800_000L,
    )

    @Test
    fun `podcasts start without a speed of their own`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))
        assertNull(store.podcast(a)!!.speed)
        assertNull(store.observePodcast(a).first()!!.speed)
    }

    @Test
    fun `setSpeed sets that show's speed only`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))
        store.saveFeed(ParsedFeed(podcast(b), emptyList()))

        store.setSpeed(a, 1.5f)

        assertEquals(1.5f, store.podcast(a)!!.speed!!, delta)
        assertEquals(1.5f, store.observePodcast(a).first()!!.speed!!, delta)
        assertNull(store.podcast(b)!!.speed)
        assertEquals(
            mapOf(a to 1.5f, b to null),
            store.observePodcasts().first().associate { it.feedUrl to it.speed },
        )

        store.setSpeed(a, 2.5f)
        assertEquals(2.5f, store.podcast(a)!!.speed!!, delta)
    }

    @Test
    fun `setSpeed with null clears it`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))
        store.setSpeed(a, 1.8f)

        store.setSpeed(a, null)

        assertNull(store.podcast(a)!!.speed)
    }

    @Test
    fun `setSpeed on an unknown show changes nothing`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))

        store.setSpeed("https://unknown.example/feed", 2.0f)

        assertNull(store.podcast("https://unknown.example/feed"))
        assertEquals(listOf(a), store.podcastUrls())
        assertNull(store.podcast(a)!!.speed)
    }

    @Test
    fun `setSpeed leaves auto-download and the other fields alone`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a, "A"), listOf(episode(a, "1"))))
        store.setAutoDownload(a, true)

        store.setSpeed(a, 1.2f)

        val show = store.podcast(a)!!
        assertEquals(podcast(a, "A").copy(autoDownload = true, speed = 1.2f), show)
        assertEquals(listOf("$a#1"), store.episodes(a).map { it.id })
    }

    @Test
    fun `a refresh keeps the stored speed`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a, "Old"), listOf(episode(a, "1"))))
        store.setSpeed(a, 1.8f)
        store.setAutoDownload(a, true)

        // A refreshed feed knows nothing about the show's speed, so its podcast says null.
        store.saveFeed(ParsedFeed(podcast(a, "New"), listOf(episode(a, "2", base + 1))))

        val show = store.podcast(a)!!
        assertEquals("New", show.title)
        assertEquals(1.8f, show.speed!!, delta)
        assertTrue(show.autoDownload)
    }

    @Test
    fun `resubscribing after unsubscribing starts without a speed`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))
        store.setSpeed(a, 2.0f)
        store.deletePodcast(a)

        store.saveFeed(ParsedFeed(podcast(a), emptyList()))

        assertNull(store.podcast(a)!!.speed)
    }
}
