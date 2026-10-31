package com.podparar.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.data.PodcastRepository
import com.podparar.core.data.QueueRepository
import com.podparar.core.downloads.EpisodeDownloads
import com.podparar.core.model.Episode
import com.podparar.core.model.HomeLists
import com.podparar.core.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home: what's under way, the queue and the newest episodes, all from the database, plus
 * playing an episode, queue changes, marking played or unplayed and downloads.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val episodes: EpisodeRepository,
    private val queue: QueueRepository,
    repository: PodcastRepository,
    private val player: PlayerController,
    private val downloads: EpisodeDownloads,
) : ViewModel() {

    internal val uiState: StateFlow<HomeUiState> =
        combine(
            episodes.continueListening,
            queue.queue,
            episodes.newEpisodes,
            repository.podcasts.map { it.isNotEmpty() }.distinctUntilChanged(),
            // Only which episode is loaded and whether it plays matter here, not the
            // position ticks, so Home isn't rebuilt every half second.
            player.nowPlaying
                .map { PlayingState(it?.episodeId, it?.isPlaying == true) }
                .distinctUntilChanged(),
        ) { continueListening, upNext, newEpisodes, hasSubscriptions, playing ->
            // An episode in Up next shows only there; it returns to its section when it leaves.
            HomeUiState(
                continueListening = HomeLists.withoutQueued(continueListening, upNext),
                upNext = upNext,
                newEpisodes = HomeLists.withoutQueued(newEpisodes, upNext),
                hasSubscriptions = hasSubscriptions,
                isLoading = false,
                nowPlayingId = playing.episodeId,
                isPlaying = playing.isPlaying,
                now = System.currentTimeMillis(),
            )
        }
            // combine takes at most five typed flows, so the downloads join in a second step.
            .combine(downloads.downloads) { state, downloadMap -> state.copy(downloads = downloadMap) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Plays [episode] from where it was left; if it's already loaded, toggles play/pause. */
    fun play(episode: Episode) {
        player.play(episode.id)
    }

    fun playNext(episode: Episode) {
        viewModelScope.launch { queue.playNext(episode.id) }
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch { queue.add(episode.id) }
    }

    fun removeFromQueue(episode: Episode) {
        viewModelScope.launch { queue.remove(episode.id) }
    }

    /** Moves the Up next item at [from] to [to] (indices within Up next). */
    fun moveInQueue(from: Int, to: Int) {
        viewModelScope.launch { queue.move(from, to) }
    }

    /** Marks [episode] played if it wasn't, unplayed if it was. */
    fun togglePlayed(episode: Episode) {
        viewModelScope.launch { episodes.setPlayed(episode.id, !episode.played) }
    }

    /** Downloads [episode] (on Wi-Fi only if that's the setting); progress shows from the database. */
    fun download(episode: Episode) {
        downloads.download(episode.id)
    }

    /** Cancels or deletes [episode]'s download. */
    fun removeDownload(episode: Episode) {
        downloads.remove(episode.id)
    }

    private data class PlayingState(val episodeId: String?, val isPlaying: Boolean)
}
