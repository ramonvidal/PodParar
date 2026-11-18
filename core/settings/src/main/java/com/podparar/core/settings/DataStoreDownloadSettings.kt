package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import com.podparar.core.model.DownloadLimit
import com.podparar.core.model.HomeLimit
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

    override val maxDownloads: Flow<Int> = preferences
        .map { prefs -> DownloadLimit.coerceFor(prefs[MAX_DOWNLOADS] ?: DownloadLimit.DEFAULT, homeLimit(prefs)) }
        .distinctUntilChanged()

    override val maxHomeEpisodes: Flow<Int> = preferences
        .map { prefs -> homeLimit(prefs) }
        .distinctUntilChanged()

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[WIFI_ONLY] = enabled }
    }

    override suspend fun setNewEpisodeAlerts(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[NEW_EPISODE_ALERTS] = enabled }
    }

    override suspend fun setMaxDownloads(value: Int) {
        dataStore.edit { prefs -> prefs[MAX_DOWNLOADS] = DownloadLimit.coerceFor(value, homeLimit(prefs)) }
    }

    /** Also lowers the stored max downloads in the same edit, so it stays below the home limit. */
    override suspend fun setMaxHomeEpisodes(value: Int) {
        dataStore.edit { prefs ->
            val home = HomeLimit.coerce(value)
            prefs[MAX_HOME_EPISODES] = home
            val downloads = prefs[MAX_DOWNLOADS] ?: DownloadLimit.DEFAULT
            prefs[MAX_DOWNLOADS] = DownloadLimit.coerceFor(downloads, home)
        }
    }

    private fun homeLimit(prefs: Preferences): Int =
        HomeLimit.coerce(prefs[MAX_HOME_EPISODES] ?: HomeLimit.DEFAULT)

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("downloads_wifi_only")
        val NEW_EPISODE_ALERTS = booleanPreferencesKey("new_episode_alerts")
        val MAX_DOWNLOADS = intPreferencesKey("max_downloads")
        val MAX_HOME_EPISODES = intPreferencesKey("max_home_episodes")
    }
}
