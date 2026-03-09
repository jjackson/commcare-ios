package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * issues
 *
 * * conflicting constructors... null(listwrapper())... confuses listwrapper as type, even though it contains val
 */

/**
 * constructor guidelines: each child of this class should follow these rules with its constructors
 *
 * 1) every constructor that sets 'val' should have a matching constructor for deserialization that
 * leaves 'val' null
 * 2) every constructor that accepts an ExternalizableWrapper should also have a convenience constructor
 * that accepts a Class, and wraps the Class in an ExtWrapBase (the identity wrapper)
 * 3) there must exist a null constructor for meta-deserialization (for applicable wrappers)
 * 4) be careful about properly disambiguating constructors
 */
abstract class ExternalizableWrapper : Externalizable {
    /* core data that is being wrapped; will be null when shell wrapper is created for deserialization */
    @JvmField
    var `val`: Any? = null

    /* create a copy of a wrapper, but with new val (but all the same type annotations */
    abstract fun clone(`val`: Any?): ExternalizableWrapper

    /* deserialize the state of the externalizable wrapper */
    @Throws(IOException::class, DeserializationException::class)
    abstract fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory)

    /* serialize the state of the externalizable wrapper (type information only, not value) */
    @Throws(IOException::class)
    abstract fun metaWriteExternal(out: DataOutputStream)

    @Throws(IOException::class, DeserializationException::class)
    abstract override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory)

    @Throws(IOException::class)
    abstract override fun writeExternal(out: DataOutputStream)

    fun baseValue(): Any? {
        var baseVal = `val`
        while (baseVal is ExternalizableWrapper) {
            baseVal = baseVal.`val`
        }
        return baseVal
    }

    val isEmpty: Boolean
        get() = baseValue() == null
}
