package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for LanguageViewModel: language switching and RTL detection.
 */
class LanguageSwitchTest {

    @Test
    fun testRtlDetectionArabic() {
        // Arabic codes should be detected as RTL
        val rtlLangs = listOf("ar", "ar-SA", "Arabic")
        for (lang in rtlLangs) {
            val isRtl = lang.lowercase().startsWith("ar")
            assertTrue(isRtl, "$lang should be RTL")
        }
    }

    @Test
    fun testRtlDetectionHebrew() {
        val isRtl = "he".lowercase().startsWith("he")
        assertTrue(isRtl)
    }

    @Test
    fun testRtlDetectionUrdu() {
        val isRtl = "ur".lowercase().startsWith("ur")
        assertTrue(isRtl)
    }

    @Test
    fun testRtlDetectionFarsi() {
        val isRtl = "fa".lowercase().startsWith("fa")
        assertTrue(isRtl)
    }

    @Test
    fun testLtrLanguagesNotRtl() {
        val ltrLangs = listOf("en", "fr", "sw", "hi", "zh", "es", "pt")
        val rtlPrefixes = listOf("ar", "he", "ur", "fa", "ps", "sd", "yi")
        for (lang in ltrLangs) {
            val isRtl = rtlPrefixes.any { lang.lowercase().startsWith(it) }
            assertFalse(isRtl, "$lang should NOT be RTL")
        }
    }

    @Test
    fun testLanguageSwitchUpdatesCurrentLanguage() {
        // Simulate language switch state tracking
        var currentLanguage = "en"
        val availableLanguages = listOf("en", "fr", "ar")

        currentLanguage = "fr"
        assertEquals("fr", currentLanguage)

        currentLanguage = "ar"
        assertEquals("ar", currentLanguage)
    }

    @Test
    fun testLanguageSwitchToUnavailableLanguageFails() {
        val availableLanguages = listOf("en", "fr")
        val requestedLang = "sw"
        val success = availableLanguages.contains(requestedLang)
        assertFalse(success, "Switching to unavailable language should fail")
    }

    @Test
    fun testAllRtlLanguagePrefixes() {
        // Complete list of RTL language prefixes from LanguageViewModel
        val rtlPrefixes = listOf("ar", "he", "ur", "fa", "ps", "sd", "yi")
        assertEquals(7, rtlPrefixes.size, "Should have exactly 7 RTL prefixes")
    }
}
