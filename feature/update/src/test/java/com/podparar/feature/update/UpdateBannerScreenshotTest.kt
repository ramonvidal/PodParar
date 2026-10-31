package com.podparar.feature.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.podparar.core.model.AppUpdate
import com.podparar.core.ui.theme.PodPararTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The update banner offering a new build and while downloading it, light and dark; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class UpdateBannerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val update = AppUpdate(
        buildNumber = 25,
        versionName = "0.25",
        downloadUrl = "https://github.com/ramonvidal/PodParar/releases/download/v0.25/Escutae.apk",
        sizeBytes = 12_345_678L,
    )

    private fun shoot(name: String, progress: Float?, dark: Boolean = false) {
        compose.setContent {
            PodPararTheme(darkTheme = dark) {
                Surface {
                    UpdateBannerContent(
                        update = update,
                        progress = progress,
                        onInstall = {},
                        onLater = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun offering() = shoot("update-banner-offering", progress = null)

    @Test
    fun offeringDark() = shoot("update-banner-offering-dark", progress = null, dark = true)

    @Test
    fun downloading() = shoot("update-banner-downloading", progress = 0.4f)

    @Test
    fun downloadingDark() = shoot("update-banner-downloading-dark", progress = 0.4f, dark = true)
}
