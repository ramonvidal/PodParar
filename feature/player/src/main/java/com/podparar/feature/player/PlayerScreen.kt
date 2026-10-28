package com.podparar.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.podparar.core.model.SkipLengths
import com.podparar.core.model.SleepTimer
import com.podparar.core.model.SleepTimers
import com.podparar.core.ui.Artwork
import com.podparar.core.ui.EmptyState
import com.podparar.core.ui.R as CoreUiR
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val skipBackMs by viewModel.skipBackMs.collectAsStateWithLifecycle()
    val skipForwardMs by viewModel.skipForwardMs.collectAsStateWithLifecycle()

    // The timer's label counts down, so the clock ticks once a second while a timed one runs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepTimer) {
        now = System.currentTimeMillis()
        val timer = sleepTimer
        while (timer != null && timer.endsAt != null) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    PlayerContent(
        state = state,
        onClose = onClose,
        onPlayPause = viewModel::togglePlayPause,
        onSkipBack = viewModel::skipBack,
        onSkipForward = viewModel::skipForward,
        onSeek = viewModel::seekTo,
        onSpeedChange = viewModel::setSpeed,
        sleepTimer = sleepTimer,
        now = now,
        skipBackMs = skipBackMs,
        skipForwardMs = skipForwardMs,
        onSetSleepTimer = viewModel::setSleepTimer,
        onSleepAtEndOfEpisode = viewModel::setSleepAtEndOfEpisode,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
    )
}

/**
 * The full player, drawn from its state alone so screenshot tests can render it: big artwork,
 * the titles, a seek bar with the elapsed and remaining time, skip back / play-pause / skip
 * forward (icons matching [skipBackMs] / [skipForwardMs]), the speed and the sleep timer, each
 * opening its own bottom sheet. [state] null means nothing is loaded. [now] (epoch ms) drives
 * the timer's countdown label.
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
    sleepTimer: SleepTimer? = null,
    now: Long = System.currentTimeMillis(),
    skipBackMs: Long = SkipLengths.DEFAULT_BACK_MS,
    skipForwardMs: Long = SkipLengths.DEFAULT_FORWARD_MS,
    onSetSleepTimer: (Int) -> Unit = {},
    onSleepAtEndOfEpisode: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
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
                sleepTimer = sleepTimer,
                now = now,
                skipBackMs = skipBackMs,
                skipForwardMs = skipForwardMs,
                onSetSleepTimer = onSetSleepTimer,
                onSleepAtEndOfEpisode = onSleepAtEndOfEpisode,
                onCancelSleepTimer = onCancelSleepTimer,
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
    sleepTimer: SleepTimer?,
    now: Long,
    skipBackMs: Long,
    skipForwardMs: Long,
    onSetSleepTimer: (Int) -> Unit,
    onSleepAtEndOfEpisode: () -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
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
            skipBackMs = skipBackMs,
            skipForwardMs = skipForwardMs,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val speedLabel = Formats.speedLabel(state.speed)
            val speedDescription = stringResource(R.string.player_speed_description, speedLabel)
            TextButton(
                onClick = { showSpeedSheet = true },
                modifier = Modifier.semantics { contentDescription = speedDescription },
            ) {
                Text(speedLabel, style = MaterialTheme.typography.titleMedium)
            }
            SleepTimerButton(
                sleepTimer = sleepTimer,
                now = now,
                onClick = { showSleepSheet = true },
            )
        }
    }

    if (showSpeedSheet) {
        SpeedSheet(
            currentSpeed = state.speed,
            podcastTitle = state.podcastTitle,
            onSpeedChange = onSpeedChange,
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showSleepSheet) {
        SleepTimerSheet(
            running = sleepTimer != null,
            onSetSleepTimer = onSetSleepTimer,
            onSleepAtEndOfEpisode = onSleepAtEndOfEpisode,
            onCancelSleepTimer = onCancelSleepTimer,
            onDismiss = { showSleepSheet = false },
        )
    }
}

@Composable
private fun SleepTimerButton(sleepTimer: SleepTimer?, now: Long, onClick: () -> Unit) {
    val title = stringResource(CoreUiR.string.sleep_timer)
    if (sleepTimer == null) {
        IconButton(onClick = onClick) {
            Icon(
                painterResource(CoreUiR.drawable.ic_bedtime),
                contentDescription = title,
            )
        }
    } else {
        val label = SleepTimers.label(sleepTimer, now)
        val description = stringResource(R.string.player_sleep_running, label)
        TextButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Icon(
                painterResource(CoreUiR.drawable.ic_bedtime),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The speed options, the current one checked; picking one applies it and closes the sheet. */
@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    podcastTitle: String,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(
                title = stringResource(CoreUiR.string.speed),
                note = stringResource(R.string.player_speed_remembered, podcastTitle),
            )
            PlaybackSpeeds.options.forEach { option ->
                SheetOption(
                    label = Formats.speedLabel(option),
                    checked = abs(option - currentSpeed) < 0.001f,
                    onClick = {
                        onSpeedChange(option)
                        close()
                    },
                )
            }
        }
    }
}

/** 15 / 30 / 45 / 60 min, end of episode and, while one runs, turn off. */
@Composable
private fun SleepTimerSheet(
    running: Boolean,
    onSetSleepTimer: (Int) -> Unit,
    onSleepAtEndOfEpisode: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(title = stringResource(CoreUiR.string.sleep_timer), note = null)
            SleepTimers.minuteOptions.forEach { minutes ->
                SheetOption(
                    label = stringResource(R.string.player_sleep_minutes, minutes),
                    checked = false,
                    onClick = {
                        onSetSleepTimer(minutes)
                        close()
                    },
                )
            }
            SheetOption(
                label = stringResource(CoreUiR.string.end_of_episode),
                checked = false,
                onClick = {
                    onSleepAtEndOfEpisode()
                    close()
                },
            )
            if (running) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SheetOption(
                    label = stringResource(R.string.player_sleep_turn_off),
                    checked = false,
                    onClick = {
                        onCancelSleepTimer()
                        close()
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, note: String?) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetOption(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (checked) {
            Icon(
                painterResource(CoreUiR.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
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
            // The default track draws a "stop indicator" dot at its end; not on this bar.
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    enabled = known,
                    drawStopIndicator = null,
                )
            },
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
    skipBackMs: Long,
    skipForwardMs: Long,
) {
    val backIcon = when (skipBackMs) {
        5_000L -> CoreUiR.drawable.ic_replay_5
        30_000L -> CoreUiR.drawable.ic_replay_30
        else -> CoreUiR.drawable.ic_replay_10
    }
    val forwardIcon = when (skipForwardMs) {
        5_000L -> CoreUiR.drawable.ic_forward_5
        10_000L -> CoreUiR.drawable.ic_forward_10
        else -> CoreUiR.drawable.ic_forward_30
    }
    val loadingLabel = stringResource(R.string.player_loading)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(onClick = onSkipBack, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(backIcon),
                contentDescription = stringResource(
                    R.string.player_skip_back,
                    (skipBackMs / 1_000L).toInt(),
                ),
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
                painterResource(forwardIcon),
                contentDescription = stringResource(
                    R.string.player_skip_forward,
                    (skipForwardMs / 1_000L).toInt(),
                ),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
