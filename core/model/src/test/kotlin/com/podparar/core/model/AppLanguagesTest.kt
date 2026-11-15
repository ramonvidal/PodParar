package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguagesTest {
    private val devices = listOf("pt", "PT", "Pt", "en", "es", "")

    @Test
    fun `resolve never returns SYSTEM`() {
        for (choice in AppLanguage.values()) {
            for (device in devices) {
                assertNotEquals("$choice on '$device'", AppLanguage.SYSTEM, AppLanguages.resolve(choice, device))
            }
        }
    }

    @Test
    fun `automatic is Baianes on Portuguese phones in any case`() {
        assertEquals(AppLanguage.BAIANES, AppLanguages.resolve(AppLanguage.SYSTEM, "pt"))
        assertEquals(AppLanguage.BAIANES, AppLanguages.resolve(AppLanguage.SYSTEM, "PT"))
        assertEquals(AppLanguage.BAIANES, AppLanguages.resolve(AppLanguage.SYSTEM, "Pt"))
    }

    @Test
    fun `automatic is English on other phones`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguages.resolve(AppLanguage.SYSTEM, "en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguages.resolve(AppLanguage.SYSTEM, "es"))
        assertEquals(AppLanguage.ENGLISH, AppLanguages.resolve(AppLanguage.SYSTEM, ""))
    }

    @Test
    fun `an explicit choice ignores the device language`() {
        for (device in devices) {
            assertEquals(AppLanguage.ENGLISH, AppLanguages.resolve(AppLanguage.ENGLISH, device))
            assertEquals(AppLanguage.BAIANES, AppLanguages.resolve(AppLanguage.BAIANES, device))
        }
    }

    @Test
    fun `locale tags`() {
        assertEquals("en", AppLanguages.localeTag(AppLanguage.ENGLISH))
        assertEquals("pt-BR-baianes", AppLanguages.localeTag(AppLanguage.BAIANES))
        assertNull(AppLanguages.localeTag(AppLanguage.SYSTEM))
    }
}
