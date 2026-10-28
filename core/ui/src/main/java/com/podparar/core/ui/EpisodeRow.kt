package com.podparar.core.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeState
import com.podparar.core.model.Formats
import com.podparar.core.model.HtmlText
import com.podparar.core.model.PlaybackRules
import java.time.ZoneId

/**
 * One episode as a white card: the date small and grey, the title, two lines of show notes,
 * and a bottom line with the length plus "Played" or how far along it is.
 * [now] is passed in so dates like "Today" stay stable in previews and tests.
 *
 * Tapping the card or its round trailing button calls [onClick] (play). The button shows
 * pause when this is the [isCurrent] episode and [isPlaying], play otherwise; the current
 * episode's title is in the primary colour.
 *
 * In lists that mix shows, pass [podcastTitle] and [artworkUrl]: the date line then reads
 * "<show> · <date>" and a 48.dp picture of the episode leads the row.
 *
 * A long press opens a small menu listing, in this order and only for the callbacks that are
 * set, "Play next" ([onPlayNext]), "Add to queue" ([onAddToQueue]) and "Mark as played" or
 * "Mark as unplayed" ([onTogglePlayed]). With none of them set there is no menu.
 * The progress bar has no stop dot at its end.
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onTogglePlayed: (() -> Unit)? = null,
    podcastTitle: String? = null,
    artworkUrl: String? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
) {
    val dateLabel = remember(episode.pubDate, now) {
        Formats.pubDateLabel(episode.pubDate, now, ZoneId.systemDefault())
    }
    val topLine = when {
        podcastTitle.isNullOrEmpty() -> dateLabel
        dateLabel.isEmpty() -> podcastTitle
        else -> "$podcastTitle · $dateLabel"
    }
    val notes = remember(episode.description) { HtmlText.toPlain(episode.description) }
    val duration = remember(episode.durationMs) { Formats.durationLabel(episode.durationMs) }
    val state = episode.state
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    var menuOpen by remember { mutableStateOf(false) }
    val markLabel = stringResource(if (episode.played) R.string.mark_unplayed else R.string.mark_played)
    val showsPause = isCurrent && isPlaying
    val playNextLabel = stringResource(R.string.play_next)
    val addToQueueLabel = stringResource(R.string.add_to_queue)
    val hasMenu = onPlayNext != null || onAddToQueue != null || onTogglePlayed != null
    val menuLabel = when {
        onPlayNext != null -> playNextLabel
        onAddToQueue != null -> addToQueueLabel
        onTogglePlayed != null -> markLabel
        else -> null
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (hasMenu) ({ menuOpen = true }) else null,
                        onLongClickLabel = menuLabel,
                    )
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (podcastTitle != null) {
                    Artwork(
                        url = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(48.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (topLine.isNotEmpty()) {
                        Text(
                            topLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = grey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            state == EpisodeState.PLAYED -> grey
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (notes.isNotEmpty()) {
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = grey,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val showProgress = state == EpisodeState.IN_PROGRESS && episode.durationMs > 0
                    if (duration.isNotEmpty() || state == EpisodeState.PLAYED || showProgress) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (duration.isNotEmpty()) {
                                Text(duration, style = MaterialTheme.typography.labelMedium, color = grey)
                            }
                            when {
                                state == EpisodeState.PLAYED -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = grey,
                                    )
                                    Text(stringResource(R.string.played), style = MaterialTheme.typography.labelMedium, color = grey)
                                }
                                showProgress -> {
                                    val progress = PlaybackRules.progress(episode.positionMs, episode.durationMs)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.width(72.dp).height(3.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        gapSize = 0.dp,
                                        drawStopIndicator = {},
                                    )
                                }
                            }
                        }
                    }
                }
                FilledTonalIconButton(onClick = onClick) {
                    Icon(
                        painter = painterResource(if (showsPause) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (showsPause) R.string.pause else R.string.play),
                    )
                }
            }
            if (hasMenu) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text(playNextLabel) },
                            onClick = {
                                menuOpen = false
                                onPlayNext()
                            },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.ic_queue_play_next), contentDescription = null)
                            },
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text(addToQueueLabel) },
                            onClick = {
                                menuOpen = false
                                onAddToQueue()
                            },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.ic_playlist_add), contentDescription = null)
                            },
                        )
                    }
                    if (onTogglePlayed != null) {
                        DropdownMenuItem(
                            text = { Text(markLabel) },
                            onClick = {
                                menuOpen = false
                                onTogglePlayed()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (episode.played) R.drawable.ic_remove_done else R.drawable.ic_done_all,
                                    ),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
