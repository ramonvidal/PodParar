package com.podparar.feature.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.data.PodcastRepository
import com.podparar.core.data.QueueRepository
import com.podparar.core.downloads.EpisodeDownloads
import com.podparar.core.model.Download
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.Podcast
import com.podparar.core.model.SleepTimer
import com.podparar.core.playback.PlayerController
import com.podparar.core.settings.DownloadSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HomeViewModel re-queries the New episodes list whenever the Home limit setting changes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(n: Int) = EpisodeWithPodcast(
        episode = Episode(
            id = "feed#ep-$n",
            podcastUrl = "feed",
            title = "Episode $n",
            description = "",
            audioUrl = "https://audio.example.com/$n.mp3",
            imageUrl = null,
            pubDate = 1_000L * n,
            durationMs = 60_000,
            positionMs = 0,
            played = false,
            lastPlayedAt = 0L,
        ),
        podcastTitle = "Show",
        podcastArtworkUrl = null,
    )

    private class FakeEpisodes(private val all: List<EpisodeWithPodcast>) : EpisodeRepository {
        val requestedLimits = mutableListOf<Int>()

        override fun episode(id: String): Flow<Episode?> = flowOf(null)
        override suspend fun getEpisode(id: String): Episode? = null
        override suspend fun getPodcast(feedUrl: String): Podcast? = null
        override suspend fun savePosition(id: String, positionMs: Long, durationMs: Long) {}
        override suspend fun setPlayed(id: String, played: Boolean) {}
        override val continueListening: Flow<List<EpisodeWithPodcast>> = flowOf(emptyList())
        override val newEpisodes: Flow<List<EpisodeWithPodcast>> = flowOf(all)
        override fun observeNewEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>> {
            requestedLimits += limit
            return flowOf(all.take(limit))
        }
        override suspend fun markAllPlayed(feedUrl: String) {}
    }

    private class FakeQueue : QueueRepository {
        override val queue: Flow<List<EpisodeWithPodcast>> = flowOf(emptyList())
        override suspend fun playNext(episodeId: String) {}
        override suspend fun add(episodeId: String) {}
        override suspend fun remove(episodeId: String) {}
        override suspend fun move(from: Int, to: Int) {}
        override suspend fun takeNext(): String? = null
    }

    private class FakePodcasts : PodcastRepository {
        override val podcasts: Flow<List<Podcast>> = flowOf(emptyList())
        override val subscribedFeedUrls: Flow<Set<String>> = flowOf(emptySet())
        override fun podcast(feedUrl: String): Flow<Podcast?> = flowOf(null)
        override fun episodes(feedUrl: String): Flow<List<Episode>> = flowOf(emptyList())
        override suspend fun search(term: String) = emptyList<com.podparar.core.model.SearchResult>()
        override suspend fun subscribe(link: String): Podcast = error("unused")
        override suspend fun refresh(feedUrl: String): List<Episode> = emptyList()
        override suspend fun refreshAll(): com.podparar.core.model.RefreshResult = error("unused")
        override suspend fun setAutoDownload(feedUrl: String, enabled: Boolean) {}
        override suspend fun unsubscribe(feedUrl: String) {}
        override suspend fun setSpeed(feedUrl: String, speed: Float?) {}
        override suspend fun exportOpml(): String = ""
        override suspend fun importOpml(opml: String, onProgress: (done: Int, total: Int) -> Unit) =
            error("unused")
    }

    private class FakePlayer : PlayerController {
        override val nowPlaying: StateFlow<NowPlaying?> = MutableStateFlow(null)
        override val sleepTimer: StateFlow<SleepTimer?> = MutableStateFlow(null)
        override val skipBackMs: StateFlow<Long> = MutableStateFlow(10_000L)
        override val skipForwardMs: StateFlow<Long> = MutableStateFlow(30_000L)
        override fun play(episodeId: String) {}
        override fun togglePlayPause() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun skipBack() {}
        override fun skipForward() {}
        override fun setSpeed(speed: Float) {}
        override fun seekToChapter(index: Int) {}
        override fun previousChapter() {}
        override fun nextChapter() {}
        override fun setSleepTimer(minutes: Int) {}
        override fun setSleepAtEndOfEpisode() {}
        override fun extendSleepTimer() {}
        override fun cancelSleepTimer() {}
    }

    private class FakeDownloads : EpisodeDownloads {
        override val downloads: Flow<Map<String, Download>> = flowOf(emptyMap())
        override fun download(episodeId: String) {}
        override fun remove(episodeId: String) {}
        override suspend fun removeAllFor(feedUrl: String, episodeIds: List<String>) {}
    }

    private class FakeSettings(initialHome: Int) : DownloadSettings {
        val home = MutableStateFlow(initialHome)
        override val wifiOnly: Flow<Boolean> = flowOf(true)
        override val newEpisodeAlerts: Flow<Boolean> = flowOf(true)
        override val maxHomeEpisodes: Flow<Int> = home
        override suspend fun setWifiOnly(enabled: Boolean) {}
        override suspend fun setNewEpisodeAlerts(enabled: Boolean) {}
    }

    @Test
    fun `changing the home limit re-queries new episodes with the new limit`() = runTest {
        val episodes = FakeEpisodes((1..100).map(::item))
        val settings = FakeSettings(initialHome = 30)
        val viewModel = HomeViewModel(
            episodes, FakeQueue(), FakePodcasts(), FakePlayer(), FakeDownloads(), settings,
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(listOf(30), episodes.requestedLimits)
        assertEquals(30, viewModel.uiState.value.newEpisodes.size)

        settings.home.value = 50

        assertEquals(listOf(30, 50), episodes.requestedLimits)
        assertEquals(50, viewModel.uiState.value.newEpisodes.size)
        job.cancel()
    }
}
