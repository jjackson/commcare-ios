package org.javarosa.core.util.externalizable

import kotlin.jvm.JvmField
import kotlin.reflect.KClass

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

    constructor(type: KClass<*>?) {
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

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        if (`in`.readBoolean()) {
            `val` = ExtUtil.read(`in`, type!!, pf)
        } else {
            `val` = null
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        val localVal = `val`
        if (localVal != null) {
            out.writeBoolean(true)
            ExtUtil.write(out, localVal)
        } else {
            out.writeBoolean(false)
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        type = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        ExtWrapTagged.writeTag(out, if (`val` == null) Any() else `val`!!)
    }
}
