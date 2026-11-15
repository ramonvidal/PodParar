package com.podparar.core.settings

import android.content.Context
import android.content.res.Resources
import com.podparar.core.model.AppLanguage
import com.podparar.core.model.AppLanguages
import java.util.Locale

/** Applies the in-app language to a [Context], before DataStore can be read. */
object AppLocale {
    internal const val KEY = "app_language"
    private const val FILE = "language"

    /**
     * Wraps [base] so its resources use [language] (default: the mirrored choice, SYSTEM if unset).
     * Never throws; on any failure returns [base].
     */
    fun wrap(base: Context, language: AppLanguage? = null): Context = try {
        val choice = language ?: readMirror(base)
        val deviceLanguage = Resources.getSystem().configuration.locales[0].language
        val resolved = AppLanguages.resolve(choice, deviceLanguage)
        val tag = AppLanguages.localeTag(resolved)
        if (tag == null) {
            base
        } else {
            val locale = Locale.forLanguageTag(tag)
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocales(android.os.LocaleList(locale))
            base.createConfigurationContext(config)
        }
    } catch (e: Exception) {
        base
    }

    internal fun parse(name: String?): AppLanguage =
        AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.SYSTEM

    internal fun mirror(context: Context, language: AppLanguage) {
        try {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, language.name).commit()
        } catch (e: Exception) {
            // The DataStore copy is still written; the next launch just may not pick the language up early.
        }
    }

    private fun readMirror(context: Context): AppLanguage =
        parse(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))
}
