package org.javarosa.core.services.locale

import org.javarosa.core.reference.ReferenceDataSource
import org.javarosa.core.util.NoLocalizedTextException
import kotlin.jvm.JvmStatic

object Localization {

    @JvmStatic
    fun get(key: String): String {
        return get(key, arrayOf())
    }

    @JvmStatic
    fun get(key: String, arg: String): String {
        checkRep()
        return LocalizerManager.getGlobalLocalizer()!!.getText(key, arrayOf(arg))
    }

    @JvmStatic
    fun get(key: String, args: Array<String>): String {
        checkRep()
        return LocalizerManager.getGlobalLocalizer()!!.getText(key, args)
    }

    @JvmStatic
    fun get(key: String, args: HashMap<*, *>): String {
        checkRep()
        return LocalizerManager.getGlobalLocalizer()!!.getText(key, args)
    }

    @JvmStatic
    fun getWithDefault(key: String, valueIfKeyMissing: String): String {
        return getWithDefault(key, arrayOf(), valueIfKeyMissing)
    }

    @JvmStatic
    fun getWithDefault(key: String, args: Array<String>, valueIfKeyMissing: String): String {
        return try {
            get(key, args)
        } catch (e: NoLocalizedTextException) {
            valueIfKeyMissing
        }
    }

    @JvmStatic
    fun registerLanguageReference(localeName: String, referenceUri: String) {
        init(false)
        if (!LocalizerManager.getGlobalLocalizer()!!.hasLocale(localeName)) {
            LocalizerManager.getGlobalLocalizer()!!.addAvailableLocale(localeName)
        }
        LocalizerManager.getGlobalLocalizer()!!.registerLocaleResource(localeName, ReferenceDataSource(referenceUri))
        if (LocalizerManager.getGlobalLocalizer()!!.defaultLocale == null) {
            LocalizerManager.getGlobalLocalizer()!!.setDefaultLocale(localeName)
        }
    }

    @JvmStatic
    fun getGlobalLocalizerAdvanced(): Localizer {
        init(false)
        return LocalizerManager.getGlobalLocalizer()!!
    }

    @JvmStatic
    fun setLocale(locale: String) {
        checkRep()
        LocalizerManager.getGlobalLocalizer()!!.setLocale(locale)
    }

    @JvmStatic
    fun getCurrentLocale(): String? {
        checkRep()
        return LocalizerManager.getGlobalLocalizer()!!.locale
    }

    @JvmStatic
    fun setDefaultLocale(defaultLocale: String) {
        checkRep()
        LocalizerManager.getGlobalLocalizer()!!.setDefaultLocale(defaultLocale)
    }

    @JvmStatic
    fun init(force: Boolean) {
        LocalizerManager.init(force)
    }

    private fun checkRep() {
        init(false)
        if (LocalizerManager.getGlobalLocalizer()!!.availableLocales.isEmpty()) {
            throw LocaleTextException("There are no locales defined for the application. Please make sure to register locale text using the Locale.register() method")
        }
    }

    @JvmStatic
    fun getArray(key: String): Array<String> {
        return get(key).split(",").toTypedArray()
    }
}
