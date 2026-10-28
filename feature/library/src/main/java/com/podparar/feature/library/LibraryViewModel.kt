package com.podparar.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.PodcastRepository
import com.podparar.core.model.PodcastException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The subscribed shows, and pull-to-refresh for all of them. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    internal val uiState: StateFlow<LibraryUiState> =
        combine(repository.podcasts, refreshing) { podcasts, isRefreshing ->
            LibraryUiState(podcasts = podcasts, isLoading = false, isRefreshing = isRefreshing)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Refreshes every show. Shows that fail keep what's stored; the grid updates from the database. */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                // The RefreshResult (failures, new episodes) is for the background refresh's alert.
                repository.refreshAll()
            } catch (e: PodcastException) {
                // refreshAll counts failures itself; nothing to show beyond what's stored.
            } finally {
                refreshing.value = false
            }
        }
    }
}
