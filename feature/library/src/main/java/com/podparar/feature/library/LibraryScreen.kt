package com.podparar.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.Podcast
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.EmptyState

internal data class LibraryUiState(
    val podcasts: List<Podcast> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@Composable
internal fun LibraryScreen(
    onOpenPodcast: (String) -> Unit,
    onFindShows: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onOpenPodcast = onOpenPodcast,
        onRefresh = viewModel::refresh,
        onFindShows = onFindShows,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onOpenPodcast: (String) -> Unit,
    onRefresh: () -> Unit,
    onFindShows: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.library_title)) },
            // The app's Scaffold already keeps clear of the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.podcasts.isEmpty()) {
                    LibraryEmpty(onFindShows)
                } else {
                    PodcastGrid(state.podcasts, onOpenPodcast)
                }
            }
        }
    }
}

@Composable
private fun LibraryEmpty(onFindShows: () -> Unit) {
    // Scrollable so pull-to-refresh still works on an empty Library; at least a screen tall so it stays centred.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                action = {
                    Button(onClick = onFindShows, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.library_find_show))
                    }
                },
            )
        }
    }
}

@Composable
private fun PodcastGrid(podcasts: List<Podcast>, onOpenPodcast: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(podcasts, key = { it.feedUrl }) { podcast ->
            PodcastTile(podcast, onClick = { onOpenPodcast(podcast.feedUrl) })
        }
    }
}

@Composable
private fun PodcastTile(podcast: Podcast, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The title is right underneath, so the picture needs no description of its own.
        Artwork(
            url = podcast.artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(
                podcast.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (podcast.author.isNotBlank()) {
                Text(
                    podcast.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
