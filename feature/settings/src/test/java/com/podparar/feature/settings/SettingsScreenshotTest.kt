package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Settings screen: defaults and changed values, light, dark and large text; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: SettingsUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        SettingsContent(
                            state = state,
                            onBack = {},
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
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // Skip back 10 s, skip forward 30 s, skip silence off, shake on, Wi-Fi only on, alerts on.
    private val defaults = SettingsUiState(appVersion = "0.20")

    // Skip 5 s back / 10 s forward, skip silence on, Wi-Fi only off, and the result of an OPML import.
    private val changed = SettingsUiState(
        skipBackMs = 5_000L,
        skipForwardMs = 10_000L,
        skipSilence = true,
        wifiOnly = false,
        opmlMessage = "Added 12 shows, 3 already followed, 1 failed",
        appVersion = "0.20",
    )

    @Test
    fun defaults() = shoot("settings-defaults", defaults)

    @Test
    fun defaultsDark() = shoot("settings-defaults-dark", defaults, dark = true)

    @Test
    fun defaultsLargeText() = shoot("settings-defaults-large-text", defaults, fontScale = 2f)

    @Test
    fun changed() = shoot("settings-changed", changed)

    @Test
    fun changedDark() = shoot("settings-changed-dark", changed, dark = true)
}
