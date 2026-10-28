plugins {
    id("podparar.android.feature")
}

android {
    namespace = "com.podparar.feature.player"
}

dependencies {
    implementation(projects.core.playback)
}
