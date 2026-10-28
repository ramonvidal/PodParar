// Episode downloads and storage clean-up (WorkManager).
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.downloads"
}

dependencies {
    api(projects.core.model)
}
