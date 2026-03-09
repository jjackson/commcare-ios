package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class ExtWrapBase : ExternalizableWrapper {

    @JvmField
    var type: Class<*>? = null

    /* serialization */
    constructor(`val`: Any) {
        if (`val` is ExternalizableWrapper) {
            throw IllegalArgumentException("ExtWrapBase can only contain base types")
        }
        this.`val` = `val`
    }

    /* deserialization */
    constructor(type: Class<*>) {
        if (ExternalizableWrapper::class.java.isAssignableFrom(type)) {
            throw IllegalArgumentException("ExtWrapBase can only contain base types")
        }
        this.type = type
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        return ExtWrapBase(`val`!!)
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        `val` = ExtUtil.read(`in`, type!!, pf)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, `val`!!)
    }

    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        throw RuntimeException("Identity wrapper should never be tagged")
    }

    override fun metaWriteExternal(out: DataOutputStream) {
        throw RuntimeException("Identity wrapper should never be tagged")
    }
}
