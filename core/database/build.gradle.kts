// Room: tables, DAOs, migrations. Room types stay inside this module.
plugins {
    id("podparar.android.library")
    id("podparar.android.hilt")
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.podparar.core.database"
    // Robolectric tests run against an in-memory database and need the merged resources.
    testOptions.unitTests.isIncludeAndroidResources = true
    // MigrationTestHelper reads the exported schemas as test assets.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

room {
    // Exported schemas are committed, so every schema change shows up in review and
    // migration tests can check old versions.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
