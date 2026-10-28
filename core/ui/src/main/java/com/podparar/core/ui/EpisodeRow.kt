package com.podparar.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 */
@Composable
fun EpisodeRow(episode: Episode, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dateLabel = remember(episode.pubDate, now) {
        Formats.pubDateLabel(episode.pubDate, now, ZoneId.systemDefault())
    }
    val notes = remember(episode.description) { HtmlText.toPlain(episode.description) }
    val duration = remember(episode.durationMs) { Formats.durationLabel(episode.durationMs) }
    val state = episode.state
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (dateLabel.isNotEmpty()) {
                Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = grey)
            }
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (state == EpisodeState.PLAYED) grey else MaterialTheme.colorScheme.onSurface,
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
                            )
                        }
                    }
                }
            }
        }
    }
}
