// OkHttp: feed download and RSS parsing, Apple Podcasts search. OkHttp stays inside this module.
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.network"
}

dependencies {
    api(projects.core.model)
}
