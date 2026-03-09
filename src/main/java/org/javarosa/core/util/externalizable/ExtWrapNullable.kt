package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class ExtWrapNullable : ExternalizableWrapper {

    @JvmField
    var type: ExternalizableWrapper? = null

    /* serialization */
    constructor(`val`: Any?) {
        this.`val` = `val`
    }

    /* deserialization */
    constructor() {
    }

    constructor(type: Class<*>?) {
        if (type != null) {
            this.type = ExtWrapBase(type)
        }
    }

    /* serialization or deserialization, depending on context */
    constructor(type: ExternalizableWrapper?) {
        if (type == null) {
            return
        }
        if (type is ExtWrapNullable) {
            throw IllegalArgumentException("Wrapping nullable with nullable is redundant")
        } else if (type.isEmpty) {
            this.type = type
        } else {
            this.`val` = type
        }
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        return ExtWrapNullable(`val`)
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        if (`in`.readBoolean()) {
            `val` = ExtUtil.read(`in`, type!!, pf)
        } else {
            `val` = null
        }
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        val localVal = `val`
        if (localVal != null) {
            out.writeBoolean(true)
            ExtUtil.write(out, localVal)
        } else {
            out.writeBoolean(false)
        }
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        type = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(IOException::class)
    override fun metaWriteExternal(out: DataOutputStream) {
        ExtWrapTagged.writeTag(out, if (`val` == null) Any() else `val`!!)
    }
}
