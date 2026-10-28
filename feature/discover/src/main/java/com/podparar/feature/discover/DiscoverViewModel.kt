package com.podparar.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.PodcastRepository
import com.podparar.core.model.FeedUrls
import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import com.podparar.core.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Searches the Apple Podcasts directory and subscribes. Search runs on its own once someone stops typing
 * for [SEARCH_DELAY_MS] with at least [MIN_QUERY_LENGTH] characters, and straight away on the keyboard's
 * Search key. Whether a row is subscribed comes from the database, so it stays right after subscribing.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    private data class SearchState(
        val query: String = "",
        val results: List<SearchResult> = emptyList(),
        val isSearching: Boolean = false,
        val hasSearched: Boolean = false,
        val error: LoadError? = null,
    )

    private val search = MutableStateFlow(SearchState())
    private val subscribing = MutableStateFlow<Set<String>>(emptySet())
    private var searchJob: Job? = null

    internal val uiState: StateFlow<DiscoverUiState> =
        combine(search, repository.subscribedFeedUrls, subscribing) { s, subscribed, adding ->
            DiscoverUiState(
                query = s.query,
                results = s.results.map { result -> item(result, subscribed, adding) },
                isSearching = s.isSearching,
                hasSearched = s.hasSearched,
                error = s.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    private val _addByLink = MutableStateFlow<AddByLinkState?>(null)

    /** The "Add by RSS link" dialog; null while it's closed. */
    internal val addByLink: StateFlow<AddByLinkState?> = _addByLink.asStateFlow()

    private val _openPodcast = Channel<String>(Channel.BUFFERED)

    /** Feed URLs of shows to open, once each (after adding one by its link). */
    internal val openPodcast: Flow<String> = _openPodcast.receiveAsFlow()

    private val _subscribeError = MutableStateFlow<LoadError?>(null)

    /** Why subscribing from a search result failed, until shown. */
    internal val subscribeError: StateFlow<LoadError?> = _subscribeError.asStateFlow()

    fun onQueryChange(query: String) {
        search.update { it.copy(query = query) }
        searchJob?.cancel()
        val term = query.trim()
        when {
            term.isEmpty() -> {
                searchJob = null
                search.value = SearchState(query = query)
            }
            term.length >= MIN_QUERY_LENGTH -> searchJob = viewModelScope.launch {
                delay(SEARCH_DELAY_MS)
                runSearch(term)
            }
            // One character: wait for more, keep what's on screen.
            else -> searchJob = null
        }
    }

    /** The keyboard's Search key, and Retry after an error. */
    fun onSearch() {
        searchJob?.cancel()
        val term = search.value.query.trim()
        if (term.isEmpty()) return
        searchJob = viewModelScope.launch { runSearch(term) }
    }

    fun onSubscribe(result: SearchResult) {
        val feedUrl = result.feedUrl
        if (feedUrl in subscribing.value) return
        subscribing.update { it + feedUrl }
        viewModelScope.launch {
            try {
                repository.subscribe(feedUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _subscribeError.value = e.toLoadError()
            } finally {
                subscribing.update { it - feedUrl }
            }
        }
    }

    fun onSubscribeErrorShown() {
        _subscribeError.value = null
    }

    fun onOpenAddByLink() {
        _addByLink.value = AddByLinkState()
    }

    fun onLinkChange(link: String) {
        _addByLink.update { it?.copy(link = link, error = null) }
    }

    fun onDismissAddByLink() {
        // Leaving while a show is being added is fine: it is still saved, the dialog just goes away.
        _addByLink.value = null
    }

    fun onAddLink() {
        val dialog = _addByLink.value ?: return
        if (dialog.isAdding || dialog.link.isBlank()) return
        _addByLink.value = dialog.copy(isAdding = true, error = null)
        viewModelScope.launch {
            try {
                val podcast = repository.subscribe(dialog.link)
                if (_addByLink.value != null) {
                    _addByLink.value = null
                    _openPodcast.send(podcast.feedUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addByLink.update { it?.copy(isAdding = false, error = e.toLoadError()) }
            }
        }
    }

    private suspend fun runSearch(term: String) {
        search.update { it.copy(isSearching = true, error = null) }
        try {
            val results = repository.search(term)
            search.update { it.copy(results = results, isSearching = false, hasSearched = true, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            search.update {
                it.copy(results = emptyList(), isSearching = false, hasSearched = true, error = e.toLoadError())
            }
        }
    }

    private fun item(result: SearchResult, subscribed: Set<String>, adding: Set<String>): SearchResultItem {
        // The stored URL is the normalized one; open that so the show page finds it.
        val stored = when {
            result.feedUrl in subscribed -> result.feedUrl
            else -> FeedUrls.normalize(result.feedUrl)?.takeIf { it in subscribed }
        }
        return SearchResultItem(
            result = if (stored != null && stored != result.feedUrl) result.copy(feedUrl = stored) else result,
            isSubscribed = stored != null,
            isSubscribing = result.feedUrl in adding,
        )
    }

    private fun Exception.toLoadError(): LoadError = (this as? PodcastException)?.error ?: LoadError.UNKNOWN

    private companion object {
        const val SEARCH_DELAY_MS = 500L
        const val MIN_QUERY_LENGTH = 2
    }
}
