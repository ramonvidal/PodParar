package com.podparar.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.podparar.core.model.AppLanguage
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreLanguageSettings(
    private val dataStore: DataStore<Preferences>,
    private val context: Context,
) : LanguageSettings {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val language: Flow<AppLanguage> = preferences
        .map { prefs -> AppLocale.parse(prefs[APP_LANGUAGE]) }
        .distinctUntilChanged()

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[APP_LANGUAGE] = language.name }
        // Contexts are wrapped before DataStore can be read, so the choice is mirrored synchronously.
        AppLocale.mirror(context, language)
    }

    private companion object {
        val APP_LANGUAGE = stringPreferencesKey(AppLocale.KEY)
    }
}
