// This phone's choices (DataStore): skip lengths, auto-download, alerts.
plugins {
    id("podparar.android.library")
    id("podparar.android.hilt")
}

android {
    namespace = "com.podparar.core.settings"
    // Robolectric tests need the merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
