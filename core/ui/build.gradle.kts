// Theme, shared composables and strings used by more than one feature.
plugins {
    id("podparar.android.library")
    id("podparar.android.compose")
}

android {
    namespace = "com.podparar.core.ui"
}

dependencies {
    implementation(projects.core.model)
    // Show artwork; loaded over OkHttp, the same client library :core:network uses.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
