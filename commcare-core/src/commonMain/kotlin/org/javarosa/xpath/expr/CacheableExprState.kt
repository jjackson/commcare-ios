package org.javarosa.xpath.expr

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

/**
 * Holder object for all of the state values an InFormCacheableExpr needs to keep track of
 */
open class CacheableExprState : Externalizable {

    @JvmField
    var computedCacheability: Boolean = false
    @JvmField
    var exprIsCacheable: Boolean = false
    @JvmField
    var computedContextTypes: Boolean = false
    @JvmField
    var contextRefIsRelevant: Boolean = false
    @JvmField
    var originalContextRefIsRelevant: Boolean = false

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        computedCacheability = SerializationHelpers.readBool(`in`)
        exprIsCacheable = SerializationHelpers.readBool(`in`)
        computedContextTypes = SerializationHelpers.readBool(`in`)
        contextRefIsRelevant = SerializationHelpers.readBool(`in`)
        originalContextRefIsRelevant = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeBool(out, computedCacheability)
        SerializationHelpers.writeBool(out, exprIsCacheable)
        SerializationHelpers.writeBool(out, computedContextTypes)
        SerializationHelpers.writeBool(out, contextRefIsRelevant)
        SerializationHelpers.writeBool(out, originalContextRefIsRelevant)
    }
}
