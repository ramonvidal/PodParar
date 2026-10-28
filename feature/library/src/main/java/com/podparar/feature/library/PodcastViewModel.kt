package com.podparar.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.data.PodcastRepository
import com.podparar.core.data.QueueRepository
import com.podparar.core.model.Episode
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/**
 * One show's page: the show and its episodes from the database, refresh and unsubscribe,
 * playing an episode, marking it (or the whole show) played or unplayed, and adding it to Up next.
 */
@HiltViewModel
class PodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
    private val episodes: EpisodeRepository,
    private val controller: PlayerController,
    private val queue: QueueRepository,
) : ViewModel() {

    /** Navigation has already decoded the path argument, so this is the feed URL as stored. */
    private val feedUrl: String = checkNotNull(savedStateHandle.get<String>(FEED_URL_ARG)) {
        "The show page needs a $FEED_URL_ARG argument"
    }

    private val refresh = MutableStateFlow(RefreshState())

    /** Set while unsubscribing, so the page doesn't flash "Show not found" before it closes. */
    private val leaving = MutableStateFlow(false)

    internal val uiState: StateFlow<PodcastUiState> =
        combine(
            repository.podcast(feedUrl),
            repository.episodes(feedUrl),
            refresh,
            // Only which episode is loaded and whether it plays matter here, not the
            // position ticks, so the page isn't rebuilt every half second.
            controller.nowPlaying
                .map { PlayingState(it?.episodeId, it?.isPlaying == true) }
                .distinctUntilChanged(),
        ) { podcast, episodeList, refreshState, playing ->
            PodcastUiState(
                podcast = podcast,
                episodes = episodeList,
                isLoading = false,
                isRefreshing = refreshState.isRefreshing,
                refreshError = refreshState.error,
                now = System.currentTimeMillis(),
                nowPlayingId = playing.episodeId,
                isPlaying = playing.isPlaying,
            )
        }
            .transform { if (!leaving.value) emit(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastUiState())

    fun refresh() {
        if (refresh.value.isRefreshing) return
        refresh.value = RefreshState(isRefreshing = true)
        viewModelScope.launch {
            val error = try {
                repository.refresh(feedUrl)
                null
            } catch (e: PodcastException) {
                e.error
            }
            refresh.value = RefreshState(isRefreshing = false, error = error)
        }
    }

    /** Plays [episode] from where it was left; if it's already loaded, toggles play/pause. */
    fun play(episode: Episode) {
        controller.play(episode.id)
    }

    /** Marks [episode] played if it wasn't, unplayed if it was. */
    fun togglePlayed(episode: Episode) {
        viewModelScope.launch {
            episodes.setPlayed(episode.id, !episode.played)
        }
    }

    /** Marks every episode of this show played. */
    fun markAllPlayed() {
        viewModelScope.launch {
            episodes.markAllPlayed(feedUrl)
        }
    }

    /** Puts [episode] at the front of Up next. */
    fun playNext(episode: Episode) {
        viewModelScope.launch {
            queue.playNext(episode.id)
        }
    }

    /** Adds [episode] to the end of Up next (no change if it's already there). */
    fun addToQueue(episode: Episode) {
        viewModelScope.launch {
            queue.add(episode.id)
        }
    }

    /** Removes the show and its episodes, then calls [onDone] (which leaves the page). */
    fun unsubscribe(onDone: () -> Unit) {
        if (leaving.value) return
        leaving.value = true
        viewModelScope.launch {
            repository.unsubscribe(feedUrl)
            onDone()
        }
    }

    private data class PlayingState(val episodeId: String?, val isPlaying: Boolean)

    private data class RefreshState(val isRefreshing: Boolean = false, val error: LoadError? = null)
}
