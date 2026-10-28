package com.podparar.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.PodcastRepository
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/** One show's page: the show and its episodes from the database, refresh and unsubscribe. */
@HiltViewModel
class PodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
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
        ) { podcast, episodes, refreshState ->
            PodcastUiState(
                podcast = podcast,
                episodes = episodes,
                isLoading = false,
                isRefreshing = refreshState.isRefreshing,
                refreshError = refreshState.error,
                now = System.currentTimeMillis(),
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

    /** Removes the show and its episodes, then calls [onDone] (which leaves the page). */
    fun unsubscribe(onDone: () -> Unit) {
        if (leaving.value) return
        leaving.value = true
        viewModelScope.launch {
            repository.unsubscribe(feedUrl)
            onDone()
        }
    }

    private data class RefreshState(val isRefreshing: Boolean = false, val error: LoadError? = null)
}
