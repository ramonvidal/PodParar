package com.podparar.ui.root

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.ui.navigation.PodPararNavHost

private const val PREFS = "app_prompts"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

/** No accounts and no sign-in: the app opens straight onto its tabs. */
@Composable
fun RootScreen(viewModel: RootViewModel = hiltViewModel()) {
    val hasSubscriptions by viewModel.hasSubscriptions.collectAsStateWithLifecycle()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AskForNotificationsOnce(enabled = hasSubscriptions)
    }
    PodPararNavHost()
}

/**
 * New-episode alerts need POST_NOTIFICATIONS on Android 13+. Asked once, the first time the user
 * follows a show; whatever they answer, the app never asks again.
 */
@Composable
private fun AskForNotificationsOnce(enabled: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return@LaunchedEffect
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
