package org.javarosa.core.util.externalizable

import kotlin.jvm.JvmField
import kotlin.reflect.KClass

class ExtWrapBase : ExternalizableWrapper {

    @JvmField
    var type: KClass<*>? = null

    /* serialization */
    constructor(`val`: Any) {
        if (`val` is ExternalizableWrapper) {
            throw IllegalArgumentException("ExtWrapBase can only contain base types")
        }
        this.`val` = `val`
    }

    /* deserialization */
    constructor(type: KClass<*>) {
        if (type == ExternalizableWrapper::class || type == ExtWrapBase::class) {
            throw IllegalArgumentException("ExtWrapBase can only contain base types")
        }
        this.type = type
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        return ExtWrapBase(`val`!!)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        `val` = ExtUtil.read(`in`, type!!, pf)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, `val`!!)
    }

    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        throw RuntimeException("Identity wrapper should never be tagged")
    }

    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        throw RuntimeException("Identity wrapper should never be tagged")
    }
}
