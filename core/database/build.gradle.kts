// Room: tables, DAOs, migrations. Room types stay inside this module.
plugins {
    id("podparar.android.library")
}

android {
    namespace = "com.podparar.core.database"
}

dependencies {
    api(projects.core.model)
}
