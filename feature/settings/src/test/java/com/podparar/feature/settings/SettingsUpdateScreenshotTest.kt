package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Settings screen's "Check for updates" row after a check: up to date, and a new build found.
 * A tall screen so the About card at the bottom is in the picture without scrolling.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsUpdateScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, updateCheck: UpdateCheck, dark: Boolean = false) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = SettingsUiState(appVersion = "0.24", updateCheck = updateCheck),
                        onBack = {},
                        onSkipBackChange = {},
                        onSkipForwardChange = {},
                        onSkipSilenceChange = {},
                        onShakeToExtendChange = {},
                        onWifiOnlyChange = {},
                        onNewEpisodeAlertsChange = {},
                        onImportOpml = {},
                        onExportOpml = {},
                        onCheckForUpdates = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun upToDate() = shoot("settings-update-up-to-date", UpdateCheck.UpToDate)

    @Test
    fun upToDateDark() = shoot("settings-update-up-to-date-dark", UpdateCheck.UpToDate, dark = true)

    @Test
    fun found() = shoot("settings-update-found", UpdateCheck.Found("0.25"))

    @Test
    fun foundDark() = shoot("settings-update-found-dark", UpdateCheck.Found("0.25"), dark = true)
}
