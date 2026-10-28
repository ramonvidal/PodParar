package com.podparar.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podparar.core.ui.PlaceholderScreen

@Composable
internal fun LibraryScreen() {
    LibraryContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun LibraryContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.library_title),
        message = stringResource(R.string.library_message),
        modifier = modifier,
    )
}
