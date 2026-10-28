// Media3 MediaSessionService, the player connection the UI uses, sleep timer, speed.
// Media3 types stay inside this module; callers see :core:model types only.
plugins {
    id("podparar.android.library")
    id("podparar.android.hilt")
}

android {
    namespace = "com.podparar.core.playback"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.settings)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // ListenableFuture.await() for the MediaController connection.
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
