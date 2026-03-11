package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

// map of objects where elements are multiple types, keys are still assumed to be of a single
// (non-polymorphic) type
// if elements are compound types (i.e., need wrappers), they must be pre-wrapped before invoking
// this wrapper, because... come on now.
class ExtWrapMapPoly : ExternalizableWrapper {

    private var keyType: ExternalizableWrapper? = null

    @JvmField
    var ordered: Boolean = false

    /* serialization */

    constructor(`val`: HashMap<*, *>) : this(`val`, null)

    constructor(`val`: HashMap<*, *>, keyType: ExternalizableWrapper?) {
        requireNotNull(`val`)
        this.`val` = `val`
        this.keyType = keyType
        this.ordered = `val` is OrderedHashtable<*, *>
    }

    /* deserialization */

    constructor()

    constructor(keyType: Class<*>) : this(keyType, false)

    constructor(keyType: ExternalizableWrapper) : this(keyType, false)

    constructor(keyType: Class<*>, ordered: Boolean) : this(ExtWrapBase(keyType), ordered)

    constructor(keyType: ExternalizableWrapper, ordered: Boolean) {
        requireNotNull(keyType)
        this.keyType = keyType
        this.ordered = ordered
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        @Suppress("UNCHECKED_CAST")
        return ExtWrapMapPoly(`val` as HashMap<*, *>, keyType)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`)
        val h: HashMap<Any, Any> =
            if (ordered) OrderedHashtable(size.toInt()) else HashMap(size.toInt())
        for (i in 0 until size) {
            val key = ExtUtil.read(`in`, keyType!!, pf)!!
            val elem = ExtUtil.read(`in`, ExtWrapTagged(), pf)!!
            h[key] = elem
        }
        `val` = h
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val h = `val` as HashMap<Any, Any>

        ExtUtil.writeNumeric(out, h.size.toLong())
        val e = h.keys.iterator()
        while (e.hasNext()) {
            val key = e.next()
            val elem = h[key]!!

            ExtUtil.write(out, if (keyType == null) key else keyType!!.clone(key))
            ExtUtil.write(out, ExtWrapTagged(elem))
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        ordered = ExtUtil.readBool(`in`)
        keyType = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val h = `val` as HashMap<Any, Any>

        ExtUtil.writeBool(out, ordered)

        val keyTagObj: Any = if (keyType == null) {
            if (h.isEmpty()) Any() else h.keys.iterator().next()
        } else {
            keyType!!
        }
        ExtWrapTagged.writeTag(out, keyTagObj)
    }
}
