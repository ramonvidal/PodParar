plugins {
    id("podparar.android.feature")
}

android {
    namespace = "com.podparar.feature.settings"
}

dependencies {
    implementation(projects.core.settings)
}
