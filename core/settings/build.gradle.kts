// This phone's choices (DataStore): skip lengths, auto-download, alerts.
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.settings"
}

dependencies {
    api(projects.core.model)
}
