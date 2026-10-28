// Wires the features together; the only module that may depend on features.
plugins {
    id("podparar.android.application")
    id("podparar.android.compose")
    id("podparar.android.hilt")
}

android {
    namespace = "com.podparar"

    // Every CI build gets a higher number, so a new APK always installs over the old one.
    val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").map(String::toInt).getOrElse(1)

    defaultConfig {
        applicationId = "com.podparar"
        versionCode = buildNumber
        versionName = "0.$buildNumber"
    }

    buildTypes {
        release {
            // Same key as debug builds, so a release installs over a test build (and the
            // other way round) without uninstalling.
            signingConfig = signingConfigs.getByName("debug")
            // Shrinking would make the APK smaller, but can break code that's only found at
            // runtime; worth turning on once there are UI tests on a real device.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.ui)
    // Schedules the background refresh and gives WorkManager Hilt's worker factory.
    implementation(projects.core.data)
    implementation(projects.core.downloads)
    implementation(projects.feature.home)
    implementation(projects.feature.library)
    implementation(projects.feature.discover)
    implementation(projects.feature.player)
    implementation(projects.feature.settings)
    implementation(projects.feature.update)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
}
