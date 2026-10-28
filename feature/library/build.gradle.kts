plugins {
    id("podparar.android.feature")
    id("podparar.android.compose.tests")
}

android {
    namespace = "com.podparar.feature.library"
}

dependencies {
    implementation(projects.core.playback)
    implementation(projects.core.downloads)
}
