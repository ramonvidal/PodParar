package com.podparar.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Tells the root screen whether the user follows any show yet (to ask for notifications then). */
@HiltViewModel
class RootViewModel @Inject constructor(repository: PodcastRepository) : ViewModel() {
    val hasSubscriptions: StateFlow<Boolean> = repository.podcasts
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
