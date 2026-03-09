package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable

// map of objects where key and data are all of single (non-polymorphic) type
// (key and value can be of separate types)
class ExtWrapMap : ExternalizableWrapper {

    private var keyType: ExternalizableWrapper? = null

    @JvmField
    var dataType: ExternalizableWrapper? = null

    @JvmField
    var type: Int = 0

    constructor(`val`: Hashtable<*, *>) : this(`val`, null, null)

    constructor(`val`: Hashtable<*, *>, dataType: ExternalizableWrapper?) : this(`val`, null, dataType)

    constructor(`val`: Hashtable<*, *>, keyType: ExternalizableWrapper?, dataType: ExternalizableWrapper?) {
        requireNotNull(`val`)
        this.`val` = `val`
        this.keyType = keyType
        this.dataType = dataType
        type = if (`val` is OrderedHashtable<*, *>) TYPE_ORDERED else TYPE_NORMAL
    }

    constructor()

    constructor(keyType: Class<*>, dataType: Class<*>) : this(keyType, dataType, TYPE_NORMAL)

    constructor(keyType: Class<*>, dataType: ExternalizableWrapper) : this(ExtWrapBase(keyType), dataType, TYPE_NORMAL)

    constructor(keyType: ExternalizableWrapper, dataType: ExternalizableWrapper) : this(keyType, dataType, TYPE_NORMAL)

    constructor(keyType: Class<*>, dataType: Class<*>, type: Int) : this(ExtWrapBase(keyType), ExtWrapBase(dataType), type)

    constructor(keyType: ExternalizableWrapper, dataType: ExternalizableWrapper, type: Int) {
        this.keyType = keyType
        this.dataType = dataType
        this.type = type
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        @Suppress("UNCHECKED_CAST")
        return ExtWrapMap(`val` as Hashtable<*, *>, keyType, dataType)
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`)
        val h: Hashtable<Any, Any> = when (type) {
            TYPE_ORDERED -> OrderedHashtable(size.toInt())
            else -> Hashtable(size.toInt())
        }

        for (i in 0 until size) {
            val key = ExtUtil.read(`in`, keyType!!, pf)
            val elem = ExtUtil.read(`in`, dataType!!, pf)
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
            ExtUtil.write(out, if (dataType == null) elem else dataType!!.clone(elem))
        }
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        type = ExtUtil.readInt(`in`)
        keyType = ExtWrapTagged.readTag(`in`, pf)
        dataType = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(IOException::class)
    override fun metaWriteExternal(out: DataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val h = `val` as Hashtable<Any, Any>

        val keyTagObj: Any = if (keyType == null) {
            if (h.isEmpty()) Any() else h.keys().nextElement()
        } else {
            keyType!!
        }
        val elemTagObj: Any = if (dataType == null) {
            if (h.isEmpty()) Any() else h.elements().nextElement()
        } else {
            dataType!!
        }

        ExtUtil.writeNumeric(out, type.toLong())
        ExtWrapTagged.writeTag(out, keyTagObj)
        ExtWrapTagged.writeTag(out, elemTagObj)
    }

    companion object {
        private const val TYPE_NORMAL = 0

        const val TYPE_ORDERED = 1
    }
}
