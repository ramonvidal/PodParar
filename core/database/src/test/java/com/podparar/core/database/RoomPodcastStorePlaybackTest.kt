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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** RoomPodcastStore's Phase 2 calls: single-episode and podcast reads, saving positions, played state. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStorePlaybackTest {
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

    private fun podcast(feedUrl: String, title: String = "Show $feedUrl") = Podcast(
        feedUrl = feedUrl,
        title = title,
        author = "Author of $title",
        description = "About $title",
        artworkUrl = "https://img.example/$title.jpg",
        lastRefreshed = base,
    )

    private fun episode(
        feedUrl: String,
        guid: String,
        pubDate: Long = base,
        title: String = "Episode $guid",
        durationMs: Long = 1_800_000L,
        positionMs: Long = 0L,
        played: Boolean = false,
        lastPlayedAt: Long = 0L,
    ) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = title,
        description = "Notes for $guid",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = durationMs,
        positionMs = positionMs,
        played = played,
        lastPlayedAt = lastPlayedAt,
    )

    /** Saves show A with episodes one and two, and show B with episode three. */
    private suspend fun seed(): Triple<Episode, Episode, Episode> {
        val one = episode(a, "one", base)
        val two = episode(a, "two", base - day, positionMs = 120_000L, lastPlayedAt = base + 1)
        val three = episode(b, "three", base)
        store.saveFeed(ParsedFeed(podcast(a), listOf(one, two)))
        store.saveFeed(ParsedFeed(podcast(b), listOf(three)))
        return Triple(one, two, three)
    }

    // --- Reads ---

    @Test
    fun `episode returns the stored episode`() = runTest {
        val (one, two, three) = seed()

        assertEquals(one, store.episode(one.id))
        assertEquals(two, store.episode(two.id))
        assertEquals(three, store.episode(three.id))
    }

    @Test
    fun `episode is null for an unknown id`() = runTest {
        seed()
        assertNull(store.episode("$a#missing"))
        assertNull(store.episode(""))
    }

    @Test
    fun `observeEpisode emits the stored episode, or null when unknown`() = runTest {
        val (one, _, _) = seed()

        assertEquals(one, store.observeEpisode(one.id).first())
        assertNull(store.observeEpisode("$a#missing").first())
    }

    @Test
    fun `observeEpisode follows savePosition and setPlayed`() = runTest {
        val (one, _, _) = seed()

        store.savePosition(one.id, 300_000L, 0L, base + 5)
        assertEquals(300_000L, store.observeEpisode(one.id).first()?.positionMs)

        store.setPlayed(one.id, true)
        val played = store.observeEpisode(one.id).first()
        assertEquals(true, played?.played)
        assertEquals(0L, played?.positionMs)
    }

    @Test
    fun `observeEpisode emits null after the show is deleted`() = runTest {
        val (one, _, _) = seed()
        store.deletePodcast(a)
        assertNull(store.observeEpisode(one.id).first())
    }

    @Test
    fun `podcast returns the stored show, or null when unknown`() = runTest {
        seed()

        assertEquals(podcast(a), store.podcast(a))
        assertEquals(podcast(b), store.podcast(b))
        assertNull(store.podcast("https://unknown.example/feed"))
    }

    // --- savePosition ---

    @Test
    fun `savePosition stores position, last played time and duration`() = runTest {
        val (one, _, _) = seed()

        store.savePosition(one.id, 754_000L, 1_900_000L, base + day)

        val saved = store.episode(one.id)!!
        assertEquals(754_000L, saved.positionMs)
        assertEquals(base + day, saved.lastPlayedAt)
        assertEquals(1_900_000L, saved.durationMs)
        assertFalse(saved.played)
        assertEquals(one.copy(positionMs = 754_000L, lastPlayedAt = base + day, durationMs = 1_900_000L), saved)
    }

    @Test
    fun `savePosition keeps the stored duration when 0 is passed`() = runTest {
        val (one, _, _) = seed()

        store.savePosition(one.id, 60_000L, 0L, base + 1)

        val saved = store.episode(one.id)!!
        assertEquals(1_800_000L, saved.durationMs)
        assertEquals(60_000L, saved.positionMs)
        assertEquals(base + 1, saved.lastPlayedAt)
    }

    @Test
    fun `savePosition keeps the stored duration when a negative one is passed`() = runTest {
        val (one, _, _) = seed()

        store.savePosition(one.id, 60_000L, -1L, base + 1)

        assertEquals(1_800_000L, store.episode(one.id)!!.durationMs)
    }

    @Test
    fun `savePosition fills in an unknown duration`() = runTest {
        val noDuration = episode(a, "nodur", durationMs = 0L)
        store.saveFeed(ParsedFeed(podcast(a), listOf(noDuration)))

        store.savePosition(noDuration.id, 10_000L, 2_400_000L, base + 2)

        assertEquals(2_400_000L, store.episode(noDuration.id)!!.durationMs)
    }

    @Test
    fun `savePosition changes only that episode`() = runTest {
        val (one, two, three) = seed()

        store.savePosition(one.id, 99_000L, 0L, base + 3)

        assertEquals(two, store.episode(two.id))
        assertEquals(three, store.episode(three.id))
    }

    @Test
    fun `savePosition overwrites an earlier position`() = runTest {
        val (_, two, _) = seed()

        store.savePosition(two.id, 30_000L, 0L, base + 10)

        assertEquals(30_000L, store.episode(two.id)!!.positionMs)
        assertEquals(base + 10, store.episode(two.id)!!.lastPlayedAt)
    }

    @Test
    fun `savePosition for an unknown id changes nothing`() = runTest {
        val (one, two, three) = seed()

        store.savePosition("$a#missing", 10_000L, 60_000L, base + 4)

        assertNull(store.episode("$a#missing"))
        assertEquals(setOf(one, two), store.episodes(a).toSet())
        assertEquals(setOf(three), store.episodes(b).toSet())
    }

    // --- setPlayed ---

    @Test
    fun `setPlayed true marks played and resets the position`() = runTest {
        val (_, two, _) = seed()

        store.setPlayed(two.id, true)

        val saved = store.episode(two.id)!!
        assertTrue(saved.played)
        assertEquals(0L, saved.positionMs)
        assertEquals(two.title, saved.title)
        assertEquals(two.durationMs, saved.durationMs)
    }

    @Test
    fun `setPlayed false marks unplayed and resets the position`() = runTest {
        val finished = episode(a, "finished", played = true, positionMs = 500_000L)
        store.saveFeed(ParsedFeed(podcast(a), listOf(finished)))

        store.setPlayed(finished.id, false)

        val saved = store.episode(finished.id)!!
        assertFalse(saved.played)
        assertEquals(0L, saved.positionMs)
    }

    @Test
    fun `setPlayed changes only that episode`() = runTest {
        val (one, two, three) = seed()

        store.setPlayed(one.id, true)

        assertEquals(two, store.episode(two.id))
        assertEquals(three, store.episode(three.id))
    }

    @Test
    fun `setPlayed for an unknown id changes nothing`() = runTest {
        val (one, two, three) = seed()

        store.setPlayed("$a#missing", true)
        store.setPlayed("$b#missing", false)

        assertNull(store.episode("$a#missing"))
        assertEquals(setOf(one, two), store.episodes(a).toSet())
        assertEquals(setOf(three), store.episodes(b).toSet())
    }

    // --- Refresh keeps what was played ---

    @Test
    fun `saveFeed after setPlayed keeps the episode played`() = runTest {
        val (one, _, _) = seed()
        store.setPlayed(one.id, true)

        // A refresh brings the episode back unplayed, with fresh details.
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "one", base, title = "Fresh title"))))

        val saved = store.episode(one.id)!!
        assertTrue(saved.played)
        assertEquals(0L, saved.positionMs)
        assertEquals("Fresh title", saved.title)
    }

    @Test
    fun `saveFeed after savePosition keeps the position and last played time`() = runTest {
        val (one, _, _) = seed()
        store.savePosition(one.id, 400_000L, 0L, base + 9)

        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "one", base))))

        val saved = store.episode(one.id)!!
        assertEquals(400_000L, saved.positionMs)
        assertEquals(base + 9, saved.lastPlayedAt)
        assertFalse(saved.played)
    }

    @Test
    fun `saveFeed after setPlayed false keeps it unplayed`() = runTest {
        val (one, _, _) = seed()
        store.setPlayed(one.id, true)
        store.setPlayed(one.id, false)

        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "one", base))))

        assertFalse(store.episode(one.id)!!.played)
    }
}
