package com.podparar.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.AppUpdate

/**
 * "PodParar 0.25 is available – Install". Takes no room when there's nothing to offer.
 * This feature's only public entry point; `:app` shows it at the top of the screen.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // The banner sits in the Scaffold's top bar, so it keeps clear of the status bar itself.
    val placed = modifier.statusBarsPadding()
    when (val current = state) {
        UpdateState.None -> Unit
        is UpdateState.Available -> UpdateBannerContent(
            update = current.update,
            progress = null,
            onInstall = { viewModel.install(current.update) },
            onLater = { viewModel.later(current.update) },
            modifier = placed,
        )
        is UpdateState.Downloading -> UpdateBannerContent(
            update = current.update,
            progress = current.progress,
            onInstall = {},
            onLater = {},
            modifier = placed,
        )
        is UpdateState.NeedsPermission -> BannerCard(placed) {
            MessageRow(stringResource(R.string.update_allow)) {
                Button(onClick = { context.startActivity(viewModel.allowInstallsIntent()) }) {
                    Text(stringResource(R.string.update_allow_button))
                }
            }
        }
        is UpdateState.Failed -> BannerCard(placed) {
            MessageRow(stringResource(R.string.update_failed)) {
                TextButton(onClick = { viewModel.install(current.update) }) { Text(stringResource(R.string.update_retry)) }
            }
        }
    }
}

/**
 * Drawn from its arguments alone, so screenshot tests can render it. [progress] is null while
 * [update] is only being offered, else how far its download has got (0 to 1).
 */
@Composable
internal fun UpdateBannerContent(
    update: AppUpdate,
    progress: Float?,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerCard(modifier) {
        if (progress == null) {
            MessageRow(stringResource(R.string.update_available, update.versionName)) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                Button(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
            }
        } else {
            Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                // The card's end padding is narrow for the buttons; even it out for the bar.
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
            )
        }
    }
}

/** A soft white card on the grey page, like the rest of the app. */
@Composable
private fun BannerCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun MessageRow(text: String, actions: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        actions()
    }
}
