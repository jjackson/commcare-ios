package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

// list of objects of multiple types
// if elements are compound types (i.e., need wrappers), they must be pre-wrapped before invoking
// this wrapper, because... come on now.
class ExtWrapListPoly : ExternalizableWrapper {

    /* serialization */

    constructor(`val`: ArrayList<*>) {
        requireNotNull(`val`)
        this.`val` = `val`
    }

    /* deserialization */

    constructor()

    override fun clone(`val`: Any?): ExternalizableWrapper {
        @Suppress("UNCHECKED_CAST")
        return ExtWrapListPoly(`val` as ArrayList<*>)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`)
        val v = ArrayList<Any?>(size.toInt())
        for (i in 0 until size) {
            v.add(ExtUtil.read(`in`, ExtWrapTagged(), pf))
        }
        `val` = v
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val v = `val` as ArrayList<Any?>
        ExtUtil.writeNumeric(out, v.size.toLong())
        for (i in v.indices) {
            ExtUtil.write(out, ExtWrapTagged(v[i]!!))
        }
    }

    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        // do nothing
    }

    override fun metaWriteExternal(out: DataOutputStream) {
        // do nothing
    }
}
