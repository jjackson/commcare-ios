package org.javarosa.core.util.externalizable

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

// List of objects of single (non-polymorphic) type
class ExtWrapList : ExternalizableWrapper {

    @JvmField
    var type: ExternalizableWrapper? = null

    private var sealed: Boolean = false
    private var listImplementation: Class<out List<*>>? = null

    /* Constructors for serialization */

    constructor(list: List<*>) : this(list, null)

    constructor(list: List<*>, type: ExternalizableWrapper?) {
        requireNotNull(list)
        this.`val` = list
        this.type = type
        @Suppress("UNCHECKED_CAST")
        this.listImplementation = list.javaClass as Class<out List<*>>
    }

    /* Constructors for deserialization */

    constructor()

    // Assumes that the list implementation is a ArrayList, since that is most common
    constructor(listElementType: Class<*>) : this(listElementType, ArrayList::class.java)

    @Suppress("UNCHECKED_CAST")
    constructor(listElementType: Class<*>, listImplementation: Class<*>) {
        this.type = ExtWrapBase(listElementType)
        this.listImplementation = listImplementation as Class<out List<*>>
        this.sealed = false
    }

    // Assumes that the list implementation is a ArrayList, since that is most common
    constructor(type: ExternalizableWrapper) : this(type, ArrayList::class.java)

    @Suppress("UNCHECKED_CAST")
    constructor(type: ExternalizableWrapper, listImplementation: Class<*>) {
        requireNotNull(type)
        this.listImplementation = listImplementation as Class<out List<*>>
        this.type = type
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        @Suppress("UNCHECKED_CAST")
        return ExtWrapList(`val` as List<*>, type)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        if (!sealed) {
            val size = ExtUtil.readNumeric(`in`).toInt()
            try {
                val l: MutableList<Any?> = if (listImplementation == ArrayList::class.java) {
                    // to preserve performance gains of instantiating a ArrayList with its size
                    ArrayList(size)
                } else {
                    @Suppress("DEPRECATION")
                    listImplementation!!.newInstance() as MutableList<Any?>
                }
                for (i in 0 until size) {
                    l.add(ExtUtil.read(`in`, type!!, pf))
                }
                `val` = l
            } catch (e: InstantiationException) {
                throw DeserializationException(e.message!!)
            } catch (e: IllegalAccessException) {
                throw DeserializationException(e.message!!)
            }
        } else {
            val size = ExtUtil.readNumeric(`in`).toInt()
            val theval = arrayOfNulls<Any>(size)
            for (i in 0 until size) {
                theval[i] = ExtUtil.read(`in`, type!!, pf)
            }
            `val` = theval
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        @Suppress("UNCHECKED_CAST")
        val l = `val` as List<Any?>
        ExtUtil.writeNumeric(out, l.size.toLong())
        for (i in l.indices) {
            ExtUtil.write(out, if (type == null) l[i]!! else type!!.clone(l[i]))
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        type = ExtWrapTagged.readTag(`in`, pf)
        try {
            @Suppress("UNCHECKED_CAST")
            listImplementation = Class.forName(ExtUtil.readString(`in`)) as Class<out List<*>>
        } catch (e: ClassNotFoundException) {
            throw DeserializationException(e.message!!)
        }
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        val tagObj: Any? = if (type == null) {
            @Suppress("UNCHECKED_CAST")
            val l = `val` as List<Any?>
            if (l.isEmpty()) {
                Any()
            } else {
                l[0]
            }
        } else {
            type
        }

        ExtWrapTagged.writeTag(out, tagObj!!)
        ExtUtil.writeString(out, listImplementation!!.name)
    }
}
