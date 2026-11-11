package com.podparar.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Long-pressing an episode on Home opens its menu in every section (Continue listening, Up next, New
 * episodes); each menu item calls the matching HomeContent callback with that episode, and a plain tap
 * still plays it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class HomeMenusTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private val minute = 60 * 1000L

    private val history = "https://feeds.example.com/the-rest-is-history"
    private val science = "https://feeds.example.com/science-weekly"
    private val football = "https://feeds.example.com/football-daily"

    private val continueTitle = "Continue: The Retreat From Moscow"
    private val upNextTitle = "Queued: Title race special"
    private val newTitle = "Fresh: Octopuses among us"

    private fun item(
        feedUrl: String,
        guid: String,
        title: String,
        podcastTitle: String,
        pubDate: Long,
        positionMs: Long = 0L,
        lastPlayedAt: Long = 0L,
    ) = EpisodeWithPodcast(
        episode = Episode(
            id = "$feedUrl#$guid",
            podcastUrl = feedUrl,
            title = title,
            description = "",
            audioUrl = "https://audio.example.com/$guid.mp3",
            imageUrl = null,
            pubDate = pubDate,
            durationMs = 50 * minute,
            positionMs = positionMs,
            played = false,
            lastPlayedAt = lastPlayedAt,
        ),
        podcastTitle = podcastTitle,
        podcastArtworkUrl = null,
    )

    private val continueItem = item(
        history, "ep-500", continueTitle, "The Rest Is History",
        pubDate = now - 3 * day, positionMs = 20 * minute, lastPlayedAt = now - 60 * minute,
    )
    private val upNextItem = item(football, "fd-880", upNextTitle, "Football Daily", pubDate = now - day)
    private val newItem = item(science, "sw-211", newTitle, "Science Weekly", pubDate = now - 2 * 60 * minute)

    private val state = HomeUiState(
        continueListening = listOf(continueItem),
        upNext = listOf(upNextItem),
        newEpisodes = listOf(newItem),
        hasSubscriptions = true,
        isLoading = false,
        now = now,
    )

    private val played = mutableListOf<Episode>()
    private val openedPodcasts = mutableListOf<String>()
    private val playedNext = mutableListOf<Episode>()
    private val addedToQueue = mutableListOf<Episode>()
    private val removedFromQueue = mutableListOf<Episode>()
    private val toggledPlayed = mutableListOf<Episode>()

    private fun show() {
        compose.setContent {
            PodPararTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    HomeContent(
                        state = state,
                        onPlay = { played += it.asEpisode() },
                        onOpenPodcast = { openedPodcasts += it },
                        onPlayNext = { playedNext += it.asEpisode() },
                        onAddToQueue = { addedToQueue += it.asEpisode() },
                        onRemoveFromQueue = { removedFromQueue += it.asEpisode() },
                        onMoveInQueue = { _, _ -> },
                        onTogglePlayed = { toggledPlayed += it.asEpisode() },
                        onFindShows = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    /** Callbacks may hand over an [Episode] or an [EpisodeWithPodcast]; tests compare episodes. */
    private fun Any?.asEpisode(): Episode = when (this) {
        is Episode -> this
        is EpisodeWithPodcast -> episode
        else -> error("Unexpected callback argument: $this")
    }

    private fun longPress(title: String) {
        compose.onNodeWithText(title).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    private fun assertShown(vararg labels: String) {
        labels.forEach { compose.onNodeWithText(it).assertExists("Menu item \"$it\" should be shown") }
    }

    private fun assertNotShown(vararg labels: String) {
        labels.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    private fun chooseAndExpectClosed(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
        assertNotShown(label, "Go to show")
    }

    // Menu contents

    @Test
    fun continueListeningMenuHasItsItems() {
        show()
        longPress(continueTitle)
        assertShown("Play next", "Add to queue", "Mark as played", "Download", "Go to show")
        assertNotShown("Remove from Up next", "Mark as unplayed")
    }

    @Test
    fun upNextMenuHasItsItems() {
        show()
        longPress(upNextTitle)
        assertShown("Play next", "Remove from Up next", "Mark as played", "Download", "Go to show")
        assertNotShown("Add to queue")
    }

    @Test
    fun newEpisodesMenuHasItsItems() {
        show()
        longPress(newTitle)
        assertShown("Play next", "Add to queue", "Mark as played", "Download", "Go to show")
        assertNotShown("Remove from Up next")
    }

    // Go to show, in every section

    @Test
    fun goToShowFromContinueListening() {
        show()
        longPress(continueTitle)
        chooseAndExpectClosed("Go to show")
        assertEquals(listOf(history), openedPodcasts)
        assertEquals(emptyList<Episode>(), played)
    }

    @Test
    fun goToShowFromUpNext() {
        show()
        longPress(upNextTitle)
        chooseAndExpectClosed("Go to show")
        assertEquals(listOf(football), openedPodcasts)
        assertEquals(emptyList<Episode>(), played)
    }

    @Test
    fun goToShowFromNewEpisodes() {
        show()
        longPress(newTitle)
        chooseAndExpectClosed("Go to show")
        assertEquals(listOf(science), openedPodcasts)
        assertEquals(emptyList<Episode>(), played)
    }

    // Other actions

    @Test
    fun removeFromUpNextRemovesThatEpisode() {
        show()
        longPress(upNextTitle)
        chooseAndExpectClosed("Remove from Up next")
        assertEquals(listOf(upNextItem.episode), removedFromQueue)
        assertEquals(emptyList<Episode>(), played)
    }

    @Test
    fun playNextFromContinueListening() {
        show()
        longPress(continueTitle)
        chooseAndExpectClosed("Play next")
        assertEquals(listOf(continueItem.episode), playedNext)
        assertEquals(emptyList<Episode>(), played)
    }

    @Test
    fun addToQueueFromNewEpisodes() {
        show()
        longPress(newTitle)
        chooseAndExpectClosed("Add to queue")
        assertEquals(listOf(newItem.episode), addedToQueue)
    }

    @Test
    fun markAsPlayedFromUpNext() {
        show()
        longPress(upNextTitle)
        chooseAndExpectClosed("Mark as played")
        assertEquals(listOf(upNextItem.episode), toggledPlayed)
    }

    // A plain tap still plays and opens no menu

    @Test
    fun tapPlaysAndOpensNoMenu() {
        show()
        listOf(continueItem, upNextItem, newItem).forEach { item ->
            compose.onNodeWithText(item.episode.title).performClick()
            compose.waitForIdle()
            assertNotShown("Play next", "Go to show")
        }
        assertEquals(listOf(continueItem.episode, upNextItem.episode, newItem.episode), played)
        assertEquals(emptyList<String>(), openedPodcasts)
    }
}
