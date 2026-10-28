package com.podparar.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.podparar.core.model.EpisodeWithPodcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Upgrading a v1 database (Phases 0-2) to v2 (adds the Up next queue) keeps every show, episode and position. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PodPararDatabaseMigrationTest {
    private val name = "migration-test.db"
    private val feedUrl = "https://a.example/feed"
    private val episodeId = "$feedUrl#one"
    private val otherId = "$feedUrl#two"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PodPararDatabase::class.java,
    )

    private fun createV1() {
        helper.createDatabase(name, 1).apply {
            insert(
                "podcasts",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("feedUrl", feedUrl)
                    put("title", "Show A")
                    put("author", "Author A")
                    put("description", "About A")
                    put("artworkUrl", "https://img.example/a.jpg")
                    put("lastRefreshed", 1_000L)
                },
            )
            insert("episodes", SQLiteDatabase.CONFLICT_FAIL, episodeRow(episodeId, "Episode one", 2_000L, 754_000L, false, 5_000L))
            insert("episodes", SQLiteDatabase.CONFLICT_FAIL, episodeRow(otherId, "Episode two", 1_500L, 0L, true, 4_000L))
            close()
        }
    }

    private fun episodeRow(id: String, title: String, pubDate: Long, positionMs: Long, played: Boolean, lastPlayedAt: Long) =
        ContentValues().apply {
            put("id", id)
            put("podcastUrl", feedUrl)
            put("title", title)
            put("description", "Notes")
            put("audioUrl", "https://audio.example/$id.mp3")
            putNull("imageUrl")
            put("pubDate", pubDate)
            put("durationMs", 1_800_000L)
            put("positionMs", positionMs)
            put("played", if (played) 1 else 0)
            put("lastPlayedAt", lastPlayedAt)
        }

    @Test
    fun `migrating 1 to 2 validates against the v2 schema and keeps the rows`() {
        createV1()

        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        db.query("SELECT title, artworkUrl FROM podcasts WHERE feedUrl = ?", arrayOf(feedUrl)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Show A", c.getString(0))
            assertEquals("https://img.example/a.jpg", c.getString(1))
        }
        db.query("SELECT positionMs, played, lastPlayedAt FROM episodes WHERE id = ?", arrayOf(episodeId)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(754_000L, c.getLong(0))
            assertEquals(0, c.getInt(1))
            assertEquals(5_000L, c.getLong(2))
        }
        db.query("SELECT played FROM episodes WHERE id = ?", arrayOf(otherId)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM queue").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun `after the migration the queue table takes rows`() {
        createV1()

        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)
        db.insert("queue", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("episodeId", episodeId)
            put("position", 0)
        })

        db.query("SELECT episodeId, position FROM queue").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(episodeId, c.getString(0))
            assertEquals(0, c.getInt(1))
            assertFalse(c.moveToNext())
        }
        db.close()
    }

    @Test
    fun `the migrated database opens with Room and the store reads and queues the old episodes`() = runTest {
        createV1()
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

        val room = Room.databaseBuilder(RuntimeEnvironment.getApplication(), PodPararDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val store: PodcastStore = RoomPodcastStore(room)

            val episode = store.episode(episodeId)!!
            assertEquals("Episode one", episode.title)
            assertEquals(754_000L, episode.positionMs)
            assertFalse(episode.played)
            assertTrue(store.episode(otherId)!!.played)
            assertEquals("Show A", store.podcast(feedUrl)!!.title)

            store.setQueue(listOf(otherId, episodeId))
            assertEquals(listOf(otherId, episodeId), store.queueIds())
            assertEquals(
                listOf(
                    EpisodeWithPodcast(store.episode(otherId)!!, "Show A", "https://img.example/a.jpg"),
                    EpisodeWithPodcast(episode, "Show A", "https://img.example/a.jpg"),
                ),
                store.observeQueue().first(),
            )
            assertEquals(listOf(episodeId), store.observeInProgress(10).first().map { it.episode.id })
        } finally {
            room.close()
        }
    }
}
