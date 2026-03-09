package org.javarosa.xpath.expr

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        computedCacheability = ExtUtil.readBool(`in`)
        exprIsCacheable = ExtUtil.readBool(`in`)
        computedContextTypes = ExtUtil.readBool(`in`)
        contextRefIsRelevant = ExtUtil.readBool(`in`)
        originalContextRefIsRelevant = ExtUtil.readBool(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeBool(out, computedCacheability)
        ExtUtil.writeBool(out, exprIsCacheable)
        ExtUtil.writeBool(out, computedContextTypes)
        ExtUtil.writeBool(out, contextRefIsRelevant)
        ExtUtil.writeBool(out, originalContextRefIsRelevant)
    }
}
