package com.podparar.core.settings

import com.podparar.core.model.AppLanguage
import kotlinx.coroutines.flow.Flow

/** The in-app language choice. */
interface LanguageSettings {
    /** The chosen language; [AppLanguage.SYSTEM] until set. */
    val language: Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}
