// Updating the app itself from the project's GitHub release page.
plugins {
    id("podparar.android.library")
    id("podparar.android.hilt")
}

android {
    namespace = "com.podparar.core.update"
    // Robolectric tests need the merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
