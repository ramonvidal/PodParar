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

/** RoomPodcastStore on an in-memory database: ordering, progress kept across refreshes, unsubscribe. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPodcastStoreTest {
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

    private fun podcast(feedUrl: String, title: String = "Show $feedUrl", artworkUrl: String? = "https://img.example/$title.jpg") =
        Podcast(
            feedUrl = feedUrl,
            title = title,
            author = "Author of $title",
            description = "<p>About $title</p>",
            artworkUrl = artworkUrl,
            lastRefreshed = base,
        )

    private fun episode(
        feedUrl: String,
        guid: String,
        pubDate: Long,
        title: String = "Episode $guid",
        durationMs: Long = 1_800_000L,
        positionMs: Long = 0L,
        played: Boolean = false,
        lastPlayedAt: Long = 0L,
        imageUrl: String? = null,
    ) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = title,
        description = "Notes for $guid",
        audioUrl = "https://audio.example/$guid.mp3",
        imageUrl = imageUrl,
        pubDate = pubDate,
        durationMs = durationMs,
        positionMs = positionMs,
        played = played,
        lastPlayedAt = lastPlayedAt,
    )

    // --- Podcasts ---

    @Test
    fun `podcasts sorted by title ignoring case`() = runTest {
        store.saveFeed(ParsedFeed(podcast("https://c.example/feed", "gamma"), emptyList()))
        store.saveFeed(ParsedFeed(podcast("https://a.example/feed", "Beta"), emptyList()))
        store.saveFeed(ParsedFeed(podcast("https://b.example/feed", "alpha"), emptyList()))
        store.saveFeed(ParsedFeed(podcast("https://d.example/feed", "Delta"), emptyList()))

        assertEquals(listOf("alpha", "Beta", "Delta", "gamma"), store.observePodcasts().first().map { it.title })
    }

    @Test
    fun `no podcasts at first`() = runTest {
        assertEquals(emptyList<Podcast>(), store.observePodcasts().first())
        assertEquals(emptyList<String>(), store.podcastUrls())
    }

    @Test
    fun `podcast round-trips every field`() = runTest {
        val show = podcast("https://a.example/feed", "Alpha")
        val noArt = podcast("https://b.example/feed", "Bravo", artworkUrl = null)
        store.saveFeed(ParsedFeed(show, emptyList()))
        store.saveFeed(ParsedFeed(noArt, emptyList()))

        assertEquals(show, store.observePodcast(show.feedUrl).first())
        assertEquals(noArt, store.observePodcast(noArt.feedUrl).first())
    }

    @Test
    fun `observePodcast emits null for an unknown feed`() = runTest {
        store.saveFeed(ParsedFeed(podcast("https://a.example/feed"), emptyList()))
        assertNull(store.observePodcast("https://unknown.example/feed").first())
    }

    @Test
    fun `podcastUrls lists every subscription once`() = runTest {
        store.saveFeed(ParsedFeed(podcast("https://a.example/feed", "A"), emptyList()))
        store.saveFeed(ParsedFeed(podcast("https://b.example/feed", "B"), emptyList()))
        store.saveFeed(ParsedFeed(podcast("https://a.example/feed", "A renamed"), emptyList()))

        assertEquals(setOf("https://a.example/feed", "https://b.example/feed"), store.podcastUrls().toSet())
        assertEquals(2, store.podcastUrls().size)
    }

    @Test
    fun `saving a feed again updates the podcast instead of adding one`() = runTest {
        val url = "https://a.example/feed"
        store.saveFeed(ParsedFeed(podcast(url, "Old title"), emptyList()))
        val renamed = podcast(url, "New title").copy(lastRefreshed = base + day)
        store.saveFeed(ParsedFeed(renamed, emptyList()))

        assertEquals(listOf(renamed), store.observePodcasts().first())
        assertEquals(renamed, store.observePodcast(url).first())
    }

    // --- Episodes ---

    @Test
    fun `episodes newest first, only that show's`() = runTest {
        val a = "https://a.example/feed"
        val b = "https://b.example/feed"
        store.saveFeed(
            ParsedFeed(
                podcast(a),
                listOf(
                    episode(a, "middle", base),
                    episode(a, "oldest", base - day),
                    episode(a, "newest", base + day),
                ),
            ),
        )
        store.saveFeed(ParsedFeed(podcast(b), listOf(episode(b, "other", base + 5 * day))))

        assertEquals(
            listOf("$a#newest", "$a#middle", "$a#oldest"),
            store.observeEpisodes(a).first().map { it.id },
        )
        assertEquals(setOf("$a#newest", "$a#middle", "$a#oldest"), store.episodes(a).map { it.id }.toSet())
        assertEquals(listOf("$b#other"), store.observeEpisodes(b).first().map { it.id })
    }

    @Test
    fun `episode round-trips every field`() = runTest {
        val a = "https://a.example/feed"
        val withArt = episode(a, "one", base, imageUrl = "https://img.example/one.jpg", positionMs = 61_000L, lastPlayedAt = base + 1)
        val played = episode(a, "two", base - day, played = true, lastPlayedAt = base + 2)
        store.saveFeed(ParsedFeed(podcast(a), listOf(withArt, played)))

        assertEquals(listOf(withArt, played), store.observeEpisodes(a).first())
    }

    @Test
    fun `no episodes for an unknown feed`() = runTest {
        assertEquals(emptyList<Episode>(), store.observeEpisodes("https://unknown.example/feed").first())
        assertEquals(emptyList<Episode>(), store.episodes("https://unknown.example/feed"))
    }

    @Test
    fun `saving a feed again keeps progress and takes the fresh details`() = runTest {
        val a = "https://a.example/feed"
        store.saveFeed(
            ParsedFeed(
                podcast(a, "Show"),
                listOf(
                    episode(a, "started", base, title = "Started (old title)", positionMs = 600_000L, lastPlayedAt = base + 10),
                    episode(a, "finished", base - day, title = "Finished (old title)", played = true, positionMs = 0L, lastPlayedAt = base + 20),
                ),
            ),
        )

        // A refresh: the feed knows nothing about progress.
        store.saveFeed(
            ParsedFeed(
                podcast(a, "Show (renamed)"),
                listOf(
                    episode(a, "started", base, title = "Started (new title)"),
                    episode(a, "finished", base - day, title = "Finished (new title)"),
                ),
            ),
        )

        val byId = store.observeEpisodes(a).first().associateBy { it.id }
        val started = byId.getValue("$a#started")
        assertEquals("Started (new title)", started.title)
        assertEquals(600_000L, started.positionMs)
        assertFalse(started.played)
        assertEquals(base + 10, started.lastPlayedAt)

        val finished = byId.getValue("$a#finished")
        assertEquals("Finished (new title)", finished.title)
        assertTrue(finished.played)
        assertEquals(base + 20, finished.lastPlayedAt)

        assertEquals("Show (renamed)", store.observePodcast(a).first()!!.title)
        assertEquals(2, byId.size)
    }

    @Test
    fun `refresh with unknown duration keeps the stored one`() = runTest {
        val a = "https://a.example/feed"
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "one", base, durationMs = 2_400_000L))))
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "one", base, durationMs = 0L))))

        assertEquals(2_400_000L, store.episodes(a).single().durationMs)
    }

    @Test
    fun `episodes no longer in the feed are kept, new ones are added`() = runTest {
        val a = "https://a.example/feed"
        store.saveFeed(
            ParsedFeed(
                podcast(a),
                listOf(
                    episode(a, "two", base),
                    episode(a, "one", base - day, positionMs = 90_000L),
                ),
            ),
        )
        // The feed now only lists its latest two episodes; "one" dropped off the end.
        store.saveFeed(
            ParsedFeed(
                podcast(a),
                listOf(
                    episode(a, "three", base + day),
                    episode(a, "two", base),
                ),
            ),
        )

        val episodes = store.observeEpisodes(a).first()
        assertEquals(listOf("$a#three", "$a#two", "$a#one"), episodes.map { it.id })
        assertEquals(90_000L, episodes.last().positionMs)
    }

    @Test
    fun `observed episodes follow a save`() = runTest {
        val a = "https://a.example/feed"
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "one", base))))
        assertEquals(1, store.observeEpisodes(a).first().size)

        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "two", base + day))))
        assertEquals(listOf("$a#two", "$a#one"), store.observeEpisodes(a).first().map { it.id })
    }

    // --- Unsubscribe ---

    @Test
    fun `deletePodcast removes the show and its episodes, not other shows`() = runTest {
        val a = "https://a.example/feed"
        val b = "https://b.example/feed"
        store.saveFeed(ParsedFeed(podcast(a, "A"), listOf(episode(a, "1", base), episode(a, "2", base - day))))
        store.saveFeed(ParsedFeed(podcast(b, "B"), listOf(episode(b, "1", base))))

        store.deletePodcast(a)

        assertNull(store.observePodcast(a).first())
        assertEquals(emptyList<Episode>(), store.observeEpisodes(a).first())
        assertEquals(emptyList<Episode>(), store.episodes(a))
        assertEquals(listOf("B"), store.observePodcasts().first().map { it.title })
        assertEquals(listOf(b), store.podcastUrls())
        assertEquals(listOf("$b#1"), store.observeEpisodes(b).first().map { it.id })
    }

    @Test
    fun `deleting an unknown podcast changes nothing`() = runTest {
        val a = "https://a.example/feed"
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1", base))))

        store.deletePodcast("https://unknown.example/feed")

        assertEquals(listOf(a), store.podcastUrls())
        assertEquals(1, store.episodes(a).size)
    }

    @Test
    fun `subscribing again after unsubscribing starts fresh`() = runTest {
        val a = "https://a.example/feed"
        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1", base, positionMs = 30_000L, played = true))))
        store.deletePodcast(a)

        store.saveFeed(ParsedFeed(podcast(a), listOf(episode(a, "1", base))))

        val ep = store.episodes(a).single()
        assertEquals(0L, ep.positionMs)
        assertFalse(ep.played)
    }
}
