package com.podparar.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.ParsedFeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Upgrading a v1 database (Phases 0-2) to v2 (adds the Up next queue) and a v2 database to v3 (adds downloads and
 * the per-show auto-download flag) keeps every show, episode, position and queue entry.
 */
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

    // --- 2 -> 3: downloads table and podcasts.autoDownload ---

    private fun createV2() {
        helper.createDatabase(name, 2).apply {
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
            insert("queue", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
                put("episodeId", otherId)
                put("position", 0)
            })
            insert("queue", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
                put("episodeId", episodeId)
                put("position", 1)
            })
            close()
        }
    }

    @Test
    fun `migrating 2 to 3 validates against the v3 schema and keeps the rows`() {
        createV2()

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

        db.query("SELECT title, artworkUrl, lastRefreshed, autoDownload FROM podcasts WHERE feedUrl = ?", arrayOf(feedUrl)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Show A", c.getString(0))
            assertEquals("https://img.example/a.jpg", c.getString(1))
            assertEquals(1_000L, c.getLong(2))
            assertEquals(0, c.getInt(3))
        }
        db.query("SELECT positionMs, played, lastPlayedAt FROM episodes WHERE id = ?", arrayOf(episodeId)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(754_000L, c.getLong(0))
            assertEquals(0, c.getInt(1))
            assertEquals(5_000L, c.getLong(2))
        }
        db.query("SELECT episodeId FROM queue ORDER BY position").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(otherId, c.getString(0))
            assertTrue(c.moveToNext())
            assertEquals(episodeId, c.getString(0))
            assertFalse(c.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun `after the 2 to 3 migration the downloads table takes rows`() {
        createV2()

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)
        db.insert("downloads", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("episodeId", episodeId)
            put("state", "DONE")
            put("filePath", "/files/episodes/one.audio")
            put("bytesDownloaded", 1_000L)
            put("totalBytes", 1_000L)
            put("updatedAt", 9_000L)
        })
        db.insert("downloads", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("episodeId", otherId)
            put("state", "QUEUED")
            putNull("filePath")
            put("bytesDownloaded", 0L)
            put("totalBytes", 0L)
            put("updatedAt", 9_001L)
        })

        db.query("SELECT state, filePath, bytesDownloaded, totalBytes, updatedAt FROM downloads WHERE episodeId = ?", arrayOf(episodeId)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DONE", c.getString(0))
            assertEquals("/files/episodes/one.audio", c.getString(1))
            assertEquals(1_000L, c.getLong(2))
            assertEquals(1_000L, c.getLong(3))
            assertEquals(9_000L, c.getLong(4))
        }
        db.query("SELECT filePath FROM downloads WHERE episodeId = ?", arrayOf(otherId)).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
        db.close()
    }

    @Test
    fun `the 2 to 3 migrated database opens with Room and the store uses downloads and auto-download`() = runTest {
        createV2()
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()

        val room = Room.databaseBuilder(RuntimeEnvironment.getApplication(), PodPararDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val store: PodcastStore = RoomPodcastStore(room)

            val show = store.podcast(feedUrl)!!
            assertEquals("Show A", show.title)
            assertFalse(show.autoDownload)
            assertEquals(754_000L, store.episode(episodeId)!!.positionMs)
            assertTrue(store.episode(otherId)!!.played)
            assertEquals(listOf(otherId, episodeId), store.queueIds())
            assertEquals(emptyMap<String, Download>(), store.observeDownloads().first())

            store.setAutoDownload(feedUrl, true)
            assertTrue(store.podcast(feedUrl)!!.autoDownload)

            val row = Download(episodeId, DownloadState.DONE, "/files/episodes/one.audio", 1_000L, 1_000L, 9_000L)
            store.upsertDownload(row)
            assertEquals(row, store.download(episodeId))
            assertEquals(mapOf(episodeId to row), store.observeDownloads().first())

            // A refresh after the upgrade: nothing old counts as new, and the old progress is kept.
            val fresh = store.saveFeed(ParsedFeed(show.copy(autoDownload = false), listOf(store.episode(episodeId)!!.copy(positionMs = 0L))))
            assertEquals(emptyList<Any>(), fresh)
            assertEquals(754_000L, store.episode(episodeId)!!.positionMs)
            assertTrue(store.podcast(feedUrl)!!.autoDownload)

            store.deletePodcast(feedUrl)
            assertNull(store.download(episodeId))
        } finally {
            room.close()
        }
    }

    @Test
    fun `a v1 database upgrades all the way to v3`() {
        createV1()

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT autoDownload FROM podcasts WHERE feedUrl = ?", arrayOf(feedUrl)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM episodes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }
}
