package com.podparar.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Phase 4 on an in-memory database: what saveFeed reports as new, auto-download, download rows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreDownloadsTest {
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

    private val day = 86_400_000L
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
        description = "Notes",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = 1_800_000L,
    )

    private fun download(
        episodeId: String,
        state: DownloadState = DownloadState.DONE,
        filePath: String? = "/files/episodes/$episodeId.audio",
        bytes: Long = 1_000L,
        total: Long = 1_000L,
        updatedAt: Long = base,
    ) = Download(episodeId, state, filePath, bytes, total, updatedAt)

    // --- saveFeed returns the new episodes ---

    @Test
    fun `the first save returns every episode`() = runTest {
        val eps = listOf(episode(a, "1", base), episode(a, "2", base - day))

        val fresh = store.saveFeed(ParsedFeed(podcast(a), eps))

        assertEquals(setOf("$a#1", "$a#2"), fresh.map { it.id }.toSet())
        assertEquals(2, fresh.size)
    }

    @Test
    fun `saving again returns only the episodes not stored before`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1", base - day), episode(a, "2", base - 2 * day))))

        val fresh = store.saveFeed(
            ParsedFeed(podcast(a), listOf(episode(a, "3", base), episode(a, "1", base - day), episode(a, "2", base - 2 * day))),
        )

        assertEquals(listOf("$a#3"), fresh.map { it.id })
        assertEquals("Episode 3", fresh.single().title)
        assertEquals(3, store.episodes(a).size)
    }

    @Test
    fun `saving the same feed again returns nothing`() = runTest {
        val feed = ParsedFeed(podcast(a), listOf(episode(a, "1"), episode(a, "2", base - day)))
        store.saveFeed(feed)

        assertEquals(emptyList<Episode>(), store.saveFeed(feed))
    }

    @Test
    fun `a feed with no episodes returns nothing`() = runTest {
        assertEquals(emptyList<Episode>(), store.saveFeed(ParsedFeed(podcast(a), emptyList())))
    }

    @Test
    fun `another show's episodes do not count as stored`() = runTest {
        store.saveFeed(ParsedFeed(podcast(b), listOf(episode(b, "1"))))

        val fresh = store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1"))))

        assertEquals(listOf("$a#1"), fresh.map { it.id })
    }

    @Test
    fun `an episode that dropped off the feed and comes back is not new`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1", base - day))))
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "2", base))))

        val fresh = store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "2", base), episode(a, "1", base - day))))

        assertEquals(emptyList<Episode>(), fresh)
    }

    // --- autoDownload ---

    @Test
    fun `podcasts start without auto-download`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))
        assertFalse(store.podcast(a)!!.autoDownload)
    }

    @Test
    fun `setAutoDownload turns it on and off for that show only`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))
        store.saveFeed(ParsedFeed(podcast(b), emptyList()))

        store.setAutoDownload(a, true)
        assertTrue(store.podcast(a)!!.autoDownload)
        assertTrue(store.observePodcast(a).first()!!.autoDownload)
        assertFalse(store.podcast(b)!!.autoDownload)
        assertEquals(
            mapOf(a to true, b to false),
            store.observePodcasts().first().associate { it.feedUrl to it.autoDownload },
        )

        store.setAutoDownload(a, false)
        assertFalse(store.podcast(a)!!.autoDownload)
    }

    @Test
    fun `setAutoDownload on an unknown show changes nothing`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), emptyList()))

        store.setAutoDownload("https://unknown.example/feed", true)

        assertNull(store.podcast("https://unknown.example/feed"))
        assertEquals(listOf(a), store.podcastUrls())
        assertFalse(store.podcast(a)!!.autoDownload)
    }

    @Test
    fun `a refresh keeps the stored auto-download choice`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a, "Old"), listOf(episode(a, "1"))))
        store.setAutoDownload(a, true)

        // A refreshed feed knows nothing about auto-download, so its podcast says false.
        store.saveFeed(ParsedFeed(podcast(a, "New"), listOf(episode(a, "2", base + day))))

        val show = store.podcast(a)!!
        assertEquals("New", show.title)
        assertTrue(show.autoDownload)
    }

    // --- downloads ---

    @Test
    fun `no downloads at first`() = runTest {
        assertEquals(emptyMap<String, Download>(), store.observeDownloads().first())
        assertEquals(emptyList<Download>(), store.downloads())
        assertNull(store.download("$a#1"))
    }

    @Test
    fun `a download row round-trips every field`() = runTest {
        val done = download("$a#1")
        val running = download("$a#2", DownloadState.RUNNING, filePath = null, bytes = 400, total = 1_000, updatedAt = base + 5)
        val unknownTotal = download("$a#3", DownloadState.QUEUED, filePath = null, bytes = 0, total = 0)
        val failed = download("$b#1", DownloadState.FAILED, filePath = null, bytes = 12, total = 0)

        listOf(done, running, unknownTotal, failed).forEach { store.upsertDownload(it) }

        assertEquals(done, store.download(done.episodeId))
        assertEquals(running, store.download(running.episodeId))
        assertEquals(unknownTotal, store.download(unknownTotal.episodeId))
        assertEquals(failed, store.download(failed.episodeId))
        assertEquals(setOf(done, running, unknownTotal, failed), store.downloads().toSet())
        assertEquals(
            mapOf(done.episodeId to done, running.episodeId to running, unknownTotal.episodeId to unknownTotal, failed.episodeId to failed),
            store.observeDownloads().first(),
        )
    }

    @Test
    fun `upsert replaces the row for that episode`() = runTest {
        val id = "$a#1"
        store.upsertDownload(download(id, DownloadState.QUEUED, null, 0, 0))
        store.upsertDownload(download(id, DownloadState.RUNNING, null, 500, 1_000, base + 1))
        val done = download(id, DownloadState.DONE, "/files/x.audio", 1_000, 1_000, base + 2)
        store.upsertDownload(done)

        assertEquals(done, store.download(id))
        assertEquals(listOf(done), store.downloads())
        assertEquals(mapOf(id to done), store.observeDownloads().first())
    }

    @Test
    fun `deleteDownload removes only that row`() = runTest {
        store.upsertDownload(download("$a#1"))
        store.upsertDownload(download("$a#2"))

        store.deleteDownload("$a#1")
        store.deleteDownload("$a#unknown")

        assertNull(store.download("$a#1"))
        assertEquals(listOf("$a#2"), store.downloads().map { it.episodeId })
        assertEquals(setOf("$a#2"), store.observeDownloads().first().keys)
    }

    @Test
    fun `observed downloads follow writes`() = runTest {
        store.upsertDownload(download("$a#1", DownloadState.QUEUED, null, 0, 0))
        assertEquals(DownloadState.QUEUED, store.observeDownloads().first()["$a#1"]?.state)

        store.upsertDownload(download("$a#1", DownloadState.DONE))
        assertEquals(DownloadState.DONE, store.observeDownloads().first()["$a#1"]?.state)

        store.deleteDownload("$a#1")
        assertEquals(emptyMap<String, Download>(), store.observeDownloads().first())
    }

    @Test
    fun `a refresh does not touch download rows`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1"))))
        val row = download("$a#1")
        store.upsertDownload(row)

        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1"), episode(a, "2", base + day))))

        assertEquals(row, store.download("$a#1"))
    }

    @Test
    fun `deletePodcast removes that show's download rows, not other shows'`() = runTest {
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1"), episode(a, "2", base - day))))
        store.saveFeed(ParsedFeed(podcast(b), listOf(episode(b, "1"))))
        store.upsertDownload(download("$a#1"))
        store.upsertDownload(download("$a#2", DownloadState.FAILED, null, 0, 0))
        store.upsertDownload(download("$b#1"))

        store.deletePodcast(a)

        assertNull(store.download("$a#1"))
        assertNull(store.download("$a#2"))
        assertEquals(listOf("$b#1"), store.downloads().map { it.episodeId })
        assertEquals(setOf("$b#1"), store.observeDownloads().first().keys)
    }
}
