package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.AppLanguage
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Language card: a heading and three single-choice rows that report the choice. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsLanguageTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<AppLanguage>()

    private fun render(language: AppLanguage = AppLanguage.SYSTEM) {
        chosen.clear()
        compose.setContent {
            PodPararTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = SettingsUiState(language = language, appVersion = "0.40"),
                        onSkipBackChange = {},
                        onSkipForwardChange = {},
                        onSkipSilenceChange = {},
                        onShakeToExtendChange = {},
                        onWifiOnlyChange = {},
                        onNewEpisodeAlertsChange = {},
                        onImportOpml = {},
                        onExportOpml = {},
                        modifier = Modifier.fillMaxSize(),
                        onLanguageChange = { chosen += it },
                    )
                }
            }
        }
    }

    @Test
    fun showsHeadingAndThreeRows() {
        render()
        listOf("Language", "Automatic", "English", "Baianês").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun clickingEnglishReportsEnglish() {
        render()
        compose.onNodeWithText("English").performClick()
        assertEquals(listOf(AppLanguage.ENGLISH), chosen)
    }

    @Test
    fun clickingBaianesReportsBaianes() {
        render()
        compose.onNodeWithText("Baianês").performClick()
        assertEquals(listOf(AppLanguage.BAIANES), chosen)
    }

    @Test
    fun clickingAutomaticReportsSystem() {
        render(AppLanguage.ENGLISH)
        compose.onNodeWithText("Automatic").performClick()
        assertEquals(listOf(AppLanguage.SYSTEM), chosen)
    }
}
