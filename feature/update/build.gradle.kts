plugins {
    id("podparar.android.feature")
}

android {
    namespace = "com.podparar.feature.update"
}

dependencies {
    implementation(projects.core.update)
}
