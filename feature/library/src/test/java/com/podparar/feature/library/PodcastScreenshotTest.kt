package com.podparar.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.Episode
import com.podparar.core.model.LoadError
import com.podparar.core.model.Podcast
import com.podparar.core.ui.theme.PodPararTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A show's page with episodes in every state, plus its error and not-found states; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class PodcastScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: PodcastUiState, dark: Boolean = false) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    PodcastContent(
                        state = state,
                        onBack = {},
                        onRefresh = {},
                        onUnsubscribe = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // Midday UTC, so "Today" / "Yesterday" hold in any nearby zone the tests run in.
    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L
    private val minute = 60 * 1000L

    private val feedUrl = "https://feeds.example.com/the-rest-is-history"

    private val podcast = Podcast(
        feedUrl = feedUrl,
        title = "The Rest Is History",
        author = "Goalhanger",
        description = "<p>Tom Holland and Dominic Sandbrook guide you through the past, " +
            "from the Romans to the Cold War.</p><p>New episodes every <b>Monday</b> &amp; <b>Thursday</b>. " +
            "Ad-free listening and bonus episodes for members.</p><p>Get in touch: " +
            "<a href=\"mailto:hello@example.com\">hello@example.com</a></p>",
        artworkUrl = null,
        lastRefreshed = now,
    )

    private fun episode(
        guid: String,
        title: String,
        description: String,
        pubDate: Long,
        durationMs: Long,
        positionMs: Long = 0L,
        played: Boolean = false,
    ) = Episode(
        id = "$feedUrl#$guid",
        podcastUrl = feedUrl,
        title = title,
        description = description,
        audioUrl = "https://audio.example.com/$guid.mp3",
        imageUrl = null,
        pubDate = pubDate,
        durationMs = durationMs,
        positionMs = positionMs,
        played = played,
    )

    // Newest first, as the store returns them.
    private val episodes = listOf(
        // NEW, today, over an hour.
        episode(
            guid = "ep-501",
            title = "501. The Fall of Constantinople",
            description = "<p>In 1453 the Ottoman sultan Mehmed II laid siege to the greatest city in Christendom.</p>" +
                "<p>Tom &amp; Dominic tell the story of its final days.</p>",
            pubDate = now - 2 * 60 * minute,
            durationMs = 65 * minute,
        ),
        // IN_PROGRESS, yesterday.
        episode(
            guid = "ep-500",
            title = "500. Napoleon in Russia: The Retreat From Moscow and the Destruction of the Grande Armée",
            description = "The winter of 1812.<br/>Half a million men marched east; few came back.",
            pubDate = now - day,
            durationMs = 52 * minute,
            positionMs = 20 * minute,
        ),
        // PLAYED, earlier this year.
        episode(
            guid = "ep-499",
            title = "499. The Great Fire of London",
            description = "<ul><li>Pudding Lane</li><li>Samuel Pepys buries his parmesan</li></ul>",
            pubDate = now - 10 * day,
            durationMs = 48 * minute,
            positionMs = 48 * minute,
            played = true,
        ),
        // Unknown duration.
        episode(
            guid = "ep-bonus",
            title = "Bonus: Your Questions Answered",
            description = "Listener questions on Rome, Vikings and the Cold War.",
            pubDate = now - 40 * day,
            durationMs = 0L,
        ),
        // Last year, NEW.
        episode(
            guid = "ep-450",
            title = "450. The Titanic",
            description = "<div>The unsinkable ship &#8212; and the night of 14 April 1912.</div>&#8220;Iceberg, right ahead!&#8221;",
            pubDate = now - 300 * day,
            durationMs = 61 * minute,
        ),
    )

    private val showState = PodcastUiState(
        podcast = podcast,
        episodes = episodes,
        isLoading = false,
        now = now,
    )

    @Test
    fun show() = shoot("podcast-show", showState)

    @Test
    fun showDark() = shoot("podcast-show-dark", showState, dark = true)

    @Test
    fun refreshError() = shoot("podcast-refresh-error", showState.copy(refreshError = LoadError.OFFLINE))

    @Test
    fun refreshErrorDark() = shoot("podcast-refresh-error-dark", showState.copy(refreshError = LoadError.OFFLINE), dark = true)

    @Test
    fun notFound() = shoot("podcast-not-found", PodcastUiState(podcast = null, isLoading = false, now = now))

    @Test
    fun notFoundDark() = shoot("podcast-not-found-dark", PodcastUiState(podcast = null, isLoading = false, now = now), dark = true)
}
