package com.podparar.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.AppLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The in-app language on a real preferences file, plus its synchronous SharedPreferences mirror. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreLanguageSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun newDataStore(): DataStore<Preferences> {
        val job = SupervisorJob()
        jobs += job
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(tmp.root, "t.preferences_pb") },
        )
    }

    private fun newSettings(dataStore: DataStore<Preferences> = newDataStore()): LanguageSettings =
        DataStoreLanguageSettings(dataStore, context)

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `default is SYSTEM`() = runTest {
        assertEquals(AppLanguage.SYSTEM, newSettings().language.first())
    }

    @Test
    fun `set and read back across instances`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).setLanguage(AppLanguage.BAIANES)
        assertEquals(AppLanguage.BAIANES, newSettings(dataStore).language.first())
        newSettings(dataStore).setLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, newSettings(dataStore).language.first())
        newSettings(dataStore).setLanguage(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, newSettings(dataStore).language.first())
    }

    @Test
    fun `the raw key stores the enum name`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).setLanguage(AppLanguage.BAIANES)
        val raw = dataStore.data.first()[stringPreferencesKey("app_language")]
        assertEquals("BAIANES", raw)
    }

    @Test
    fun `an unknown stored value reads as SYSTEM`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("app_language")] = "KLINGON" }
        assertEquals(AppLanguage.SYSTEM, newSettings(dataStore).language.first())
    }

    @Test
    fun `setLanguage also writes the SharedPreferences mirror`() = runTest {
        val settings = newSettings()
        settings.setLanguage(AppLanguage.BAIANES)
        val prefs = context.getSharedPreferences("language", Context.MODE_PRIVATE)
        assertEquals("BAIANES", prefs.getString("app_language", null))
        settings.setLanguage(AppLanguage.ENGLISH)
        assertEquals("ENGLISH", prefs.getString("app_language", null))
    }
}
