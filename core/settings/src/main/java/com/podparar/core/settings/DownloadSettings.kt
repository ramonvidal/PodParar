package com.podparar.core.settings

import com.podparar.core.model.DownloadLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** This phone's download and background-refresh choices. */
interface DownloadSettings {
    /** Download episodes only on unmetered networks; true until set. */
    val wifiOnly: Flow<Boolean>

    /** Post a notification when a background refresh finds new episodes; true until set. */
    val newEpisodeAlerts: Flow<Boolean>

    /** The most episodes kept downloaded at once; DEFAULT until set. */
    val maxDownloads: Flow<Int> get() = flowOf(DownloadLimit.DEFAULT)

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setNewEpisodeAlerts(enabled: Boolean)

    suspend fun setMaxDownloads(value: Int) {}
}
