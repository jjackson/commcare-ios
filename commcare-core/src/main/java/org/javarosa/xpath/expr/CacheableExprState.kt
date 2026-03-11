package org.javarosa.xpath.expr

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
        computedCacheability = ExtUtil.readBool(`in`)
        exprIsCacheable = ExtUtil.readBool(`in`)
        computedContextTypes = ExtUtil.readBool(`in`)
        contextRefIsRelevant = ExtUtil.readBool(`in`)
        originalContextRefIsRelevant = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeBool(out, computedCacheability)
        ExtUtil.writeBool(out, exprIsCacheable)
        ExtUtil.writeBool(out, computedContextTypes)
        ExtUtil.writeBool(out, contextRefIsRelevant)
        ExtUtil.writeBool(out, originalContextRefIsRelevant)
    }
}
