// Episode downloads, background feed refresh, new-episode alerts and storage clean-up (WorkManager).
plugins {
    id("podparar.android.library")
    id("podparar.android.hilt")
}

android {
    namespace = "com.podparar.core.downloads"
    // Robolectric tests need the merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    // Generates the factory for the @HiltWorker workers.
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
}
