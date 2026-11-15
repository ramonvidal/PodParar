package com.podparar.core.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podparar.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppLocaleTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun mirror(value: String?) {
        val editor = app.getSharedPreferences("language", Context.MODE_PRIVATE).edit()
        if (value == null) editor.clear() else editor.putString("app_language", value)
        editor.commit()
    }

    @Test
    fun `English wraps to en`() {
        val locale = AppLocale.wrap(app, AppLanguage.ENGLISH).resources.configuration.locales[0]
        assertEquals("en", locale.language)
    }

    @Test
    fun `Baianes wraps to pt-BR with the baianes variant`() {
        val locale = AppLocale.wrap(app, AppLanguage.BAIANES).resources.configuration.locales[0]
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.country)
        assertEquals("baianes", locale.variant)
    }

    @Test
    fun `without an explicit language it reads the mirror`() {
        mirror("BAIANES")
        assertEquals("pt", AppLocale.wrap(app).resources.configuration.locales[0].language)
        mirror("ENGLISH")
        assertEquals("en", AppLocale.wrap(app).resources.configuration.locales[0].language)
    }

    @Test
    fun `an unset or unknown mirror falls back to automatic and never throws`() {
        mirror(null)
        assertNotNull(AppLocale.wrap(app))
        mirror("KLINGON")
        assertNotNull(AppLocale.wrap(app))
        assertNotNull(AppLocale.wrap(app, AppLanguage.SYSTEM))
    }
}
