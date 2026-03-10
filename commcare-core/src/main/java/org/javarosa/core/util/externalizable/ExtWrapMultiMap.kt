package org.javarosa.core.util.externalizable

import org.javarosa.core.util.ListMultimap
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class ExtWrapMultiMap : ExternalizableWrapper {

    private var keyType: ExternalizableWrapper? = null

    /* Constructors for serialization */

    constructor(`val`: ListMultimap<*, *>) : this(`val`, null)

    constructor(`val`: ListMultimap<*, *>, keyType: ExternalizableWrapper?) {
        requireNotNull(`val`)
        this.`val` = `val`
        this.keyType = keyType
    }

    /* Constructors for deserialization */

    constructor()

    constructor(keyType: Class<*>) : this(ExtWrapBase(keyType))

    constructor(keyType: ExternalizableWrapper) {
        requireNotNull(keyType)
        this.keyType = keyType
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        @Suppress("UNCHECKED_CAST")
        return ExtWrapMultiMap(`val` as ListMultimap<*, *>, keyType)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`)
        val multimap = ListMultimap<Any, Any>()
        for (i in 0 until size) {
            val key = ExtUtil.read(`in`, keyType!!, pf)!!
            val numberOfValues = ExtUtil.readNumeric(`in`)
            for (l in 0 until numberOfValues) {
                multimap.put(key, ExtUtil.read(`in`, ExtWrapTagged(), pf)!!)
            }
        }
        `val` = multimap
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val multimap = `val` as ListMultimap<Any, Any>
        ExtUtil.writeNumeric(out, multimap.keySet().size.toLong())
        for (key in multimap.keySet()) {
            ExtUtil.write(out, if (keyType == null) key else keyType!!.clone(key))
            val values = multimap[key]
            ExtUtil.writeNumeric(out, values.size.toLong())
            val valueIterator = values.iterator()
            while (valueIterator.hasNext()) {
                ExtUtil.write(out, ExtWrapTagged(valueIterator.next()))
            }
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        keyType = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: DataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val multimap = `val` as ListMultimap<Any, Any>
        val keyTagObj: Any = if (keyType == null) {
            if (multimap.isEmpty) Any() else multimap.keys().iterator().next()
        } else {
            keyType!!
        }
        ExtWrapTagged.writeTag(out, keyTagObj)
    }
}
