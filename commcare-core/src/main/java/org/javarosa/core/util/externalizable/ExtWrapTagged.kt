package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Hashtable

class ExtWrapTagged : ExternalizableWrapper {

    /* serialization */
    constructor(`val`: Any) {
        if (`val` is ExtWrapTagged) {
            throw IllegalArgumentException("Wrapping tagged with tagged is redundant")
        }
        this.`val` = `val`
    }

    /* deserialization */
    constructor()

    override fun clone(`val`: Any?): ExternalizableWrapper {
        return ExtWrapTagged(`val`!!)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        val type = readTag(`in`, pf)
        `val` = ExtUtil.read(`in`, type, pf)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        val localVal = `val`!!
        writeTag(out, localVal)
        ExtUtil.write(out, localVal)
    }

    override fun metaReadExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        throw RuntimeException("Tagged wrapper should never be tagged")
    }

    override fun metaWriteExternal(out: DataOutputStream) {
        throw RuntimeException("Tagged wrapper should never be tagged")
    }

    companion object {
        private val WRAPPER_CODES: Hashtable<Class<*>, Int> = Hashtable<Class<*>, Int>().apply {
            put(ExtWrapNullable::class.java, 0x00)
            put(ExtWrapList::class.java, 0x20)
            put(ExtWrapListPoly::class.java, 0x21)
            put(ExtWrapMap::class.java, 0x22)
            put(ExtWrapMapPoly::class.java, 0x23)
            put(ExtWrapMultiMap::class.java, 0x24)
            put(ExtWrapIntEncodingUniform::class.java, 0x40)
            put(ExtWrapIntEncodingSmall::class.java, 0x41)
        }

        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun readTag(`in`: DataInputStream, pf: PrototypeFactory): ExternalizableWrapper {
            val tag = ByteArray(PrototypeFactory.getClassHashSize())
            `in`.read(tag, 0, tag.size)

            if (PrototypeFactory.compareHash(tag, PrototypeFactory.getWrapperTag())) {
                val wrapperCode = ExtUtil.readInt(`in`)

                // find wrapper indicated by code
                var type: ExternalizableWrapper? = null
                val e = WRAPPER_CODES.keys()
                while (e.hasMoreElements()) {
                    val t = e.nextElement()
                    if (WRAPPER_CODES[t]!! == wrapperCode) {
                        try {
                            type = PrototypeFactory.getInstance(t) as ExternalizableWrapper
                        } catch (ccoe: CannotCreateObjectException) {
                            throw CannotCreateObjectException(
                                "Serious problem: cannot create built-in ExternalizableWrapper [${t.name}]"
                            )
                        }
                    }
                }
                if (type == null) {
                    throw DeserializationException(
                        "Unrecognized ExternalizableWrapper type [$wrapperCode]"
                    )
                }

                type.metaReadExternal(`in`, pf)
                return type
            } else {
                val type = pf.getClass(tag)
                    ?: throw DeserializationException(
                        "No datatype registered to serialization code ${ExtUtil.printBytes(tag)}"
                    )

                return ExtWrapBase(type)
            }
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeTag(out: DataOutputStream, o: Any) {
            var obj = o
            if (obj is ExternalizableWrapper && obj !is ExtWrapBase) {
                out.write(PrototypeFactory.getWrapperTag(), 0, PrototypeFactory.getClassHashSize())
                ExtUtil.writeNumeric(out, WRAPPER_CODES[obj.javaClass]!!.toLong())
                obj.metaWriteExternal(out)
            } else {
                var type: Class<*>? = null

                if (obj is ExtWrapBase) {
                    val extType = obj
                    if (extType.`val` != null) {
                        obj = extType.`val`!!
                    } else {
                        type = extType.type
                    }
                }
                if (type == null) {
                    type = obj.javaClass
                }

                val tag = PrototypeFactory.getClassHash(type) // cache this?
                out.write(tag, 0, tag.size)
            }
        }
    }
}
