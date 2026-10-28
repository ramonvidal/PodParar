// Updating the app itself from the project's GitHub release page.
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.update"
}

dependencies {
    api(projects.core.model)
}
