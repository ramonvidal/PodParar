plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "podparar.android.application"
            implementationClass = "com.podparar.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "podparar.android.library"
            implementationClass = "com.podparar.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "podparar.android.compose"
            implementationClass = "com.podparar.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "podparar.android.hilt"
            implementationClass = "com.podparar.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "podparar.android.feature"
            implementationClass = "com.podparar.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidComposeTests") {
            id = "podparar.android.compose.tests"
            implementationClass = "com.podparar.buildlogic.AndroidComposeTestsConventionPlugin"
        }
        register("jvmLibrary") {
            id = "podparar.jvm.library"
            implementationClass = "com.podparar.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
