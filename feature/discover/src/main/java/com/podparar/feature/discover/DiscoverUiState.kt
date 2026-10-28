package com.podparar.feature.discover

import com.podparar.core.model.LoadError
import com.podparar.core.model.SearchResult

/** One row of search results, and whether that show is already followed or being added right now. */
internal data class SearchResultItem(
    val result: SearchResult,
    val isSubscribed: Boolean,
    val isSubscribing: Boolean = false,
)

/** Everything the Discover tab draws. */
internal data class DiscoverUiState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: LoadError? = null,
)

/** The "Add by RSS link" dialog while it's open. */
internal data class AddByLinkState(
    val link: String = "",
    val isAdding: Boolean = false,
    val error: LoadError? = null,
)
