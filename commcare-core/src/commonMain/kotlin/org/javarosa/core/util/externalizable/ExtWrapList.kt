package org.javarosa.core.util.externalizable

import kotlin.jvm.JvmField
import kotlin.reflect.KClass

// List of objects of single (non-polymorphic) type
class ExtWrapList : ExternalizableWrapper {

    @JvmField
    var type: ExternalizableWrapper? = null

    private var sealed: Boolean = false
    private var listFactory: (() -> MutableList<Any?>)? = null
    private var listTag: String? = null

    /* Constructors for serialization */

    constructor(list: List<*>) : this(list, null)

    constructor(list: List<*>, type: ExternalizableWrapper?) {
        requireNotNull(list)
        this.`val` = list
        this.type = type
        this.listTag = list::class.qualifiedName ?: ARRAYLIST_TAG
        this.listFactory = LIST_FACTORIES[listTag] ?: { ArrayList() }
    }

    /* Constructors for deserialization */

    constructor()

    constructor(listElementType: KClass<*>) {
        this.type = ExtWrapBase(listElementType)
        this.listFactory = { ArrayList() }
        this.listTag = ARRAYLIST_TAG
        this.sealed = false
    }

    constructor(listElementType: KClass<*>, listFactory: () -> MutableList<Any?>, listTag: String = ARRAYLIST_TAG) {
        this.type = ExtWrapBase(listElementType)
        this.listFactory = listFactory
        this.listTag = listTag
        this.sealed = false
    }

    constructor(type: ExternalizableWrapper) {
        requireNotNull(type)
        this.type = type
        this.listFactory = { ArrayList() }
        this.listTag = ARRAYLIST_TAG
    }

    constructor(type: ExternalizableWrapper, listFactory: () -> MutableList<Any?>, listTag: String = ARRAYLIST_TAG) {
        requireNotNull(type)
        this.type = type
        this.listFactory = listFactory
        this.listTag = listTag
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        @Suppress("UNCHECKED_CAST")
        return ExtWrapList(`val` as List<*>, type)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        if (!sealed) {
            val size = ExtUtil.readNumeric(`in`).toInt()
            val factory = listFactory ?: { ArrayList() }
            val l: MutableList<Any?> = if (listTag == ARRAYLIST_TAG) {
                ArrayList(size)
            } else {
                factory()
            }
            for (i in 0 until size) {
                l.add(ExtUtil.read(`in`, type!!, pf))
            }
            `val` = l
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
        val tag = ExtUtil.readString(`in`)
        listTag = tag
        listFactory = LIST_FACTORIES[tag] ?: { ArrayList() }
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
        ExtUtil.writeString(out, listTag ?: ARRAYLIST_TAG)
    }

    companion object {
        private const val ARRAYLIST_TAG = "java.util.ArrayList"

        /** Registry of known list implementations by class name. */
        internal val LIST_FACTORIES: HashMap<String, () -> MutableList<Any?>> = HashMap<String, () -> MutableList<Any?>>().apply {
            put("java.util.ArrayList", { ArrayList() })
            put("kotlin.collections.ArrayList", { ArrayList() })
        }
    }
}
