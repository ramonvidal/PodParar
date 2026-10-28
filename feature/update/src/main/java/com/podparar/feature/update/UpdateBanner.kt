package com.podparar.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The "a new version is available" banner that `:app` shows at the top of the screen (a known
 * exception to "one entry point per feature"). Renders nothing until updates arrive in Phase 6.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    // No update check yet.
}
