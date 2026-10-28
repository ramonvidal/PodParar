// Media3 MediaSessionService, the player connection the UI uses, sleep timer, speed.
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.playback"
}

dependencies {
    api(projects.core.model)
}
