package com.podparar.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): MinimalExternalModuleDependency = findLibrary(alias).get().get()

internal const val COMPILE_SDK = 35
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 35

@Suppress("UnstableApiUsage")
internal fun Project.configureKotlinAndroid(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = COMPILE_SDK
        defaultConfig { minSdk = MIN_SDK }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlin(
        "kotlinx.coroutines.ExperimentalCoroutinesApi",
        "androidx.compose.material3.ExperimentalMaterial3Api",
        "androidx.compose.foundation.ExperimentalFoundationApi",
        "androidx.compose.foundation.layout.ExperimentalLayoutApi",
    )
}

internal fun Project.configureKotlin(vararg optIns: String) {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            optIn.addAll(*optIns)
        }
    }
}
