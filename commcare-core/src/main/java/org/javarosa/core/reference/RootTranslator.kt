package org.javarosa.core.reference

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory

/**
 * A Root Translator is a simple reference factory which doesn't
 * actually derive any specific references, but rather translates
 * references from one prefix to another. This is useful for roots
 * which don't describe any real raw accessor like "jr://media/",
 * which could access a file reference (jr://file/) on one platform,
 * but a resource reference (jr://resource/) on another.
 *
 * Root Translators can be externalized and used as a dynamically
 * configured object.
 *
 * @author ctsims
 */
open class RootTranslator : ReferenceFactory, Externalizable {

    @JvmField
    var prefix: String? = null

    @JvmField
    var translatedPrefix: String? = null

    /**
     * Serialization only!
     */
    constructor()

    /**
     * Creates a translator which will create references of the
     * type described by translatedPrefix whenever references of
     * the type prefix are being derived.
     */
    constructor(prefix: String?, translatedPrefix: String?) {
        //TODO: Manage semantics of "ends with /" etc here?
        this.prefix = prefix
        this.translatedPrefix = translatedPrefix
    }

    @Throws(InvalidReferenceException::class)
    override fun derive(URI: String): Reference {
        return ReferenceManager.instance().DeriveReference(translatedPrefix + URI.substring(prefix!!.length))
    }

    @Throws(InvalidReferenceException::class)
    override fun derive(URI: String, context: String): Reference {
        return ReferenceManager.instance().DeriveReference(URI, translatedPrefix + context.substring(prefix!!.length))
    }

    override fun derives(URI: String): Boolean {
        return URI.startsWith(prefix!!)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        prefix = ExtUtil.readString(`in`)
        translatedPrefix = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, prefix)
        ExtUtil.writeString(out, translatedPrefix)
    }
}
