package com.podparar.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.Formats
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.PlaybackSpeeds
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.EmptyState
import com.podparar.core.ui.R as CoreUiR

@Composable
internal fun PlayerScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.nowPlaying.collectAsStateWithLifecycle()
    PlayerContent(
        state = state,
        onClose = onClose,
        onPlayPause = viewModel::togglePlayPause,
        onSkipBack = viewModel::skipBack,
        onSkipForward = viewModel::skipForward,
        onSeek = viewModel::seekTo,
        onSpeedChange = viewModel::setSpeed,
    )
}

/**
 * The full player, drawn from its state alone so screenshot tests can render it: big artwork,
 * the titles, a seek bar with the elapsed and remaining time, back 10 / play-pause / forward 30,
 * and the speed. [state] null means nothing is loaded.
 */
@Composable
internal fun PlayerContent(
    state: NowPlaying?,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(CoreUiR.drawable.ic_keyboard_arrow_down),
                    contentDescription = stringResource(R.string.player_close),
                )
            }
            Text(
                text = stringResource(R.string.player_now_playing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Balances the close button so the label sits in the middle.
            Spacer(Modifier.size(48.dp))
        }

        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.player_nothing_playing),
                    body = stringResource(R.string.player_nothing_playing_body),
                )
            }
        } else {
            NowPlayingBody(
                state = state,
                onPlayPause = onPlayPause,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onSeek = onSeek,
                onSpeedChange = onSpeedChange,
            )
        }
    }
}

@Composable
private fun NowPlayingBody(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = state.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.podcastTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = onSeek,
        )
        Spacer(Modifier.height(8.dp))
        Controls(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            onPlayPause = onPlayPause,
            onSkipBack = onSkipBack,
            onSkipForward = onSkipForward,
        )
        Spacer(Modifier.height(8.dp))
        val speedLabel = Formats.speedLabel(state.speed)
        val speedDescription = stringResource(R.string.player_speed_description, speedLabel)
        TextButton(
            onClick = { onSpeedChange(PlaybackSpeeds.next(state.speed)) },
            modifier = Modifier.semantics { contentDescription = speedDescription },
        ) {
            Text(speedLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * While the listener drags, the thumb and the elapsed time follow the finger and the player is
 * left alone; it seeks once on release. An unknown duration can't be sought: the bar is
 * disabled and only the elapsed time shows.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var draggingMs by remember { mutableStateOf<Long?>(null) }
    val known = durationMs > 0
    val shownMs = if (known) (draggingMs ?: positionMs).coerceIn(0L, durationMs) else positionMs
    val positionLabel = stringResource(R.string.player_position)

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = if (known) shownMs.toFloat() else 0f,
            onValueChange = { draggingMs = it.toLong() },
            onValueChangeFinished = {
                draggingMs?.let(onSeek)
                draggingMs = null
            },
            enabled = known,
            valueRange = 0f..(if (known) durationMs.toFloat() else 1f),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = positionLabel },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val timeStyle = MaterialTheme.typography.bodySmall
            val grey = MaterialTheme.colorScheme.onSurfaceVariant
            Text(Formats.timeLabel(shownMs), style = timeStyle, color = grey)
            if (known) {
                Text(Formats.remainingLabel(shownMs, durationMs), style = timeStyle, color = grey)
            }
        }
    }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
) {
    val loadingLabel = stringResource(R.string.player_loading)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(onClick = onSkipBack, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(CoreUiR.drawable.ic_replay_10),
                contentDescription = stringResource(CoreUiR.string.skip_back),
                modifier = Modifier.size(32.dp),
            )
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
            when {
                isBuffering -> CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = loadingLabel },
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
                isPlaying -> Icon(
                    painterResource(CoreUiR.drawable.ic_pause),
                    contentDescription = stringResource(CoreUiR.string.pause),
                    modifier = Modifier.size(36.dp),
                )
                else -> Icon(
                    painterResource(CoreUiR.drawable.ic_play_arrow),
                    contentDescription = stringResource(CoreUiR.string.play),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        IconButton(onClick = onSkipForward, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(CoreUiR.drawable.ic_forward_30),
                contentDescription = stringResource(CoreUiR.string.skip_forward),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
