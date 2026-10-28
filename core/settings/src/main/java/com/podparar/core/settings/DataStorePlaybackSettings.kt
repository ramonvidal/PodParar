package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import com.podparar.core.model.PlaybackSpeeds
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStorePlaybackSettings(
    private val dataStore: DataStore<Preferences>,
) : PlaybackSettings {

    override val speed: Flow<Float> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[SPEED]?.let(PlaybackSpeeds::clamp) ?: PlaybackSpeeds.DEFAULT }
        .distinctUntilChanged()

    override suspend fun setSpeed(speed: Float) {
        val clamped = PlaybackSpeeds.clamp(speed)
        dataStore.edit { prefs -> prefs[SPEED] = clamped }
    }

    private companion object {
        val SPEED = floatPreferencesKey("playback_speed")
    }
}
