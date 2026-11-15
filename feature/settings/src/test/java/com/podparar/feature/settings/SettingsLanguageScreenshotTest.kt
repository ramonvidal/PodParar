package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.AppLanguage
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Settings screen with the Language card (language plumbing). A tall screen so the card is in the picture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsLanguageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, language: AppLanguage, dark: Boolean) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
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
                        onLanguageChange = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun baianes() = shoot("settings-language-baianes", AppLanguage.BAIANES, dark = false)

    @Test
    fun baianesDark() = shoot("settings-language-baianes-dark", AppLanguage.BAIANES, dark = true)

    @Test
    fun automatic() = shoot("settings-language-automatic", AppLanguage.SYSTEM, dark = false)

    @Test
    fun automaticDark() = shoot("settings-language-automatic-dark", AppLanguage.SYSTEM, dark = true)
}
