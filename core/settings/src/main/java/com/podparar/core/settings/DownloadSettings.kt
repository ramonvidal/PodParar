package com.podparar.core.settings

import kotlinx.coroutines.flow.Flow

/** This phone's download and background-refresh choices. */
interface DownloadSettings {
    /** Download episodes only on unmetered networks; true until set. */
    val wifiOnly: Flow<Boolean>

    /** Post a notification when a background refresh finds new episodes; true until set. */
    val newEpisodeAlerts: Flow<Boolean>

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setNewEpisodeAlerts(enabled: Boolean)
}
