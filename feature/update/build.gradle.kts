plugins {
    id("podparar.android.feature")
    id("podparar.android.compose.tests")
}

android {
    namespace = "com.podparar.feature.update"
}

dependencies {
    implementation(projects.core.update)
}
