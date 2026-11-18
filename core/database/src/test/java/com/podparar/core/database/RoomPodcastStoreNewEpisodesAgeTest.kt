package com.podparar.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** RoomPodcastStore.observeNewEpisodes' age rule: recent, undated or downloaded (DONE) episodes only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreNewEpisodesAgeTest {
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
    private val base = 1_727_863_200_000L // 2024-10-02T10:00:00Z
    private val since = base - 30 * day
    private val a = "https://a.example/feed"

    private fun podcast(feedUrl: String = a) = Podcast(
        feedUrl = feedUrl,
        title = "Show",
        author = "Author",
        description = "About",
        artworkUrl = null,
        lastRefreshed = base,
    )

    private fun episode(guid: String, pubDate: Long, positionMs: Long = 0L, played: Boolean = false) = Episode(
        id = "$a#$guid",
        podcastUrl = a,
        title = "Episode $guid",
        description = "Notes for $guid",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = 1_800_000L,
        positionMs = positionMs,
        played = played,
        lastPlayedAt = 0L,
    )

    private suspend fun save(vararg episodes: Episode) {
        store.saveFeed(ParsedFeed(podcast(), episodes.toList()))
    }

    private suspend fun download(episode: Episode, state: DownloadState) {
        store.upsertDownload(Download(episode.id, state, null, 0L, 0L, base))
    }

    private suspend fun ids(limit: Int = 50, sinceMs: Long = since) =
        store.observeNewEpisodes(limit, sinceMs).first().map { it.episode.id }

    @Test
    fun `episodes older than sinceMs are left out`() = runTest {
        val recent = episode("recent", base - day)
        val old = episode("old", since - 1)
        save(recent, old)

        assertEquals(listOf(recent.id), ids())
    }

    @Test
    fun `an episode exactly at sinceMs is included`() = runTest {
        val edge = episode("edge", since)
        val before = episode("before", since - 1)
        save(edge, before)

        assertEquals(listOf(edge.id), ids())
    }

    @Test
    fun `an episode with an unknown pubDate is included`() = runTest {
        val unknown = episode("unknown", 0L)
        val negative = episode("negative", -1L)
        val old = episode("old", since - day)
        save(unknown, negative, old)

        assertEquals(setOf(unknown.id, negative.id), ids().toSet())
    }

    @Test
    fun `an old episode with a DONE download is included`() = runTest {
        val old = episode("old", base - 400 * day)
        val oldNoDownload = episode("other", base - 400 * day)
        save(old, oldNoDownload)
        download(old, DownloadState.DONE)

        assertEquals(listOf(old.id), ids())
    }

    @Test
    fun `a QUEUED, RUNNING or FAILED download does not exempt an old episode`() = runTest {
        val queued = episode("queued", base - 400 * day)
        val running = episode("running", base - 400 * day)
        val failed = episode("failed", base - 400 * day)
        save(queued, running, failed)
        download(queued, DownloadState.QUEUED)
        download(running, DownloadState.RUNNING)
        download(failed, DownloadState.FAILED)

        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun `a downloaded old episode that is played or started is still left out`() = runTest {
        val played = episode("played", base - 400 * day, played = true)
        val started = episode("started", base - 400 * day, positionMs = 5_000L)
        save(played, started)
        download(played, DownloadState.DONE)
        download(started, DownloadState.DONE)

        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun `results are newest first with id ascending on ties, mixing recent and downloaded old ones`() = runTest {
        val newest = episode("newest", base)
        val tieB = episode("b", base - day)
        val tieA = episode("a", base - day)
        val oldDone = episode("olddone", base - 400 * day)
        val unknown = episode("unknown", 0L)
        save(newest, tieB, tieA, oldDone, unknown)
        download(oldDone, DownloadState.DONE)

        assertEquals(listOf(newest.id, tieA.id, tieB.id, oldDone.id, unknown.id), ids())
    }

    @Test
    fun `the limit applies after the age filter`() = runTest {
        val eps = (1..6).map { episode("$it", base - it * day) }
        val old = episode("old", since - day)
        save(*eps.toTypedArray(), old)

        assertEquals(eps.take(3).map { it.id }, ids(limit = 3))
    }

    @Test
    fun `a download finishing brings an old episode in`() = runTest {
        val old = episode("old", base - 400 * day)
        save(old)
        assertEquals(emptyList<String>(), ids())

        download(old, DownloadState.DONE)

        assertEquals(listOf(old.id), ids())
    }

    @Test
    fun `Long MIN_VALUE as sinceMs lets every dated episode through`() = runTest {
        val old = episode("old", 1L)
        save(old)

        assertEquals(listOf(old.id), ids(sinceMs = Long.MIN_VALUE))
    }

    @Test
    fun `results carry the show's title`() = runTest {
        val one = episode("1", base)
        save(one)

        assertEquals(
            listOf(EpisodeWithPodcast(one, "Show", null)),
            store.observeNewEpisodes(50, since).first(),
        )
    }
}
