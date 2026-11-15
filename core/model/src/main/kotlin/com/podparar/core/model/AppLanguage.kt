package com.podparar.core.model

/** The in-app language choice. ENGLISH is the default resources; BAIANES is the pt-BR "baianes" variant. */
enum class AppLanguage { SYSTEM, ENGLISH, BAIANES }

object AppLanguages {
    /**
     * Never returns SYSTEM: SYSTEM -> BAIANES when [deviceLanguage] (ISO 639-1, e.g. "pt") is "pt"
     * (case-insensitive), else ENGLISH; other values as given.
     */
    fun resolve(choice: AppLanguage, deviceLanguage: String): AppLanguage = when (choice) {
        AppLanguage.SYSTEM ->
            if (deviceLanguage.equals("pt", ignoreCase = true)) AppLanguage.BAIANES else AppLanguage.ENGLISH
        else -> choice
    }

    /** Locale tag used to load the resources: ENGLISH -> "en", BAIANES -> "pt-BR-baianes", SYSTEM -> null. */
    fun localeTag(language: AppLanguage): String? = when (language) {
        AppLanguage.ENGLISH -> "en"
        AppLanguage.BAIANES -> "pt-BR-baianes"
        AppLanguage.SYSTEM -> null
    }
}
