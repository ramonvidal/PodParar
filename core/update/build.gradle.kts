// Updating the app itself from the project's GitHub release page.
plugins {
    id("podparar.android.library")
    id("podparar.android.hilt")
}

android {
    namespace = "com.podparar.core.update"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
