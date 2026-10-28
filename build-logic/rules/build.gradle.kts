// Kept apart from :convention so it has no Android dependencies and can be tested anywhere.
plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("moduleRules") {
            id = "podparar.module-rules"
            implementationClass = "com.podparar.buildlogic.rules.ModuleRulesPlugin"
        }
    }
}
