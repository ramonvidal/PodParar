pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "PodParar"

// See CLAUDE.md, "Module structure", for what may depend on what.
include(":app")
include(":core:model")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:playback")
include(":core:downloads")
include(":core:settings")
include(":core:ui")
include(":core:update")
include(":feature:home")
include(":feature:library")
include(":feature:discover")
include(":feature:player")
include(":feature:settings")
include(":feature:update")
