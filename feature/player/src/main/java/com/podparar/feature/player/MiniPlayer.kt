package com.podparar.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.PlaybackRules
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.R as CoreUiR

/**
 * The small player that `:app` shows above the bottom bar on every screen (a known exception
 * to "one entry point per feature"). Renders nothing while nothing is loaded.
 */
@Composable
fun MiniPlayer(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<PlayerViewModel>()
    val state by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val nowPlaying = state ?: return
    MiniPlayerContent(
        state = nowPlaying,
        onPlayPause = viewModel::togglePlayPause,
        onOpen = onOpenPlayer,
        modifier = modifier,
    )
}

/**
 * A white card bar: a thin progress line along its top, the artwork, the episode and show
 * titles, and a play/pause button. Tapping anywhere else opens the full player.
 */
@Composable
internal fun MiniPlayerContent(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            ProgressLine(PlaybackRules.progress(state.positionMs, state.durationMs))
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(
                    url = state.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    if (state.isPlaying) {
                        Icon(
                            painterResource(CoreUiR.drawable.ic_pause),
                            contentDescription = stringResource(CoreUiR.string.pause),
                        )
                    } else {
                        Icon(
                            painterResource(CoreUiR.drawable.ic_play_arrow),
                            contentDescription = stringResource(CoreUiR.string.play),
                        )
                    }
                }
            }
        }
    }
}

/** 2 dp along the card's top edge: the accent colour for what's been heard, grey for the rest. */
@Composable
private fun ProgressLine(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
