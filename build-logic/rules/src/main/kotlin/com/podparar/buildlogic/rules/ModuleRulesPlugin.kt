package com.podparar.buildlogic.rules

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/** Applied to the root project; checks [ModuleRules] once all modules are configured. */
class ModuleRulesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "podparar.module-rules belongs in the root build.gradle.kts" }
        target.gradle.projectsEvaluated {
            val modules = target.subprojects.map { project ->
                ModuleRules.Module(
                    path = project.path,
                    usesAndroid = ANDROID_PLUGINS.any(project.plugins::hasPlugin),
                    dependsOn = project.configurations
                        .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                        .map { it.path }
                        .toSet(),
                )
            }
            val violations = ModuleRules.violations(modules)
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Module structure broken (see CLAUDE.md, \"Module structure\"):\n" +
                        violations.joinToString("\n") { "  - $it" },
                )
            }
        }
    }

    private companion object {
        val ANDROID_PLUGINS = listOf("com.android.application", "com.android.library")
    }
}
