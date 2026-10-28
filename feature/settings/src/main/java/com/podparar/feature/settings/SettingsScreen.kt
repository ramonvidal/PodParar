package com.podparar.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podparar.core.ui.PlaceholderScreen

@Composable
internal fun SettingsScreen() {
    SettingsContent()
}

/** Drawn from its state alone, so screenshot tests can render it. A placeholder until its phase lands. */
@Composable
internal fun SettingsContent(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        message = stringResource(R.string.settings_message),
        modifier = modifier,
    )
}
