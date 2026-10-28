package com.podparar.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podparar.core.ui.PlaceholderScreen

@Composable
internal fun HomeScreen() {
    HomeContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun HomeContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.home_title),
        message = stringResource(R.string.home_message),
        modifier = modifier,
    )
}
