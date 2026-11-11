package com.podparar.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.podparar.core.model.Download
import com.podparar.core.model.DownloadState

/**
 * The long-press menu for an episode. Items appear in this order, each only when its callback
 * is non-null: Play next, Add to queue, Remove from Up next, Mark as played / Mark as unplayed
 * (by [played]), Download / Remove download (Download when [download] is null or FAILED, else
 * Remove download), Go to show.
 * Choosing an item calls [onDismiss] and then the callback.
 */
@Composable
fun EpisodeActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    played: Boolean,
    download: Download? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onTogglePlayed: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onOpenPodcast: (() -> Unit)? = null,
) {
    val canDownload = canDownload(download)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onPlayNext != null) {
            ActionItem(R.string.play_next, R.drawable.ic_queue_play_next, onDismiss, onPlayNext)
        }
        if (onAddToQueue != null) {
            ActionItem(R.string.add_to_queue, R.drawable.ic_playlist_add, onDismiss, onAddToQueue)
        }
        if (onRemoveFromQueue != null) {
            ActionItem(R.string.remove_from_queue, R.drawable.ic_playlist_remove, onDismiss, onRemoveFromQueue)
        }
        if (onTogglePlayed != null) {
            ActionItem(
                if (played) R.string.mark_unplayed else R.string.mark_played,
                if (played) R.drawable.ic_remove_done else R.drawable.ic_done_all,
                onDismiss,
                onTogglePlayed,
            )
        }
        if (canDownload && onDownload != null) {
            ActionItem(R.string.download, R.drawable.ic_download, onDismiss, onDownload)
        }
        if (!canDownload && onRemoveDownload != null) {
            ActionItem(R.string.remove_download, R.drawable.ic_delete, onDismiss, onRemoveDownload)
        }
        if (onOpenPodcast != null) {
            ActionItem(R.string.go_to_show, R.drawable.ic_podcasts, onDismiss, onOpenPodcast)
        }
    }
}

/** True when [EpisodeActionsMenu] with these callbacks and [download] would show at least one item. */
fun hasEpisodeActions(
    download: Download? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onTogglePlayed: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onOpenPodcast: (() -> Unit)? = null,
): Boolean = firstEpisodeActionLabel(
    played = false,
    download = download,
    onPlayNext = onPlayNext,
    onAddToQueue = onAddToQueue,
    onRemoveFromQueue = onRemoveFromQueue,
    onTogglePlayed = onTogglePlayed,
    onDownload = onDownload,
    onRemoveDownload = onRemoveDownload,
    onOpenPodcast = onOpenPodcast,
) != null

/**
 * The string of the first item [EpisodeActionsMenu] would show, or null when it would show
 * none. Used as the long-press accessibility label.
 */
internal fun firstEpisodeActionLabel(
    played: Boolean,
    download: Download?,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onRemoveFromQueue: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onRemoveDownload: (() -> Unit)?,
    onOpenPodcast: (() -> Unit)?,
): Int? {
    val canDownload = canDownload(download)
    return when {
        onPlayNext != null -> R.string.play_next
        onAddToQueue != null -> R.string.add_to_queue
        onRemoveFromQueue != null -> R.string.remove_from_queue
        onTogglePlayed != null -> if (played) R.string.mark_unplayed else R.string.mark_played
        canDownload && onDownload != null -> R.string.download
        !canDownload && onRemoveDownload != null -> R.string.remove_download
        onOpenPodcast != null -> R.string.go_to_show
        else -> null
    }
}

private fun canDownload(download: Download?): Boolean =
    download == null || download.state == DownloadState.FAILED

@Composable
private fun ActionItem(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    onDismiss: () -> Unit,
    action: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            onDismiss()
            action()
        },
        leadingIcon = {
            Icon(painter = painterResource(icon), contentDescription = null)
        },
    )
}
