package com.podparar.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.podparar.core.model.PlaybackSpeeds
import com.podparar.core.model.SkipLengths
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStorePlaybackSettings(
    private val dataStore: DataStore<Preferences>,
) : PlaybackSettings {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val speed: Flow<Float> = preferences
        .map { prefs -> prefs[SPEED]?.let(PlaybackSpeeds::clamp) ?: PlaybackSpeeds.DEFAULT }
        .distinctUntilChanged()

    override val skipSilence: Flow<Boolean> = preferences
        .map { prefs -> prefs[SKIP_SILENCE] ?: false }
        .distinctUntilChanged()

    override val skipBackMs: Flow<Long> = preferences
        .map { prefs -> prefs[SKIP_BACK_MS]?.let(SkipLengths::nearestBack) ?: SkipLengths.DEFAULT_BACK_MS }
        .distinctUntilChanged()

    override val skipForwardMs: Flow<Long> = preferences
        .map { prefs -> prefs[SKIP_FORWARD_MS]?.let(SkipLengths::nearestForward) ?: SkipLengths.DEFAULT_FORWARD_MS }
        .distinctUntilChanged()

    override val shakeToExtend: Flow<Boolean> = preferences
        .map { prefs -> prefs[SHAKE_TO_EXTEND] ?: true }
        .distinctUntilChanged()

    override suspend fun setSpeed(speed: Float) {
        val clamped = PlaybackSpeeds.clamp(speed)
        dataStore.edit { prefs -> prefs[SPEED] = clamped }
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SKIP_SILENCE] = enabled }
    }

    override suspend fun setSkipBackMs(ms: Long) {
        val nearest = SkipLengths.nearestBack(ms)
        dataStore.edit { prefs -> prefs[SKIP_BACK_MS] = nearest }
    }

    override suspend fun setSkipForwardMs(ms: Long) {
        val nearest = SkipLengths.nearestForward(ms)
        dataStore.edit { prefs -> prefs[SKIP_FORWARD_MS] = nearest }
    }

    override suspend fun setShakeToExtend(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SHAKE_TO_EXTEND] = enabled }
    }

    private companion object {
        val SPEED = floatPreferencesKey("playback_speed")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val SKIP_BACK_MS = longPreferencesKey("skip_back_ms")
        val SKIP_FORWARD_MS = longPreferencesKey("skip_forward_ms")
        val SHAKE_TO_EXTEND = booleanPreferencesKey("shake_to_extend")
    }
}
