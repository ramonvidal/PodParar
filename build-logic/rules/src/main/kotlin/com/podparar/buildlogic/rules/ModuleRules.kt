package com.podparar.buildlogic.rules

/**
 * The module structure from CLAUDE.md, as code. Any build fails when it's broken.
 *
 * - :app wires everything together; nothing depends on it.
 * - :feature:* modules depend only on :core:* modules, never on each other.
 * - :core:* modules never depend on features, and only on the core modules listed here.
 * - Modules in [PURE_KOTLIN] must not use Android, so their logic stays plain and testable.
 */
object ModuleRules {
    const val APP = ":app"
    private const val FEATURE_PREFIX = ":feature:"

    /** Every core module, with the core modules it may depend on. New ones must be added here on purpose. */
    val CORE_DEPENDENCIES: Map<String, Set<String>> = mapOf(
        // Models and rules in plain Kotlin: podcasts, episodes, played/resume rules, parsing, OPML.
        ":core:model" to emptySet(),
        // Room: tables, DAOs, migrations.
        ":core:database" to setOf(":core:model"),
        // OkHttp: feed download and RSS parsing, Apple Podcasts search.
        ":core:network" to setOf(":core:model"),
        // Repositories joining network and database.
        ":core:data" to setOf(":core:model", ":core:database", ":core:network"),
        // Media3 service, the player connection the UI uses, sleep timer, speed.
        ":core:playback" to setOf(":core:model", ":core:data", ":core:settings"),
        // Episode downloads and storage clean-up (WorkManager).
        ":core:downloads" to setOf(":core:model", ":core:data", ":core:settings"),
        // This phone's choices (DataStore).
        ":core:settings" to setOf(":core:model"),
        // Theme, shared composables, strings used by more than one feature.
        ":core:ui" to setOf(":core:model"),
        // Updating the app itself from the project's release page.
        ":core:update" to setOf(":core:model"),
    )

    val PURE_KOTLIN: Set<String> = setOf(":core:model")

    data class Module(val path: String, val usesAndroid: Boolean, val dependsOn: Set<String>)

    fun violations(modules: List<Module>): List<String> = modules.flatMap { module ->
        listOfNotNull(placementViolation(module), androidViolation(module)) +
            module.dependsOn.filter { it != module.path }.mapNotNull { dependencyViolation(module.path, it) }
    }

    private fun isFeature(path: String) = path.startsWith(FEATURE_PREFIX) && path.count { it == ':' } == 2
    private fun isCore(path: String) = path in CORE_DEPENDENCIES

    private fun placementViolation(module: Module): String? = when {
        module.path == APP || isFeature(module.path) || isCore(module.path) -> null
        module.path.startsWith(":core:") ->
            "${module.path} is a new core module: add it to ModuleRules.CORE_DEPENDENCIES with what it may depend on"
        // Intermediate folders such as :core and :feature are fine as long as they hold no code.
        module.path == ":core" || module.path == ":feature" -> null
        else -> "${module.path} is outside the structure: modules go under :core:<name> or :feature:<name>"
    }

    private fun androidViolation(module: Module): String? =
        if (module.path in PURE_KOTLIN && module.usesAndroid) {
            "${module.path} must stay plain Kotlin: move Android code to another :core module or a feature"
        } else {
            null
        }

    private fun dependencyViolation(from: String, to: String): String? {
        val reason = when {
            to == APP -> "nothing may depend on $APP"
            from == APP -> null
            isFeature(from) && isFeature(to) -> "features must not depend on each other; move what they share to a :core module"
            isFeature(from) && isCore(to) -> null
            isCore(from) && isFeature(to) -> "core modules must not depend on features"
            isCore(from) && isCore(to) ->
                if (to in CORE_DEPENDENCIES.getValue(from)) null
                else "$from may only depend on ${CORE_DEPENDENCIES.getValue(from).ifEmpty { setOf("nothing") }.joinToString()}"
            else -> null // Placement violations are reported separately.
        }
        return reason?.let { "$from -> $to: $it" }
    }
}
