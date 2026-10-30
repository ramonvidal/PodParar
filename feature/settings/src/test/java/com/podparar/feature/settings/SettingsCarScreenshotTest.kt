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
 * The Settings screen with "Car and headphone buttons skip" turned off (Car phase).
 * A tall screen so the whole Playback card is in the picture without scrolling.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsCarScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = SettingsUiState(nextPreviousSkip = false, appVersion = "0.27"),
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
                        onNextPreviousSkipChange = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun nextPreviousSkipOff() = shoot("settings-car-next-previous-skip-off", dark = false)

    @Test
    fun nextPreviousSkipOffDark() = shoot("settings-car-next-previous-skip-off-dark", dark = true)
}
