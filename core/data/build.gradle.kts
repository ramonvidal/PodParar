// Repositories joining network and database. Callers see :core:model types only.
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.data"
}

dependencies {
    // Repositories take and return model types, so callers get them too.
    api(projects.core.model)
}
