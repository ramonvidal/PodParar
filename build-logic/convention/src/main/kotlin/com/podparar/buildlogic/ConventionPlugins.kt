package com.podparar.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** The :app module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = TARGET_SDK
        }
    }
}

/** Any Android library module. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> { configureKotlinAndroid(this) }
        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }
}

/** Jetpack Compose with the shared BOM. Apply after the application or library plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        @Suppress("UNCHECKED_CAST")
        (extensions.getByName("android") as CommonExtension<*, *, *, *, *, *>).buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.library("androidx.compose.bom"))
            add("implementation", bom)
            add("implementation", libs.library("androidx.compose.ui"))
            add("implementation", libs.library("androidx.compose.ui.graphics"))
            add("implementation", libs.library("androidx.compose.ui.tooling.preview"))
            add("implementation", libs.library("androidx.compose.material3"))
            add("implementation", libs.library("androidx.compose.material.icons.core"))
            add("debugImplementation", libs.library("androidx.compose.ui.tooling"))
        }
    }
}

/** Hilt dependency injection. Apply after the application or library plugin. */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.library("hilt.android"))
            add("ksp", libs.library("hilt.compiler"))
        }
    }
}

/**
 * A screen or group of screens. Features see the core modules and nothing else:
 * see CLAUDE.md, "Module structure".
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("podparar.android.library")
        pluginManager.apply("podparar.android.compose")
        pluginManager.apply("podparar.android.hilt")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.library("androidx.lifecycle.runtime.compose"))
            add("implementation", libs.library("androidx.lifecycle.viewmodel.compose"))
            add("implementation", libs.library("androidx.navigation.compose"))
            add("implementation", libs.library("androidx.hilt.navigation.compose"))
            add("testImplementation", libs.library("kotlinx.coroutines.test"))
        }
    }
}

/**
 * UI tests for composables that run on the plain JVM (Robolectric), so CI checks screens
 * on every push without an emulator. Apply after the feature or library plugin.
 */
class AndroidComposeTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure<LibraryExtension> {
            testOptions.unitTests.isIncludeAndroidResources = true
            // Real drawing, so tests can save screenshots (Roborazzi) to build/outputs/roborazzi.
            testOptions.unitTests.all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("roborazzi.test.record", "true")
                // Declared, so a test run restored from the build cache brings its pictures back
                // too, and every release has every screen's screenshots.
                it.outputs.dir(layout.buildDirectory.dir("outputs/roborazzi"))
            }
        }
        dependencies {
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("roborazzi"))
            add("testImplementation", libs.library("roborazzi.compose"))
            add("testImplementation", libs.library("androidx.test.ext.junit"))
            add("testImplementation", platform(libs.library("androidx.compose.bom")))
            add("testImplementation", libs.library("androidx.compose.ui.test.junit4"))
            add("debugImplementation", libs.library("androidx.compose.ui.test.manifest"))
        }
    }
}

/** Plain Kotlin, no Android: domain models and rules. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }
}
