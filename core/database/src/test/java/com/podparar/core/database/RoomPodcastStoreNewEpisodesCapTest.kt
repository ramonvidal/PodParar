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

/**
 * RoomPodcastStore.observeNewEpisodes' cap rule: at most `limit` unplayed, unstarted episodes;
 * DONE downloads always show, the remaining slots go to the newest non-downloaded ones.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreNewEpisodesCapTest {
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

    private suspend fun ids(limit: Int = 50) =
        store.observeNewEpisodes(limit).first().map { it.episode.id }

    @Test
    fun `without downloads the newest limit episodes show and older ones drop off`() = runTest {
        val eps = (1..6).map { episode("$it", base - it * day) }
        save(*eps.toTypedArray())

        assertEquals(eps.take(3).map { it.id }, ids(limit = 3))
    }

    @Test
    fun `old episodes show while there is room under the limit`() = runTest {
        val old = episode("old", base - 400 * day)
        val recent = episode("recent", base)
        save(old, recent)

        assertEquals(listOf(recent.id, old.id), ids())
    }

    @Test
    fun `a DONE download always shows even when old and beyond the newest slots`() = runTest {
        val newest = (1..5).map { episode("n$it", base - it * day) }
        val oldDone = episode("olddone", base - 400 * day)
        save(*newest.toTypedArray(), oldDone)
        download(oldDone, DownloadState.DONE)

        // limit 3: one slot is taken by the downloaded episode, two go to the newest others.
        assertEquals(listOf(newest[0].id, newest[1].id, oldDone.id), ids(limit = 3))
    }

    @Test
    fun `many old downloaded episodes all show and push non-downloaded ones out`() = runTest {
        val done = (1..4).map { episode("d$it", base - (300 + it) * day) }
        val others = (1..10).map { episode("o$it", base - it * day) }
        save(*done.toTypedArray(), *others.toTypedArray())
        done.forEach { download(it, DownloadState.DONE) }

        val result = ids(limit = 6)

        assertEquals(6, result.size)
        assertEquals(done.map { it.id }.toSet(), result.filter { id -> done.any { it.id == id } }.toSet())
        assertEquals(listOf(others[0].id, others[1].id, done[0].id, done[1].id, done[2].id, done[3].id), result)
    }

    @Test
    fun `when downloaded episodes reach the limit the newest limit of them show`() = runTest {
        val done = (1..5).map { episode("d$it", base - it * day) }
        val fresh = episode("fresh", base + day)
        save(*done.toTypedArray(), fresh)
        done.forEach { download(it, DownloadState.DONE) }

        assertEquals(done.take(3).map { it.id }, ids(limit = 3))
    }

    @Test
    fun `when downloaded episodes exceed the limit the cap still applies`() = runTest {
        val done = (1..8).map { episode("d$it", base - it * day) }
        save(*done.toTypedArray())
        done.forEach { download(it, DownloadState.DONE) }

        assertEquals(done.take(5).map { it.id }, ids(limit = 5))
    }

    @Test
    fun `a QUEUED, RUNNING or FAILED download does not exempt an episode`() = runTest {
        val newer = (1..3).map { episode("n$it", base - it * day) }
        val queued = episode("queued", base - 400 * day)
        val running = episode("running", base - 401 * day)
        val failed = episode("failed", base - 402 * day)
        save(*newer.toTypedArray(), queued, running, failed)
        download(queued, DownloadState.QUEUED)
        download(running, DownloadState.RUNNING)
        download(failed, DownloadState.FAILED)

        assertEquals(newer.map { it.id }, ids(limit = 3))
    }

    @Test
    fun `a downloaded episode that is played or started is left out`() = runTest {
        val played = episode("played", base - day, played = true)
        val started = episode("started", base - 2 * day, positionMs = 5_000L)
        save(played, started)
        download(played, DownloadState.DONE)
        download(started, DownloadState.DONE)

        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun `played and started episodes do not take slots from the others`() = runTest {
        val played = episode("played", base, played = true)
        val started = episode("started", base - day, positionMs = 1L)
        val one = episode("one", base - 2 * day)
        val two = episode("two", base - 3 * day)
        save(played, started, one, two)

        assertEquals(listOf(one.id, two.id), ids(limit = 2))
    }

    @Test
    fun `results are newest first with id ascending on ties and undated last`() = runTest {
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
    fun `undated episodes are ordinary and lose out to newer ones under the cap`() = runTest {
        val unknown = episode("unknown", 0L)
        val negative = episode("negative", -1L)
        val dated1 = episode("d1", base)
        val dated2 = episode("d2", base - day)
        save(unknown, negative, dated1, dated2)

        assertEquals(listOf(dated1.id, dated2.id), ids(limit = 2))
    }

    @Test
    fun `undated episodes show when there is room`() = runTest {
        val unknown = episode("unknown", 0L)
        val dated = episode("dated", base)
        save(unknown, dated)

        assertEquals(listOf(dated.id, unknown.id), ids())
    }

    @Test
    fun `a download finishing brings an old episode back in`() = runTest {
        val newer = (1..3).map { episode("n$it", base - it * day) }
        val old = episode("old", base - 400 * day)
        save(*newer.toTypedArray(), old)
        assertEquals(newer.map { it.id }, ids(limit = 3))

        download(old, DownloadState.DONE)

        assertEquals(listOf(newer[0].id, newer[1].id, old.id), ids(limit = 3))
    }

    @Test
    fun `results carry the show's title`() = runTest {
        val one = episode("1", base)
        save(one)

        assertEquals(
            listOf(EpisodeWithPodcast(one, "Show", null)),
            store.observeNewEpisodes(50).first(),
        )
    }
}
