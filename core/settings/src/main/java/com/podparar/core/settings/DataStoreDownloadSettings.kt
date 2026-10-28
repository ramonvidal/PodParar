package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreDownloadSettings(
    private val dataStore: DataStore<Preferences>,
) : DownloadSettings {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val wifiOnly: Flow<Boolean> = preferences
        .map { prefs -> prefs[WIFI_ONLY] ?: true }
        .distinctUntilChanged()

    override val newEpisodeAlerts: Flow<Boolean> = preferences
        .map { prefs -> prefs[NEW_EPISODE_ALERTS] ?: true }
        .distinctUntilChanged()

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[WIFI_ONLY] = enabled }
    }

    override suspend fun setNewEpisodeAlerts(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[NEW_EPISODE_ALERTS] = enabled }
    }

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("downloads_wifi_only")
        val NEW_EPISODE_ALERTS = booleanPreferencesKey("new_episode_alerts")
    }
}
