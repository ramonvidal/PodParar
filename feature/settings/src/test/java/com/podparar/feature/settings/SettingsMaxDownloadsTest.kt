package com.podparar.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "Downloaded episodes limit" row in Downloads and the dialog it opens. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsMaxDownloadsTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<Int>()

    private fun render(state: SettingsUiState = SettingsUiState(appVersion = "0.40")) {
        chosen.clear()
        compose.setContent {
            PodPararTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = state,
                        onSkipBackChange = {},
                        onSkipForwardChange = {},
                        onSkipSilenceChange = {},
                        onShakeToExtendChange = {},
                        onWifiOnlyChange = {},
                        onNewEpisodeAlertsChange = {},
                        onImportOpml = {},
                        onExportOpml = {},
                        modifier = Modifier.fillMaxSize(),
                        onMaxDownloadsChange = { chosen += it },
                    )
                }
            }
        }
    }

    private fun inDialog(text: String): SemanticsMatcher = hasText(text) and hasAnyAncestor(isDialog())

    private fun openDialog() {
        compose.onNodeWithText("Downloaded episodes limit").performClick()
    }

    @Test
    fun rowShowsTitleHintAndDefaultValue() {
        render()
        compose.onNodeWithText("Downloaded episodes limit").assertExists()
        compose.onNodeWithText(
            "The most episodes kept downloaded at once. At the limit, new downloads are blocked until you remove some. It is always lower than the Home limit.",
        ).assertExists()
        compose.onNodeWithText("20 episodes").assertExists()
    }

    @Test
    fun rowShowsCurrentValue() {
        render(SettingsUiState(maxDownloads = 50, maxHomeEpisodes = 500, appVersion = "0.40"))
        compose.onNodeWithText("50 episodes").assertExists()
    }

    @Test
    fun dialogIsClosedUntilRowIsTapped() {
        render()
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)
        compose.onAllNodesWithText("5 episodes").assertCountEquals(0)
    }

    @Test
    fun tappingRowListsOnlyOptionsBelowHomeLimit() {
        render()
        openDialog()
        listOf(5, 10, 20, 30).forEach {
            compose.onNode(inDialog("$it episodes")).assertExists()
        }
        listOf(50, 100, 200).forEach {
            compose.onAllNodes(inDialog("$it episodes")).assertCountEquals(0)
        }
        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun highHomeLimitListsAllSevenOptions() {
        render(SettingsUiState(maxHomeEpisodes = 500, appVersion = "0.40"))
        openDialog()
        listOf(5, 10, 20, 30, 50, 100, 200).forEach {
            compose.onNode(inDialog("$it episodes")).assertExists()
        }
    }

    @Test
    fun lowHomeLimitListsOnlyFiveTenTwenty() {
        render(SettingsUiState(maxHomeEpisodes = 30, appVersion = "0.40"))
        openDialog()
        listOf(5, 10, 20).forEach {
            compose.onNode(inDialog("$it episodes")).assertExists()
        }
        listOf(30, 50, 100, 200).forEach {
            compose.onAllNodes(inDialog("$it episodes")).assertCountEquals(0)
        }
    }

    @Test
    fun nonOptionCurrentValueAppearsInList() {
        render(SettingsUiState(maxDownloads = 25, appVersion = "0.40"))
        openDialog()
        compose.onNode(inDialog("25 episodes")).assertExists()
        listOf(5, 10, 20, 30).forEach {
            compose.onNode(inDialog("$it episodes")).assertExists()
        }
    }

    @Test
    fun optionCurrentValueIsNotDuplicated() {
        render(SettingsUiState(maxDownloads = 30, appVersion = "0.40"))
        openDialog()
        // One in the row behind the dialog, one in the dialog list.
        compose.onAllNodesWithText("30 episodes").assertCountEquals(2)
    }

    @Test
    fun choosingAnOptionReportsItAndClosesDialog() {
        render()
        openDialog()
        compose.onNode(inDialog("30 episodes")).performClick()
        assertEquals(listOf(30), chosen)
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)
    }

    @Test
    fun choosingNonOptionCurrentValueReportsIt() {
        render(SettingsUiState(maxDownloads = 25, appVersion = "0.40"))
        openDialog()
        compose.onNode(inDialog("25 episodes")).performClick()
        assertEquals(listOf(25), chosen)
    }

    @Test
    fun cancelClosesWithoutReporting() {
        render()
        openDialog()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Int>(), chosen)
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)
    }
}
