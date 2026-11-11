package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Settings is a bottom-bar tab: it has a title, no back arrow, and all its sections. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsTabTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render() {
        compose.setContent {
            PodPararTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = SettingsUiState(appVersion = "0.36"),
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
    fun showsTitle() {
        render()
        compose.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun hasNoBackArrow() {
        render()
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
    }

    @Test
    fun showsAllSections() {
        render()
        listOf("Playback", "Downloads", "Your shows", "About").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }
}
