package com.podparar.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.Episode
import com.podparar.core.model.HtmlText
import com.podparar.core.model.LoadError
import com.podparar.core.model.Podcast
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.EmptyState
import com.podparar.core.ui.EpisodeRow
import com.podparar.core.ui.messageRes
import com.podparar.core.ui.R as CoreUiR

internal data class PodcastUiState(
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshError: LoadError? = null,
    val now: Long = System.currentTimeMillis(),
    /** The episode loaded in the player, if any. */
    val nowPlayingId: String? = null,
    /** Whether [nowPlayingId] is playing right now (not paused). */
    val isPlaying: Boolean = false,
)

@Composable
internal fun PodcastScreen(
    onBack: () -> Unit,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PodcastContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onUnsubscribe = { viewModel.unsubscribe(onDone = onBack) },
        onPlayEpisode = viewModel::play,
        onTogglePlayed = viewModel::togglePlayed,
        onMarkAllPlayed = viewModel::markAllPlayed,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
    )
}

/**
 * A show's page, drawn from its state alone so screenshot tests can render it.
 * [onUnsubscribe] is called once the listener has confirmed it. Tapping an episode calls
 * [onPlayEpisode] (play, or play/pause if it's the loaded one); long-pressing it offers
 * [onPlayNext], [onAddToQueue] and [onTogglePlayed]. The top bar's menu offers
 * [onMarkAllPlayed] (at once) and unsubscribing.
 */
@Composable
internal fun PodcastContent(
    state: PodcastUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayEpisode: (Episode) -> Unit = {},
    onTogglePlayed: (Episode) -> Unit = {},
    onMarkAllPlayed: () -> Unit = {},
    onPlayNext: (Episode) -> Unit = {},
    onAddToQueue: (Episode) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmUnsubscribe by rememberSaveable { mutableStateOf(false) }
    val podcast = state.podcast

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.library_back),
                    )
                }
            },
            actions = {
                if (podcast != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painterResource(CoreUiR.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.library_more_options),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_mark_all_played)) },
                                onClick = {
                                    menuOpen = false
                                    onMarkAllPlayed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_unsubscribe)) },
                                onClick = {
                                    menuOpen = false
                                    confirmUnsubscribe = true
                                },
                            )
                        }
                    }
                }
            },
            // The app's Scaffold already keeps clear of the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            podcast == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = stringResource(R.string.library_show_not_found))
            }
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        PodcastHeader(podcast, state.refreshError)
                    }
                    item(key = "count") {
                        Text(
                            pluralStringResource(
                                R.plurals.library_episode_count,
                                state.episodes.size,
                                state.episodes.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        val isCurrent = episode.id == state.nowPlayingId
                        EpisodeRow(
                            episode = episode,
                            now = state.now,
                            onClick = { onPlayEpisode(episode) },
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && state.isPlaying,
                            onTogglePlayed = { onTogglePlayed(episode) },
                            onPlayNext = { onPlayNext(episode) },
                            onAddToQueue = { onAddToQueue(episode) },
                        )
                    }
                }
            }
        }
    }

    if (confirmUnsubscribe && podcast != null) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text(stringResource(R.string.library_unsubscribe_title, podcast.title)) },
            text = { Text(stringResource(R.string.library_unsubscribe_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnsubscribe = false
                        onUnsubscribe()
                    },
                ) {
                    Text(stringResource(R.string.library_unsubscribe))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsubscribe = false }) {
                    Text(stringResource(CoreUiR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PodcastHeader(podcast: Podcast, refreshError: LoadError?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(
                url = podcast.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    podcast.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (podcast.author.isNotBlank()) {
                    Text(
                        podcast.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val description = remember(podcast.description) { HtmlText.toPlain(podcast.description) }
        if (description.isNotEmpty()) {
            ShowNotes(description)
        }
        if (refreshError != null) {
            Text(
                stringResource(refreshError.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The show's description, four lines until "More" is tapped. */
@Composable
private fun ShowNotes(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (expanded || overflows) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(if (expanded) R.string.library_less else R.string.library_more))
            }
        }
    }
}

private const val COLLAPSED_LINES = 4
