package com.podparar.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podparar.core.ui.PlaceholderScreen

@Composable
internal fun DiscoverScreen() {
    DiscoverContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun DiscoverContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.discover_title),
        message = stringResource(R.string.discover_message),
        modifier = modifier,
    )
}
