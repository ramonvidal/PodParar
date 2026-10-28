package com.podparar.core.downloads

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Notification channels and helpers shared by the workers. */
internal object Notifications {
    const val DOWNLOADS_CHANNEL_ID = "downloads"
    const val NEW_EPISODES_CHANNEL_ID = "new-episodes"
    const val NEW_EPISODES_NOTIFICATION_ID = 4_001

    fun createDownloadsChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                DOWNLOADS_CHANNEL_ID,
                context.getString(R.string.downloads_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.downloads_channel_description) },
        )
    }

    fun createNewEpisodesChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NEW_EPISODES_CHANNEL_ID,
                context.getString(R.string.downloads_new_episodes_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.downloads_new_episodes_channel_description) },
        )
    }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /** Opens the app without this module having to know its activity. */
    fun openAppIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
}
