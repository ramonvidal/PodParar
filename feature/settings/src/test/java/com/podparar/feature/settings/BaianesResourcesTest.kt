package com.podparar.feature.settings

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** The Baianês resources (values-b+pt+BR+baianes) are picked for the pt-BR-baianes locale. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BaianesResourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun localized(tag: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config)
    }

    private val baianes get() = localized("pt-BR-baianes")

    @Test
    fun settingsLanguageIsBaianes() {
        assertEquals("Idioma", baianes.getString(R.string.settings_language))
        assertNotEquals(
            context.getString(R.string.settings_language),
            baianes.getString(R.string.settings_language),
        )
    }

    @Test
    fun settingsPlaybackIsBaianes() {
        assertEquals("Reprodução", baianes.getString(R.string.settings_playback))
        assertNotEquals(
            context.getString(R.string.settings_playback),
            baianes.getString(R.string.settings_playback),
        )
    }

    @Test
    fun settingsSkipSilenceIsBaianes() {
        assertEquals("Pular silêncio", baianes.getString(R.string.settings_skip_silence))
        assertNotEquals(
            context.getString(R.string.settings_skip_silence),
            baianes.getString(R.string.settings_skip_silence),
        )
    }

    @Test
    fun settingsWifiOnlyIsBaianes() {
        assertEquals("Só no Wi-Fi", baianes.getString(R.string.settings_wifi_only))
    }

    @Test
    fun defaultLocaleKeepsEnglish() {
        val english = localized("en")
        assertEquals("Language", english.getString(R.string.settings_language))
        assertEquals("Playback", english.getString(R.string.settings_playback))
        assertEquals("Skip silence", english.getString(R.string.settings_skip_silence))
        assertEquals("Wi-Fi only", english.getString(R.string.settings_wifi_only))
    }
}
