plugins {
    id("podparar.android.feature")
    id("podparar.android.compose.tests")
}

android {
    namespace = "com.podparar.feature.home"
}

dependencies {
    implementation(projects.core.playback)
    implementation(projects.core.downloads)
    implementation(libs.reorderable)
}
