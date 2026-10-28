package com.podparar.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podparar.core.ui.PlaceholderScreen

@Composable
internal fun PlayerScreen() {
    PlayerContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun PlayerContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.player_title),
        message = stringResource(R.string.player_message),
        modifier = modifier,
    )
}
