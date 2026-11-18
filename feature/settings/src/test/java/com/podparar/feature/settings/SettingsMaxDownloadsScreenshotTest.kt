package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Downloaded episodes limit row, and the dialog it opens. A tall screen so the Downloads card is in the picture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsMaxDownloadsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(dark: Boolean, maxDownloads: Int) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = SettingsUiState(maxDownloads = maxDownloads, appVersion = "0.40"),
                        onSkipBackChange = {},
                        onSkipForwardChange = {},
                        onSkipSilenceChange = {},
                        onShakeToExtendChange = {},
                        onWifiOnlyChange = {},
                        onNewEpisodeAlertsChange = {},
                        onImportOpml = {},
                        onExportOpml = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun dialogOpen() {
        render(dark = false, maxDownloads = 25)
        compose.onNodeWithText("Downloaded episodes limit").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-max-downloads-dialog.png")
    }

    @Test
    fun rowDark() {
        render(dark = true, maxDownloads = 20)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-max-downloads-dark.png")
    }
}
