package org.javarosa.core.services.locale

import org.javarosa.core.util.NoLocalizedTextException
import org.javarosa.core.util.UnregisteredLocaleException
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * The Localizer object maintains mappings for locale ID's and Object
 * ID's to the String values associated with them in different
 * locales.
 *
 * @author Drew Roos/Clayton Sims
 */
class Localizer @JvmOverloads constructor(
    private var fallbackDefaultLocale: Boolean = false,
    private var fallbackDefaultForm: Boolean = false
) : Externalizable {

    private var locales: ArrayList<String> = ArrayList()
    private var localeResources: HashMap<String, ArrayList<LocaleDataSource>> = HashMap()
    private var currentLocaleData: HashMap<String, String>? = HashMap()
    var defaultLocale: String? = null
        private set
    var locale: String? = null
        private set

    /**
     * Create a new locale (with no mappings). Do nothing if the locale is already defined.
     *
     * @param locale Locale to add. Must not be null.
     * @return True if the locale was not already defined.
     * @throws NullPointerException if locale is null
     */
    fun addAvailableLocale(locale: String): Boolean {
        return if (hasLocale(locale)) {
            false
        } else {
            locales.add(locale)
            localeResources[locale] = ArrayList<LocaleDataSource>()
            true
        }
    }

    /**
     * Get a list of defined locales.
     *
     * @return Array of defined locales, in order they were created.
     */
    val availableLocales: Array<String>
        get() {
            return locales.toTypedArray()
        }

    /**
     * Get whether a locale is defined. The locale need not have any mappings.
     *
     * @param locale Locale
     * @return Whether the locale is defined. False if null
     */
    fun hasLocale(locale: String?): Boolean {
        return locale != null && locales.contains(locale)
    }

    /**
     * Set the current locale. The locale must be defined. Will notify all registered ILocalizables of the change in locale.
     *
     * @param currentLocale Locale. Must be defined and not null.
     * @throws UnregisteredLocaleException If locale is null or not defined.
     */
    fun setLocale(currentLocale: String?) {
        if (currentLocale == null || !hasLocale(currentLocale)) {
            throw UnregisteredLocaleException("Attempted to set to a locale that is not defined. Attempted Locale: $currentLocale")
        }

        if (currentLocale != this.locale) {
            this.locale = currentLocale
        }
        loadCurrentLocaleResources()
    }

    /**
     * Set the default locale. The locale must be defined.
     *
     * @param defaultLocale Default locale. Must be defined. May be null, in which case there will be no default locale.
     * @throws UnregisteredLocaleException If locale is not defined.
     */
    fun setDefaultLocale(defaultLocale: String?) {
        if (defaultLocale != null && !hasLocale(defaultLocale)) {
            throw UnregisteredLocaleException("Attempted to set default to a locale that is not defined")
        }

        this.defaultLocale = defaultLocale
    }

    /**
     * Set the current locale to the default locale. The default locale must be set.
     *
     * @throws IllegalStateException If default locale is not set.
     */
    fun setToDefault() {
        if (defaultLocale == null) {
            throw IllegalStateException("Attempted to set to default locale when default locale not set")
        }

        setLocale(defaultLocale!!)
    }

    /**
     * Constructs a body of local resources to be the set of Current Locale Data.
     */
    private fun loadCurrentLocaleResources() {
        currentLocaleData = locale?.let { getLocaleData(it) }
    }

    /**
     * Registers a resource file as a source of locale data for the specified
     * locale.
     *
     * @param locale   The locale of the definitions provided.
     * @param resource A LocaleDataSource containing string data for the locale provided
     * @throws NullPointerException if resource or locale are null
     */
    fun registerLocaleResource(locale: String?, resource: LocaleDataSource?) {
        if (locale == null) {
            throw NullPointerException("Attempt to register a data source to a null locale in the localizer")
        }
        if (resource == null) {
            throw NullPointerException("Attempt to register a null data source in the localizer")
        }
        if (localeResources.containsKey(locale)) {
            val resources = localeResources[locale]!!
            resources.add(resource)
        } else {
            val resources = ArrayList<LocaleDataSource>()
            resources.add(resource)
            localeResources[locale] = resources
        }

        if (locale == this.locale || locale == defaultLocale) {
            // Reload locale data if the resource is for a locale in use
            loadCurrentLocaleResources()
        }
    }

    /**
     * Get the set of mappings for a locale.
     *
     * @return HashMap representing text mappings for this locale. Returns null if locale not defined or null.
     */
    fun getLocaleData(locale: String?): HashMap<String, String>? {
        if (locale == null || !this.locales.contains(locale)) {
            return null
        }

        //It's very important that any default locale contain the appropriate strings to localize the interface
        //for any possible language. As such, we'll keep around a table with only the default locale keys to
        //ensure that there are no localizations which are only present in another locale, which causes ugly
        //and difficult to trace errors.
        val defaultLocaleKeys = HashSet<String>()

        //This table will be loaded with the default values first (when applicable), and then with any
        //language specific translations overwriting the existing values.
        val data = HashMap<String, String>()

        // If there's a default locale, we load all of its elements into memory first, then allow
        // the current locale to overwrite any differences between the two.
        if (fallbackDefaultLocale && defaultLocale != null) {
            for (defaultResource in localeResources[defaultLocale]!!) {
                data.putAll(defaultResource.getLocalizedText())
            }
            defaultLocaleKeys.addAll(data.keys)
        }

        for (resource in localeResources[locale]!!) {
            data.putAll(resource.getLocalizedText())
        }

        //If we're using a default locale, now we want to make sure that it has all of the keys
        //that the locale we want to use does. Otherwise, the app will crash when we switch to
        //a locale that doesn't contain the key.
        if (fallbackDefaultLocale && defaultLocale != null) {
            var missingKeys = ""
            var keysmissing = 0
            val en = data.keys.iterator()
            while (en.hasNext()) {
                val key = en.next() as String
                if (!defaultLocaleKeys.contains(key)) {
                    missingKeys += "$key,"
                    keysmissing++
                }
            }
            if (keysmissing > 0) {
                //Is there a good way to localize these exceptions?
                throw NoLocalizedTextException(
                    "Error loading locale " + locale +
                            ". There were " + keysmissing + " keys which were contained in this locale, but were not " +
                            "properly registered in the default Locale. Any keys which are added to a locale should always " +
                            "be added to the default locale to ensure appropriate functioning.\n" +
                            "The missing translations were for the keys: " + missingKeys, missingKeys, defaultLocale!!
                )
            }
        }

        return data
    }

    /**
     * Get the mappings for a locale, but throw an exception if locale is not defined.
     *
     * @param locale Locale
     * @return Text mappings for locale.
     * @throws UnregisteredLocaleException If locale is not defined or null.
     */
    fun getLocaleMap(locale: String?): HashMap<String, String> {
        return getLocaleData(locale)
            ?: throw UnregisteredLocaleException("Attempted to access an undefined locale.")
    }

    /**
     * Determine whether a locale has a mapping for a given text handle. Only tests the specified locale and form; does
     * not fallback to any default locale or text form.
     *
     * @param locale Locale. Must be defined and not null.
     * @param textID Text handle.
     * @return True if a mapping exists for the text handle in the given locale.
     * @throws UnregisteredLocaleException If locale is not defined.
     */
    fun hasMapping(locale: String?, textID: String?): Boolean {
        if (locale == null || !locales.contains(locale)) {
            throw UnregisteredLocaleException("Attempted to access an undefined locale ($locale) while checking for a mapping for  $textID")
        }
        val resources = localeResources[locale]!!
        for (source in resources) {
            if (source.getLocalizedText().containsKey(textID)) {
                return true
            }
        }
        return false
    }

    /**
     * Retrieve the localized text for a text handle in the current locale. See getText(String, String) for details.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @return Localized text. If no text is found after using all fallbacks, return null.
     * @throws UnregisteredLocaleException If current locale is not set.
     * @throws NullPointerException        if textID is null
     */
    fun getText(textID: String): String? {
        val currentLocale = locale ?: throw UnregisteredLocaleException("Current locale not set")
        return getText(textID, currentLocale)
    }

    /**
     * Retrieve the localized text for a text handle in the current locale. See getText(String, String) for details.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @param args   arguments for string variables.
     * @return Localized text
     * @throws UnregisteredLocaleException If current locale is not set.
     * @throws NullPointerException        if textID is null
     * @throws NoLocalizedTextException    If there is no text for the specified id
     */
    fun getText(textID: String, args: Array<String>): String {
        val currentLocale = locale ?: throw UnregisteredLocaleException("Current locale not set")
        var text = getText(textID, currentLocale)
        if (text != null) {
            text = processArguments(text, args)
        } else {
            throw NoLocalizedTextException(
                "The Localizer could not find a definition for ID: $textID in the '$currentLocale' locale.",
                textID, currentLocale
            )
        }
        return text
    }

    /**
     * Retrieve the localized text for a text handle in the current locale. See getText(String, String) for details.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @param args   arguments for string variables.
     * @return Localized text. If no text is found after using all fallbacks, return null.
     * @throws UnregisteredLocaleException If current locale is not set.
     * @throws NullPointerException        if textID is null
     * @throws NoLocalizedTextException    If there is no text for the specified id
     */
    fun getText(textID: String, args: HashMap<*, *>): String {
        val currentLocale = locale ?: throw UnregisteredLocaleException("Current locale not set")
        var text = getText(textID, currentLocale)
        if (text != null) {
            text = processArguments(text, args)
        } else {
            throw NoLocalizedTextException(
                "The Localizer could not find a definition for ID: $textID in the '$currentLocale' locale.",
                textID, currentLocale
            )
        }
        return text
    }

    /**
     * Retrieve the localized text for a text handle in the given locale. If no mapping is found initially, then,
     * depending on enabled fallback modes, other places will be searched until a mapping is found.
     *
     * The search order is thus:
     * 1) Specified locale, specified text form
     * 2) Specified locale, default text form
     * 3) Default locale, specified text form
     * 4) Default locale, default text form
     *
     * (1) and (3) are only searched if a text form ('long', 'short', etc.) is specified.
     * If a text form is specified, (2) and (4) are only searched if default-form-fallback mode is enabled.
     * (3) and (4) are only searched if default-locale-fallback mode is enabled. It is not an error in this situation
     * if no default locale is set; (3) and (4) will simply not be searched.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @param locale Locale. Must be defined and not null.
     * @return Localized text. If no text is found after using all fallbacks, return null.
     * @throws UnregisteredLocaleException If the locale is not defined or null.
     * @throws NullPointerException        if textID is null
     */
    fun getText(textID: String, locale: String?): String? {
        var text = getRawText(locale, textID)
        if (text == null && fallbackDefaultForm && textID.contains(";")) {
            text = getRawText(locale, textID.substring(0, textID.indexOf(";")))
        }
        //Update: We handle default text without forms without needing to do this. We still need it for default text with default forms, though
        if (text == null && fallbackDefaultLocale && locale != defaultLocale && defaultLocale != null && fallbackDefaultForm) {
            text = getText(textID, defaultLocale!!)
        }
        return text
    }

    /**
     * Get text for locale and exact text ID only, not using any fallbacks.
     *
     * NOTE: This call will only return the full compliment of available strings if and
     * only if the requested locale is current. Otherwise it will only retrieve strings
     * declared at runtime.
     *
     * @param locale Locale. Must be defined and not null.
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @return Localized text. Return null if none found.
     * @throws UnregisteredLocaleException If the locale is not defined or null.
     * @throws NullPointerException        if textID is null
     */
    fun getRawText(locale: String?, textID: String): String? {
        if (locale == null) {
            throw UnregisteredLocaleException("Null locale when attempting to fetch text id: $textID")
        }
        return if (locale == this.locale) {
            currentLocaleData?.get(textID)
        } else {
            getLocaleMap(locale)[textID]
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(dis: PlatformDataInputStream, pf: PrototypeFactory) {
        fallbackDefaultLocale = SerializationHelpers.readBool(dis)
        fallbackDefaultForm = SerializationHelpers.readBool(dis)
        @Suppress("UNCHECKED_CAST")
        localeResources = SerializationHelpers.readStringListPolyMap(dis, pf) as HashMap<String, ArrayList<LocaleDataSource>>
        locales = SerializationHelpers.readStringList(dis)
        setDefaultLocale(SerializationHelpers.readNullableString(dis, pf))
        val currentLocale = SerializationHelpers.readNullableString(dis, pf)
        if (currentLocale != null) {
            setLocale(currentLocale)
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: PlatformDataOutputStream) {
        SerializationHelpers.writeBool(dos, fallbackDefaultLocale)
        SerializationHelpers.writeBool(dos, fallbackDefaultForm)
        SerializationHelpers.writeStringListPolyMap(dos, localeResources as HashMap<*, *>)
        SerializationHelpers.writeList(dos, locales)
        SerializationHelpers.writeNullable(dos, defaultLocale)
        SerializationHelpers.writeNullable(dos, locale)
    }

    override fun equals(other: Any?): Boolean {
        if (other is Localizer) {
            //TODO: Compare all resources
            return (SerializationHelpers.nullEquals(locales, other.locales, false) &&
                    SerializationHelpers.nullEquals(localeResources, other.localeResources, true) &&
                    SerializationHelpers.nullEquals(defaultLocale, other.defaultLocale, false) &&
                    SerializationHelpers.nullEquals(locale, other.locale, true) &&
                    fallbackDefaultLocale == other.fallbackDefaultLocale &&
                    fallbackDefaultForm == other.fallbackDefaultForm)
        }

        return false
    }

    override fun hashCode(): Int {
        var hash = locales.hashCode() xor
                localeResources.hashCode() xor
                (if (fallbackDefaultLocale) 0 else 31) xor
                (if (fallbackDefaultForm) 0 else 31)
        if (defaultLocale != null) {
            hash = hash xor defaultLocale.hashCode()
        }
        if (locale != null) {
            hash = hash xor locale.hashCode()
        }
        return hash
    }

    /**
     * For Testing: Get default locale fallback mode
     *
     * @return default locale fallback mode
     */
    fun getFallbackLocale(): Boolean {
        return fallbackDefaultLocale
    }

    /**
     * For Testing: Get default form fallback mode
     *
     * @return default form fallback mode
     */
    fun getFallbackForm(): Boolean {
        return fallbackDefaultForm
    }

    companion object {
        @JvmStatic
        fun getArgs(text: String): ArrayList<String> {
            val args = ArrayList<String>()
            var i = text.indexOf("\${")
            while (i != -1) {
                val j = text.indexOf("}", i)
                if (j == -1) {
                    org.javarosa.core.util.platformStdErrPrintln("Warning: unterminated \${...} arg")
                    break
                }

                val arg = text.substring(i + 2, j)
                if (!args.contains(arg)) {
                    args.add(arg)
                }

                i = text.indexOf("\${", j + 1)
            }
            return args
        }

        /**
         * Replace all arguments in 'text', of form ${x}, with the value 'x' maps
         * to in 'args'.
         */
        @JvmStatic
        fun processArguments(text: String, args: HashMap<*, *>): String {
            var text = text
            var i = text.indexOf("\${")

            // find every instance of ${some_key} in text and replace it with the
            // value that some_key maps to in args.
            while (i != -1) {
                var j = text.indexOf("}", i)

                // abort if no closing bracket
                if (j == -1) {
                    org.javarosa.core.util.platformStdErrPrintln("Warning: unterminated \${...} arg")
                    break
                }

                val argName = text.substring(i + 2, j)
                val argVal = args[argName] as String?
                // if we found a mapping in the args table, perform text substitution
                if (argVal != null) {
                    text = text.substring(0, i) + argVal + text.substring(j + 1)
                    j = i + argVal.length - 1
                }

                i = text.indexOf("\${", j + 1)
            }
            return text
        }

        @JvmStatic
        fun processArguments(text: String, args: Array<String>): String {
            return processArguments(text, args, 0)
        }

        @JvmStatic
        fun processArguments(text: String, args: Array<String>, currentArg: Int): String {
            var currentArg = currentArg
            if (text.contains("\${") && args.size > currentArg) {
                var index = extractNextIndex(text, args)

                if (index == -1) {
                    index = currentArg
                    currentArg++
                }

                val value = args[index]
                val replaced = replaceFirstValue(text, value)
                return replaced[0] + processArguments(replaced[1], args, currentArg)
            } else {
                return text
            }
        }

        @JvmStatic
        fun clearArguments(text: String): String {
            val v = getArgs(text)
            val empty = Array(v.size) { "" }
            return processArguments(text, empty)
        }

        private fun extractNextIndex(text: String, args: Array<String>): Int {
            val start = text.indexOf("\${")
            val end = text.indexOf("}", start)

            if (start != -1 && end != -1) {
                val value = text.substring(start + "\${".length, end)
                try {
                    val index = Integer.parseInt(value)
                    if (index >= 0 && index < args.size) {
                        return index
                    }
                } catch (nfe: NumberFormatException) {
                    return -1
                }
            }

            return -1
        }

        private fun replaceFirstValue(text: String, value: String): Array<String> {
            val start = text.indexOf("\${")
            val end = text.indexOf("}", start)

            return arrayOf(text.substring(0, start) + value, text.substring(end + 1, text.length))
        }
    }
}
