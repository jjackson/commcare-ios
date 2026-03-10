package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

// map of objects where key and data are all of single (non-polymorphic) type
// (key and value can be of separate types)
class ExtWrapMap : ExternalizableWrapper {

    private var keyType: ExternalizableWrapper? = null

    @JvmField
    var dataType: ExternalizableWrapper? = null

    @JvmField
    var type: Int = 0

    constructor(`val`: HashMap<*, *>) : this(`val`, null, null)

    constructor(`val`: HashMap<*, *>, dataType: ExternalizableWrapper?) : this(`val`, null, dataType)

    constructor(`val`: HashMap<*, *>, keyType: ExternalizableWrapper?, dataType: ExternalizableWrapper?) {
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
        return ExtWrapMap(`val` as HashMap<*, *>, keyType, dataType)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val size = ExtUtil.readNumeric(`in`)
        val h: HashMap<Any, Any> = when (type) {
            TYPE_ORDERED -> OrderedHashtable(size.toInt())
            else -> HashMap(size.toInt())
        }

        for (i in 0 until size) {
            val key = ExtUtil.read(`in`, keyType!!, pf)!!
            val elem = ExtUtil.read(`in`, dataType!!, pf)!!
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
            ExtUtil.write(out, if (dataType == null) elem else dataType!!.clone(elem))
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        type = ExtUtil.readInt(`in`)
        keyType = ExtWrapTagged.readTag(`in`, pf)
        dataType = ExtWrapTagged.readTag(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val h = `val` as HashMap<Any, Any>

        val keyTagObj: Any = if (keyType == null) {
            if (h.isEmpty()) Any() else h.keys.iterator().next()
        } else {
            keyType!!
        }
        val elemTagObj: Any = if (dataType == null) {
            if (h.isEmpty()) Any() else h.values.iterator().next()
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
