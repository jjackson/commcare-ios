package org.javarosa.core.reference

import org.javarosa.core.services.locale.LocaleDataSource
import org.javarosa.core.services.locale.LocalizationUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.io.PlatformInputStream

/**
 * The ReferenceDataSource is a source of locale data which
 * is located at a location which is defined by a ReferenceURI.
 *
 * @author Clayton Sims
 */
open class ReferenceDataSource : LocaleDataSource {

    private var referenceURI: String? = null

    @Suppress("unused")
    constructor() {
        // for serialization
    }

    /**
     * Creates a new Data Source for Locale data with the given resource URI.
     *
     * @param referenceURI URI to the resource file from which data should be loaded
     */
    constructor(referenceURI: String?) {
        if (referenceURI == null) {
            throw NullPointerException("Reference URI cannot be null when creating a Resource File Data Source")
        }
        this.referenceURI = referenceURI
    }

    override fun getLocalizedText(): HashMap<String, String> {
        var inputStream: PlatformInputStream? = null
        try {
            inputStream = ReferenceManager.instance().DeriveReference(referenceURI!!).getStream()
            if (inputStream == null) {
                throw PlatformIOException("There is no resource at $referenceURI")
            }
            return LocalizationUtils.parseLocaleInput(inputStream)
        } catch (e: PlatformIOException) {
            e.printStackTrace()
            throw RuntimeException("IOException while getting localized text at reference $referenceURI\n${e.message}")
        } catch (e: InvalidReferenceException) {
            e.printStackTrace()
            throw RuntimeException("Invalid Reference! $referenceURI")
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: PlatformIOException) {
                }
            }
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        referenceURI = `in`.readUTF()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        out.writeUTF(referenceURI!!)
    }
}
