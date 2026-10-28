package com.podparar.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.SearchResult
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.EmptyState
import com.podparar.core.ui.messageRes
import com.podparar.core.ui.R as CoreUiR

@Composable
internal fun DiscoverScreen(
    onOpenPodcast: (feedUrl: String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.addByLink.collectAsStateWithLifecycle()
    val subscribeError by viewModel.subscribeError.collectAsStateWithLifecycle()
    val openPodcast by rememberUpdatedState(onOpenPodcast)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openPodcast.collect { openPodcast(it) }
    }
    LaunchedEffect(subscribeError) {
        val error = subscribeError ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(error.messageRes()))
        viewModel.onSubscribeErrorShown()
    }

    Box(Modifier.fillMaxSize()) {
        DiscoverContent(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onSubscribe = viewModel::onSubscribe,
            onOpenPodcast = onOpenPodcast,
            onAddByLink = viewModel::onOpenAddByLink,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    dialog?.let {
        AddByLinkDialog(
            state = it,
            onLinkChange = viewModel::onLinkChange,
            onAdd = viewModel::onAddLink,
            onDismiss = viewModel::onDismissAddByLink,
        )
    }
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun DiscoverContent(
    state: DiscoverUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSubscribe: (SearchResult) -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
    onAddByLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(stringResource(R.string.discover_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            SearchField(query = state.query, onQueryChange = onQueryChange, onSearch = onSearch)
            TextButton(onClick = onAddByLink, modifier = Modifier.padding(top = 4.dp)) {
                Icon(painterResource(CoreUiR.drawable.ic_link), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.discover_add_by_link))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.error != null -> EmptyState(
                    title = stringResource(state.error.messageRes()),
                    modifier = Modifier.align(Alignment.Center),
                    icon = CoreUiR.drawable.ic_search,
                    action = {
                        Button(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(CoreUiR.string.retry))
                        }
                    },
                )
                state.isSearching && state.results.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                !state.hasSearched -> EmptyState(
                    title = stringResource(R.string.discover_start_title),
                    body = stringResource(R.string.discover_start_body),
                    icon = CoreUiR.drawable.ic_search,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.results.isEmpty() -> EmptyState(
                    title = stringResource(R.string.discover_no_results, state.query.trim()),
                    icon = CoreUiR.drawable.ic_search,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    ResultList(state.results, onSubscribe = onSubscribe, onOpenPodcast = onOpenPodcast)
                    if (state.isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.discover_search_hint)) },
        leadingIcon = { Icon(painterResource(CoreUiR.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.discover_clear_search),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focus.clearFocus()
            onSearch()
        }),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun ResultList(
    results: List<SearchResultItem>,
    onSubscribe: (SearchResult) -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.result.feedUrl }) { item ->
            ResultRow(item, onSubscribe = onSubscribe, onOpenPodcast = onOpenPodcast)
        }
    }
}

@Composable
private fun ResultRow(
    item: SearchResultItem,
    onSubscribe: (SearchResult) -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
) {
    val result = item.result
    val clickable = if (item.isSubscribed) Modifier.clickable { onOpenPodcast(result.feedUrl) } else Modifier
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = clickable.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(url = result.artworkUrl, contentDescription = null, modifier = Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.author.isNotBlank()) {
                    Text(
                        result.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SubscribeButton(item, onSubscribe = { onSubscribe(result) }, onOpen = { onOpenPodcast(result.feedUrl) })
        }
    }
}

@Composable
private fun SubscribeButton(item: SearchResultItem, onSubscribe: () -> Unit, onOpen: () -> Unit) {
    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    when {
        item.isSubscribed -> FilledTonalButton(onClick = onOpen, contentPadding = padding) {
            Icon(painterResource(CoreUiR.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(CoreUiR.string.subscribed))
        }
        item.isSubscribing -> Button(onClick = {}, enabled = false, contentPadding = padding) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(CoreUiR.string.subscribe))
        }
        else -> Button(onClick = onSubscribe, contentPadding = padding) {
            Text(stringResource(CoreUiR.string.subscribe))
        }
    }
}
