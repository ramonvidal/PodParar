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
import androidx.compose.material3.CircularProgressIndicator
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
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState
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
 * "Mark as unplayed" ([onTogglePlayed]), then "Download" ([onDownload], when there is no
 * [download] or it failed) or "Remove download" ([onRemoveDownload], otherwise), and last
 * "Go to show" ([onOpenPodcast]); see [EpisodeActionsMenu]. With none of them applying there
 * is no menu.
 *
 * When [download] is set the bottom line also says where it stands: "Downloaded", a small
 * circle filling up with "Downloading" (spinning while the size is unknown), or "Download
 * failed" in the error colour.
 * The progress bar has no stop dot at its end and the circle no gap.
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
    download: Download? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onOpenPodcast: (() -> Unit)? = null,
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
    val showsPause = isCurrent && isPlaying
    val menuLabelRes = firstEpisodeActionLabel(
        played = episode.played,
        download = download,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onRemoveFromQueue = null,
        onTogglePlayed = onTogglePlayed,
        onDownload = onDownload,
        onRemoveDownload = onRemoveDownload,
        onOpenPodcast = onOpenPodcast,
    )
    val menuLabel = menuLabelRes?.let { stringResource(it) }

    Card(modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (menuLabel != null) ({ menuOpen = true }) else null,
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
                    if (duration.isNotEmpty() || state == EpisodeState.PLAYED || showProgress || download != null) {
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
                            if (download != null) {
                                DownloadStatus(download)
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
            if (menuLabel != null) {
                EpisodeActionsMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    played = episode.played,
                    download = download,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onTogglePlayed = onTogglePlayed,
                    onDownload = onDownload,
                    onRemoveDownload = onRemoveDownload,
                    onOpenPodcast = onOpenPodcast,
                )
            }
        }
    }
}

/** The download part of an episode's bottom line: an icon or small circle, then a label. */
@Composable
private fun DownloadStatus(download: Download) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (download.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else grey
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (download.state) {
            DownloadState.DONE -> Icon(
                painter = painterResource(R.drawable.ic_download_done),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color,
            )
            DownloadState.FAILED -> Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color,
            )
            DownloadState.QUEUED, DownloadState.RUNNING -> {
                val progress = download.progress
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        gapSize = 0.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        Text(
            stringResource(
                when (download.state) {
                    DownloadState.DONE -> R.string.downloaded
                    DownloadState.FAILED -> R.string.download_failed
                    DownloadState.QUEUED, DownloadState.RUNNING -> R.string.downloading
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
