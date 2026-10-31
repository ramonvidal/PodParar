package com.podparar.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.Download
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeWithPodcast
import com.podparar.core.model.PlaybackRules
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.EmptyState
import com.podparar.core.ui.EpisodeRow
import com.podparar.core.ui.R as CoreUiR
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal data class HomeUiState(
    /** Started, not finished, most recently played first; none that are in [upNext]. */
    val continueListening: List<EpisodeWithPodcast> = emptyList(),
    /** The queue, in order; never includes the episode playing. */
    val upNext: List<EpisodeWithPodcast> = emptyList(),
    /** Unplayed, unstarted episodes from every show, newest first; none that are in [upNext]. */
    val newEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val hasSubscriptions: Boolean = true,
    val isLoading: Boolean = true,
    /** The episode loaded in the player, if any. */
    val nowPlayingId: String? = null,
    /** Whether [nowPlayingId] is playing right now (not paused). */
    val isPlaying: Boolean = false,
    val now: Long = System.currentTimeMillis(),
    /** Every download, keyed by episode id. */
    val downloads: Map<String, Download> = emptyMap(),
)

@Composable
internal fun HomeScreen(
    onOpenPodcast: (feedUrl: String) -> Unit,
    onFindShows: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onPlay = viewModel::play,
        onOpenPodcast = onOpenPodcast,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onRemoveFromQueue = viewModel::removeFromQueue,
        onMoveInQueue = viewModel::moveInQueue,
        onTogglePlayed = viewModel::togglePlayed,
        onFindShows = onFindShows,
        onDownload = viewModel::download,
        onRemoveDownload = viewModel::removeDownload,
    )
}

/**
 * Home, drawn from its state alone so screenshot tests can render it.
 * [onMoveInQueue] gets indices within Up next (0 = its first item), once per drag, on drop.
 * Long-pressing a Continue listening card, or tapping a queued episode's show name, calls
 * [onOpenPodcast] with that show's feed URL. Long-pressing a new episode also offers
 * [onDownload] or [onRemoveDownload].
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onPlay: (Episode) -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
    onPlayNext: (Episode) -> Unit,
    onAddToQueue: (Episode) -> Unit,
    onRemoveFromQueue: (Episode) -> Unit,
    onMoveInQueue: (from: Int, to: Int) -> Unit,
    onTogglePlayed: (Episode) -> Unit,
    onFindShows: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (Episode) -> Unit = {},
    onRemoveDownload: (Episode) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            // The app's Scaffold already keeps clear of the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.hasSubscriptions -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    action = {
                        Button(onClick = onFindShows, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.home_find_show))
                        }
                    },
                )
            }
            else -> HomeList(
                state = state,
                onPlay = onPlay,
                onOpenPodcast = onOpenPodcast,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onRemoveFromQueue = onRemoveFromQueue,
                onMoveInQueue = onMoveInQueue,
                onTogglePlayed = onTogglePlayed,
                onDownload = onDownload,
                onRemoveDownload = onRemoveDownload,
            )
        }
    }
}

@Composable
private fun HomeList(
    state: HomeUiState,
    onPlay: (Episode) -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
    onPlayNext: (Episode) -> Unit,
    onAddToQueue: (Episode) -> Unit,
    onRemoveFromQueue: (Episode) -> Unit,
    onMoveInQueue: (from: Int, to: Int) -> Unit,
    onTogglePlayed: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
    onRemoveDownload: (Episode) -> Unit,
) {
    // While a queue item is dragged the list shows this local order; after the drop it is kept
    // until the database sends the new order, so the row doesn't jump back for a frame.
    var dragOrder by remember { mutableStateOf<List<EpisodeWithPodcast>?>(null) }
    var dragFrom by remember { mutableIntStateOf(NOT_DRAGGING) }
    LaunchedEffect(state.upNext) {
        if (dragFrom == NOT_DRAGGING) dragOrder = null
    }
    val upNext = dragOrder ?: state.upNext
    val currentOnMove by rememberUpdatedState(onMoveInQueue)

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Indices by key, so the items above Up next in this list don't matter.
        val order = dragOrder ?: return@rememberReorderableLazyListState
        val fromIndex = order.indexOfFirst { upNextKey(it.episode.id) == from.key }
        val toIndex = order.indexOfFirst { upNextKey(it.episode.id) == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        dragOrder = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.continueListening.isNotEmpty()) {
            item(key = "continue_header", contentType = "header") {
                SectionHeader(stringResource(R.string.home_continue_listening))
            }
            item(key = "continue_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.continueListening, key = { it.episode.id }) { item ->
                        ContinueCard(
                            item = item,
                            onClick = { onPlay(item.episode) },
                            onLongClick = { onOpenPodcast(item.episode.podcastUrl) },
                        )
                    }
                }
            }
        }

        if (upNext.isNotEmpty()) {
            item(key = "up_next_header", contentType = "header") {
                SectionHeader(
                    title = stringResource(R.string.home_up_next),
                    detail = pluralStringResource(R.plurals.home_up_next_count, upNext.size, upNext.size),
                )
            }
            itemsIndexed(upNext, key = { _, item -> upNextKey(item.episode.id) }) { index, item ->
                ReorderableItem(reorderState, key = upNextKey(item.episode.id)) { isDragging ->
                    UpNextRow(
                        item = item,
                        isDragging = isDragging,
                        onClick = { onPlay(item.episode) },
                        onOpenPodcast = { onOpenPodcast(item.episode.podcastUrl) },
                        onRemove = { onRemoveFromQueue(item.episode) },
                        onMoveUp = if (index > 0) ({ onMoveInQueue(index, index - 1) }) else null,
                        onMoveDown = if (index < upNext.lastIndex) ({ onMoveInQueue(index, index + 1) }) else null,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = {
                                val order = dragOrder ?: state.upNext
                                dragOrder = order
                                dragFrom = order.indexOfFirst { it.episode.id == item.episode.id }
                            },
                            onDragStopped = {
                                val from = dragFrom
                                val to = dragOrder?.indexOfFirst { it.episode.id == item.episode.id } ?: -1
                                dragFrom = NOT_DRAGGING
                                if (from >= 0 && to >= 0 && from != to) {
                                    currentOnMove(from, to)
                                } else {
                                    dragOrder = null
                                }
                            },
                        ),
                    )
                }
            }
        }

        item(key = "new_header", contentType = "header") {
            SectionHeader(stringResource(R.string.home_new_episodes))
        }
        if (state.newEpisodes.isEmpty()) {
            item(key = "caught_up") {
                Text(
                    stringResource(R.string.home_caught_up),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.newEpisodes, key = { "new:" + it.episode.id }) { item ->
                val episode = item.episode
                val isCurrent = episode.id == state.nowPlayingId
                EpisodeRow(
                    episode = episode,
                    now = state.now,
                    onClick = { onPlay(episode) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && state.isPlaying,
                    onTogglePlayed = { onTogglePlayed(episode) },
                    podcastTitle = item.podcastTitle,
                    artworkUrl = item.artworkUrl,
                    onPlayNext = { onPlayNext(episode) },
                    onAddToQueue = { onAddToQueue(episode) },
                    download = state.downloads[episode.id],
                    onDownload = { onDownload(episode) },
                    onRemoveDownload = { onRemoveDownload(episode) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** A 140 dp card: artwork, title, how far along it is and the minutes left. */
@Composable
private fun ContinueCard(item: EpisodeWithPodcast, onClick: () -> Unit, onLongClick: () -> Unit) {
    val episode = item.episode
    val remainingMs = (episode.durationMs - episode.positionMs).coerceAtLeast(0L)
    Card(modifier = Modifier.width(140.dp)) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The title is right underneath, so the picture needs no description of its own.
            Artwork(
                url = item.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { PlaybackRules.progress(episode.positionMs, episode.durationMs) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                val minutes = ((remainingMs + MS_PER_MINUTE - 1) / MS_PER_MINUTE).coerceAtLeast(1L).toInt()
                Text(
                    pluralStringResource(R.plurals.home_minutes_left, minutes, minutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A queued episode: drag handle, artwork, title and show. Swiping it either way removes it;
 * screen readers get Remove / Move up / Move down as actions instead.
 */
@Composable
private fun UpNextRow(
    item: EpisodeWithPodcast,
    isDragging: Boolean,
    onClick: () -> Unit,
    onOpenPodcast: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    handleModifier: Modifier,
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                currentOnRemove()
                true
            } else {
                false
            }
        },
    )
    val removeLabel = stringResource(CoreUiR.string.remove_from_queue)
    val moveUpLabel = stringResource(R.string.home_move_up)
    val moveDownLabel = stringResource(R.string.home_move_down)

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                    if (onMoveUp != null) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                    if (onMoveDown != null) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                }
            },
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, CardDefaults.shape)
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_playlist_remove),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = handleModifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_drag_handle),
                        contentDescription = stringResource(R.string.home_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The title is right beside it, so the picture needs no description of its own.
                Artwork(
                    url = item.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        item.episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpenPodcast),
                    )
                }
            }
        }
    }
}

/** Up next keys are prefixed: the same episode can also be listed under New episodes. */
private fun upNextKey(episodeId: String): String = "up:$episodeId"

private const val NOT_DRAGGING = -1
private const val MS_PER_MINUTE = 60_000L
