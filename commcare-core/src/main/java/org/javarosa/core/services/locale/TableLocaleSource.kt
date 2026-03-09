package org.javarosa.core.services.locale

import org.javarosa.core.util.UnregisteredLocaleException
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable

/**
 * @author Clayton Sims
 */
class TableLocaleSource : LocaleDataSource {
    private var localeData: Hashtable<String, String>

    constructor() {
        localeData = Hashtable()
    }

    constructor(localeData: Hashtable<String, String>) {
        this.localeData = localeData
    }

    /**
     * Set a text mapping for a single text handle for a given locale.
     *
     * @param textID Text handle. Must not be null. Need not be previously defined for this locale.
     * @param text   Localized text for this text handle and locale. Will overwrite any previous mapping, if one existed.
     *               If null, will remove any previous mapping for this text handle, if one existed.
     * @throws UnregisteredLocaleException If locale is not defined or null.
     * @throws NullPointerException        if textID is null
     */
    fun setLocaleMapping(textID: String?, text: String?) {
        if (textID == null) {
            throw NullPointerException("Null textID when attempting to register $text in locale table")
        }
        if (text == null) {
            localeData.remove(textID)
        } else {
            localeData[textID] = text
        }
    }

    /**
     * Determine whether a locale has a mapping for a given text handle. Only tests the specified locale and form; does
     * not fallback to any default locale or text form.
     *
     * @param textID Text handle.
     * @return True if a mapping exists for the text handle in the given locale.
     * @throws UnregisteredLocaleException If locale is not defined.
     */
    fun hasMapping(textID: String?): Boolean {
        return textID != null && localeData[textID] != null
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TableLocaleSource) {
            return false
        }
        return ExtUtil.equals(localeData, other.localeData, true)
    }

    override fun hashCode(): Int {
        return localeData.hashCode()
    }

    override fun getLocalizedText(): Hashtable<String, String> {
        return localeData
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        localeData = ExtUtil.read(`in`, ExtWrapMap(String::class.java, String::class.java), pf) as Hashtable<String, String>
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapMap(localeData))
    }
}
