package com.podparar.buildlogic.rules

import com.podparar.buildlogic.rules.ModuleRules.Module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRulesTest {
    private fun module(path: String, vararg dependsOn: String, usesAndroid: Boolean = path != ":core:model") =
        Module(path, usesAndroid, dependsOn.toSet())

    private val valid = listOf(
        module(":core"),
        module(":feature"),
        module(":core:model"),
        module(":core:database", ":core:model"),
        module(":core:network", ":core:model"),
        module(":core:data", ":core:model", ":core:database", ":core:network"),
        module(":core:settings", ":core:model"),
        module(":core:playback", ":core:model", ":core:data", ":core:settings"),
        module(":core:downloads", ":core:model", ":core:data", ":core:settings"),
        module(":core:ui", ":core:model"),
        module(":core:update", ":core:model"),
        module(":feature:home", ":core:model", ":core:data", ":core:ui", ":core:playback"),
        module(":feature:library", ":core:model", ":core:data", ":core:ui"),
        module(":app", ":core:ui", ":core:playback", ":feature:home", ":feature:library"),
    )

    private fun violationsWith(vararg extra: Module) =
        ModuleRules.violations(valid.filter { m -> extra.none { it.path == m.path } } + extra)

    @Test
    fun `the intended structure passes`() {
        assertEquals(emptyList<String>(), ModuleRules.violations(valid))
    }

    @Test
    fun `every core module in CLAUDE md is listed`() {
        assertEquals(
            setOf(
                ":core:model", ":core:database", ":core:network", ":core:data", ":core:playback",
                ":core:downloads", ":core:settings", ":core:ui", ":core:update",
            ),
            ModuleRules.CORE_DEPENDENCIES.keys,
        )
    }

    @Test
    fun `features cannot depend on each other`() {
        val violations = violationsWith(module(":feature:home", ":core:ui", ":feature:library"))
        assertEquals(1, violations.size)
        assertTrue(violations.single().startsWith(":feature:home -> :feature:library"))
    }

    @Test
    fun `nothing depends on app`() {
        assertTrue(violationsWith(module(":feature:home", ":app")).single().contains("nothing may depend on :app"))
        assertTrue(violationsWith(module(":core:data", ":core:model", ":app")).single().contains("nothing may depend on :app"))
    }

    @Test
    fun `core cannot depend on features`() {
        val violations = violationsWith(module(":core:data", ":core:model", ":feature:library"))
        assertTrue(violations.single().contains("core modules must not depend on features"))
    }

    @Test
    fun `core modules only depend on the core modules listed for them`() {
        // The UI and the database never reach for the network directly.
        assertEquals(1, violationsWith(module(":core:ui", ":core:model", ":core:data")).size)
        assertEquals(1, violationsWith(module(":core:database", ":core:model", ":core:network")).size)
        // Network and database stay independent of each other and of the repositories above them.
        assertEquals(1, violationsWith(module(":core:network", ":core:model", ":core:data")).size)
        // Playback and downloads sit side by side; neither uses the other.
        assertEquals(1, violationsWith(module(":core:playback", ":core:model", ":core:downloads")).size)
        // Settings are read by others but read nothing themselves.
        assertEquals(1, violationsWith(module(":core:settings", ":core:model", ":core:data")).size)
        // The model depends on nothing.
        assertTrue(
            violationsWith(module(":core:model", ":core:ui", usesAndroid = false)).single()
                .contains("may only depend on nothing"),
        )
    }

    @Test
    fun `the model module must stay plain Kotlin`() {
        assertTrue(violationsWith(module(":core:model", usesAndroid = true)).single().contains("plain Kotlin"))
    }

    @Test
    fun `new modules must fit the structure`() {
        assertTrue(violationsWith(module(":core:notifications")).single().contains("CORE_DEPENDENCIES"))
        assertTrue(violationsWith(module(":utils")).single().contains("outside the structure"))
        assertTrue(violationsWith(module(":feature:home:extra")).single().contains("outside the structure"))
    }
}
