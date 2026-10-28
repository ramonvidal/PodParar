package com.podparar.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.ParsedFeed
import com.podparar.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** RoomPodcastStore's Phase 3 calls: the Up next queue, Continue listening, New episodes, Mark all played. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreQueueTest {
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
    private val b = "https://b.example/feed"

    private fun podcast(feedUrl: String, title: String, artworkUrl: String? = "https://img.example/$title.jpg") = Podcast(
        feedUrl = feedUrl,
        title = title,
        author = "Author of $title",
        description = "About $title",
        artworkUrl = artworkUrl,
        lastRefreshed = base,
    )

    private fun episode(
        feedUrl: String,
        guid: String,
        pubDate: Long = base,
        positionMs: Long = 0L,
        played: Boolean = false,
        lastPlayedAt: Long = 0L,
        imageUrl: String? = null,
    ) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = "Episode $guid",
        description = "Notes for $guid",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = imageUrl,
        pubDate = pubDate,
        durationMs = 1_800_000L,
        positionMs = positionMs,
        played = played,
        lastPlayedAt = lastPlayedAt,
    )

    private suspend fun save(podcast: Podcast, vararg episodes: Episode) {
        store.saveFeed(ParsedFeed(podcast, episodes.toList()))
    }

    private fun List<EpisodeWithPodcast>.ids() = map { it.episode.id }

    // --- queueIds / setQueue ---

    @Test
    fun `the queue starts empty`() = runTest {
        assertEquals(emptyList<String>(), store.queueIds())
        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeQueue().first())
    }

    @Test
    fun `setQueue stores the ids in order`() = runTest {
        val one = episode(a, "1")
        val two = episode(a, "2")
        val three = episode(a, "3")
        save(podcast(a, "A"), one, two, three)

        store.setQueue(listOf(three.id, one.id, two.id))

        assertEquals(listOf(three.id, one.id, two.id), store.queueIds())
        assertEquals(listOf(three.id, one.id, two.id), store.observeQueue().first().ids())
    }

    @Test
    fun `setQueue replaces the whole queue`() = runTest {
        val one = episode(a, "1")
        val two = episode(a, "2")
        val three = episode(a, "3")
        save(podcast(a, "A"), one, two, three)

        store.setQueue(listOf(one.id, two.id, three.id))
        store.setQueue(listOf(two.id, one.id))

        assertEquals(listOf(two.id, one.id), store.queueIds())
        assertEquals(listOf(two.id, one.id), store.observeQueue().first().ids())
    }

    @Test
    fun `setQueue with an empty list clears the queue`() = runTest {
        val one = episode(a, "1")
        save(podcast(a, "A"), one)
        store.setQueue(listOf(one.id))

        store.setQueue(emptyList())

        assertEquals(emptyList<String>(), store.queueIds())
        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeQueue().first())
    }

    @Test
    fun `a longer queue keeps its order`() = runTest {
        val eps = (1..12).map { episode(a, "$it", pubDate = base - it * day) }
        save(podcast(a, "A"), *eps.toTypedArray())
        val order = eps.map { it.id }.reversed().let { it.drop(5) + it.take(5) }

        store.setQueue(order)

        assertEquals(order, store.queueIds())
        assertEquals(order, store.observeQueue().first().ids())
    }

    // --- observeQueue ---

    @Test
    fun `observeQueue carries each episode with its show's title and artwork`() = runTest {
        val one = episode(a, "1", imageUrl = "https://img.example/ep1.jpg")
        val three = episode(b, "3")
        save(podcast(a, "Show A", artworkUrl = "https://img.example/a.jpg"), one)
        save(podcast(b, "Show B", artworkUrl = null), three)

        store.setQueue(listOf(three.id, one.id))

        assertEquals(
            listOf(
                EpisodeWithPodcast(three, "Show B", null),
                EpisodeWithPodcast(one, "Show A", "https://img.example/a.jpg"),
            ),
            store.observeQueue().first(),
        )
    }

    @Test
    fun `observeQueue leaves out ids whose episode does not exist`() = runTest {
        val one = episode(a, "1")
        val two = episode(a, "2")
        save(podcast(a, "A"), one, two)

        store.setQueue(listOf("$a#missing", two.id, "https://nowhere.example/feed#x", one.id))

        assertEquals(listOf(two.id, one.id), store.observeQueue().first().ids())
    }

    @Test
    fun `observeQueue follows changes to the queue and to the episodes`() = runTest {
        val one = episode(a, "1")
        val two = episode(a, "2")
        save(podcast(a, "A"), one, two)

        store.setQueue(listOf(one.id))
        assertEquals(listOf(one.id), store.observeQueue().first().ids())

        store.setQueue(listOf(two.id, one.id))
        assertEquals(listOf(two.id, one.id), store.observeQueue().first().ids())

        store.savePosition(one.id, 60_000L, 0L, base + 1)
        assertEquals(60_000L, store.observeQueue().first().last().episode.positionMs)
    }

    // --- deletePodcast ---

    @Test
    fun `deletePodcast removes that show's episodes from the queue and keeps the rest in order`() = runTest {
        val a1 = episode(a, "1")
        val a2 = episode(a, "2")
        val b1 = episode(b, "1")
        val b2 = episode(b, "2")
        save(podcast(a, "A"), a1, a2)
        save(podcast(b, "B"), b1, b2)
        store.setQueue(listOf(b2.id, a1.id, b1.id, a2.id))

        store.deletePodcast(a)

        assertEquals(listOf(b2.id, b1.id), store.queueIds())
        assertEquals(listOf(b2.id, b1.id), store.observeQueue().first().ids())
    }

    @Test
    fun `resubscribing after deletePodcast does not bring the old queue entries back`() = runTest {
        val a1 = episode(a, "1")
        save(podcast(a, "A"), a1)
        store.setQueue(listOf(a1.id))

        store.deletePodcast(a)
        save(podcast(a, "A"), a1)

        assertEquals(emptyList<String>(), store.queueIds())
        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeQueue().first())
    }

    @Test
    fun `a feed refresh keeps the queue`() = runTest {
        val a1 = episode(a, "1")
        val a2 = episode(a, "2")
        save(podcast(a, "A"), a1, a2)
        store.setQueue(listOf(a2.id, a1.id))

        save(podcast(a, "A"), a1, a2, episode(a, "3", pubDate = base + day))

        assertEquals(listOf(a2.id, a1.id), store.queueIds())
    }

    // --- observeInProgress ---

    @Test
    fun `observeInProgress lists started unplayed episodes, most recently played first`() = runTest {
        val older = episode(a, "older", positionMs = 10_000L, lastPlayedAt = base + 1)
        val newer = episode(b, "newer", positionMs = 20_000L, lastPlayedAt = base + 5)
        val middle = episode(a, "middle", positionMs = 5_000L, lastPlayedAt = base + 3)
        val unstarted = episode(a, "unstarted", lastPlayedAt = base + 9)
        val played = episode(b, "played", played = true, lastPlayedAt = base + 10)
        save(podcast(a, "A"), older, middle, unstarted)
        save(podcast(b, "B"), newer, played)
        store.savePosition(older.id, 10_000L, 0L, base + 1)
        store.savePosition(middle.id, 5_000L, 0L, base + 3)
        store.savePosition(newer.id, 20_000L, 0L, base + 5)
        store.setPlayed(played.id, true)

        val items = store.observeInProgress(10).first()

        assertEquals(listOf(newer.id, middle.id, older.id), items.ids())
        assertEquals(listOf("B", "A", "A"), items.map { it.podcastTitle })
        assertEquals("https://img.example/B.jpg", items.first().podcastArtworkUrl)
        assertEquals(20_000L, items.first().episode.positionMs)
    }

    @Test
    fun `observeInProgress respects the limit`() = runTest {
        val eps = (1..6).map { episode(a, "$it") }
        save(podcast(a, "A"), *eps.toTypedArray())
        eps.forEachIndexed { i, ep -> store.savePosition(ep.id, 1_000L, 0L, base + i) }

        val items = store.observeInProgress(3).first()

        assertEquals(listOf(eps[5].id, eps[4].id, eps[3].id), items.ids())
    }

    @Test
    fun `an episode leaves observeInProgress when it is marked played`() = runTest {
        val one = episode(a, "1")
        save(podcast(a, "A"), one)
        store.savePosition(one.id, 1_000L, 0L, base + 1)
        assertEquals(listOf(one.id), store.observeInProgress(10).first().ids())

        store.setPlayed(one.id, true)

        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeInProgress(10).first())
    }

    // --- observeNewEpisodes ---

    @Test
    fun `observeNewEpisodes lists unplayed unstarted episodes across shows, newest first`() = runTest {
        val a1 = episode(a, "1", pubDate = base - 3 * day)
        val a2 = episode(a, "2", pubDate = base)
        val b1 = episode(b, "1", pubDate = base - day)
        val started = episode(b, "started", pubDate = base + day)
        val played = episode(a, "played", pubDate = base + 2 * day)
        save(podcast(a, "A"), a1, a2, played)
        save(podcast(b, "B", artworkUrl = null), b1, started)
        store.savePosition(started.id, 3_000L, 0L, base + 1)
        store.setPlayed(played.id, true)

        val items = store.observeNewEpisodes(50).first()

        assertEquals(listOf(a2.id, b1.id, a1.id), items.ids())
        assertEquals(EpisodeWithPodcast(b1, "B", null), items[1])
        assertEquals("https://img.example/A.jpg", items[0].podcastArtworkUrl)
    }

    @Test
    fun `observeNewEpisodes respects the limit`() = runTest {
        val eps = (1..8).map { episode(a, "$it", pubDate = base + it * day) }
        save(podcast(a, "A"), *eps.toTypedArray())

        val items = store.observeNewEpisodes(4).first()

        assertEquals(listOf(eps[7].id, eps[6].id, eps[5].id, eps[4].id), items.ids())
    }

    @Test
    fun `an episode marked unplayed again shows up as new`() = runTest {
        val one = episode(a, "1")
        save(podcast(a, "A"), one)
        store.setPlayed(one.id, true)
        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeNewEpisodes(50).first())

        store.setPlayed(one.id, false)

        assertEquals(listOf(one.id), store.observeNewEpisodes(50).first().ids())
    }

    @Test
    fun `the Home lists are empty after the show is deleted`() = runTest {
        val one = episode(a, "1")
        val two = episode(a, "2")
        save(podcast(a, "A"), one, two)
        store.savePosition(one.id, 1_000L, 0L, base + 1)

        store.deletePodcast(a)

        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeInProgress(10).first())
        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeNewEpisodes(50).first())
    }

    // --- markAllPlayed ---

    @Test
    fun `markAllPlayed marks every episode of that show played with no position`() = runTest {
        val a1 = episode(a, "1")
        val a2 = episode(a, "2")
        val a3 = episode(a, "3")
        save(podcast(a, "A"), a1, a2, a3)
        store.savePosition(a2.id, 50_000L, 0L, base + 1)

        store.markAllPlayed(a)

        for (ep in store.episodes(a)) {
            assertTrue(ep.id, ep.played)
            assertEquals(ep.id, 0L, ep.positionMs)
        }
        assertEquals(3, store.episodes(a).size)
    }

    @Test
    fun `markAllPlayed leaves other shows alone`() = runTest {
        val a1 = episode(a, "1")
        val b1 = episode(b, "1")
        val b2 = episode(b, "2")
        save(podcast(a, "A"), a1)
        save(podcast(b, "B"), b1, b2)
        store.savePosition(b1.id, 40_000L, 0L, base + 2)
        val before = store.episodes(b).toSet()

        store.markAllPlayed(a)

        assertEquals(before, store.episodes(b).toSet())
        assertFalse(store.episode(b1.id)!!.played)
        assertEquals(40_000L, store.episode(b1.id)!!.positionMs)
    }

    @Test
    fun `markAllPlayed takes the show out of both Home lists`() = runTest {
        val a1 = episode(a, "1")
        val a2 = episode(a, "2")
        val b1 = episode(b, "1")
        save(podcast(a, "A"), a1, a2)
        save(podcast(b, "B"), b1)
        store.savePosition(a1.id, 1_000L, 0L, base + 1)

        store.markAllPlayed(a)

        assertEquals(emptyList<EpisodeWithPodcast>(), store.observeInProgress(10).first())
        assertEquals(listOf(b1.id), store.observeNewEpisodes(50).first().ids())
    }

    @Test
    fun `markAllPlayed survives a feed refresh`() = runTest {
        val a1 = episode(a, "1")
        save(podcast(a, "A"), a1)

        store.markAllPlayed(a)
        save(podcast(a, "A"), a1)

        assertTrue(store.episode(a1.id)!!.played)
    }

    @Test
    fun `markAllPlayed for an unknown show changes nothing`() = runTest {
        val a1 = episode(a, "1")
        save(podcast(a, "A"), a1)

        store.markAllPlayed("https://unknown.example/feed")

        assertEquals(listOf(a1), store.episodes(a))
    }
}
