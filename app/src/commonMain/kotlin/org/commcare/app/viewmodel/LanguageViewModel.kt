package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.javarosa.core.services.locale.Localizer

/**
 * Manages language selection for multi-language forms and menus.
 * Reads available locales from the engine's Localizer and switches display language.
 */
class LanguageViewModel {
    var availableLanguages by mutableStateOf<List<String>>(emptyList())
        private set
    var currentLanguage by mutableStateOf("")
        private set

    private var localizer: Localizer? = null

    /**
     * Initialize from a Localizer (typically from FormDef or Localization).
     */
    fun loadLanguages(localizer: Localizer) {
        this.localizer = localizer
        availableLanguages = localizer.availableLocales.toList()
        currentLanguage = localizer.locale ?: availableLanguages.firstOrNull() ?: ""
    }

    /**
     * Switch the active language. Returns true if the switch was successful.
     */
    fun setLanguage(language: String): Boolean {
        val loc = localizer ?: return false
        return try {
            loc.setLocale(language)
            currentLanguage = language
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if the current language is RTL.
     */
    val isRtl: Boolean
        get() {
            val lang = currentLanguage.lowercase()
            return lang.startsWith("ar") || // Arabic
                lang.startsWith("he") || // Hebrew
                lang.startsWith("ur") || // Urdu
                lang.startsWith("fa") || // Farsi
                lang.startsWith("ps") || // Pashto
                lang.startsWith("sd") || // Sindhi
                lang.startsWith("yi")    // Yiddish
        }
}
