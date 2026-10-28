plugins {
    id("podparar.android.feature")
    id("podparar.android.compose.tests")
}

android {
    namespace = "com.podparar.feature.settings"
}

dependencies {
    implementation(projects.core.settings)
    implementation(projects.core.update)
    // OPML import and export use the system file pickers.
    implementation(libs.androidx.activity.compose)
}
