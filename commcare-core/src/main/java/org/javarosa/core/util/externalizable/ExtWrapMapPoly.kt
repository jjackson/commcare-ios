package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable

// map of objects where elements are multiple types, keys are still assumed to be of a single
// (non-polymorphic) type
// if elements are compound types (i.e., need wrappers), they must be pre-wrapped before invoking
// this wrapper, because... come on now.
class ExtWrapMapPoly : ExternalizableWrapper {

    private var keyType: ExternalizableWrapper? = null

    @JvmField
    var ordered: Boolean = false

    /* serialization */

    constructor(`val`: Hashtable<*, *>) : this(`val`, null)

    constructor(`val`: Hashtable<*, *>, keyType: ExternalizableWrapper?) {
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
        return ExtWrapMapPoly(`val` as Hashtable<*, *>, keyType)
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`)
        val h: Hashtable<Any, Any> =
            if (ordered) OrderedHashtable(size.toInt()) else Hashtable(size.toInt())
        for (i in 0 until size) {
            val key = ExtUtil.read(`in`, keyType!!, pf)
            val elem = ExtUtil.read(`in`, ExtWrapTagged(), pf)!!
            h[key] = elem
        }
        `val` = h
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val h = `val` as Hashtable<Any, Any>

        ExtUtil.writeNumeric(out, h.size.toLong())
        val e = h.keys()
        while (e.hasMoreElements()) {
            val key = e.nextElement()
            val elem = h[key]!!

            ExtUtil.write(out, if (keyType == null) key else keyType!!.clone(key))
            ExtUtil.write(out, ExtWrapTagged(elem))
        }
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        ordered = ExtUtil.readBool(`in`)
        keyType = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(IOException::class)
    override fun metaWriteExternal(out: DataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val h = `val` as Hashtable<Any, Any>

        ExtUtil.writeBool(out, ordered)

        val keyTagObj: Any = if (keyType == null) {
            if (h.isEmpty()) Any() else h.keys().nextElement()
        } else {
            keyType!!
        }
        ExtWrapTagged.writeTag(out, keyTagObj)
    }
}
