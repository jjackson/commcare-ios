package org.javarosa.core.util.externalizable

import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

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
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val type = readTag(`in`, pf)
        `val` = ExtUtil.read(`in`, type, pf)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        val localVal = `val`!!
        writeTag(out, localVal)
        ExtUtil.write(out, localVal)
    }

    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        throw RuntimeException("Tagged wrapper should never be tagged")
    }

    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        throw RuntimeException("Tagged wrapper should never be tagged")
    }

    companion object {
        private val WRAPPER_CODES: HashMap<KClass<*>, Int> = HashMap<KClass<*>, Int>().apply {
            put(ExtWrapNullable::class, 0x00)
            put(ExtWrapList::class, 0x20)
            put(ExtWrapListPoly::class, 0x21)
            put(ExtWrapMap::class, 0x22)
            put(ExtWrapMapPoly::class, 0x23)
            put(ExtWrapMultiMap::class, 0x24)
            put(ExtWrapIntEncodingUniform::class, 0x40)
            put(ExtWrapIntEncodingSmall::class, 0x41)
        }

        private val WRAPPER_FACTORIES: HashMap<Int, () -> ExternalizableWrapper> = HashMap<Int, () -> ExternalizableWrapper>().apply {
            put(0x00, { ExtWrapNullable() })
            put(0x20, { ExtWrapList() })
            put(0x21, { ExtWrapListPoly() })
            put(0x22, { ExtWrapMap() })
            put(0x23, { ExtWrapMapPoly() })
            put(0x24, { ExtWrapMultiMap() })
            put(0x40, { ExtWrapIntEncodingUniform() })
            put(0x41, { ExtWrapIntEncodingSmall() })
        }

        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun readTag(`in`: PlatformDataInputStream, pf: PrototypeFactory): ExternalizableWrapper {
            val tag = ByteArray(PrototypeFactory.getClassHashSize())
            `in`.read(tag, 0, tag.size)

            if (PrototypeFactory.compareHash(tag, PrototypeFactory.getWrapperTag())) {
                val wrapperCode = ExtUtil.readInt(`in`)

                val factory = WRAPPER_FACTORIES[wrapperCode]
                    ?: throw DeserializationException(
                        "Unrecognized ExternalizableWrapper type [$wrapperCode]"
                    )
                val type = factory()

                type.metaReadExternal(`in`, pf)
                return type
            } else {
                val className = pf.getClassName(tag)
                    ?: throw DeserializationException(
                        "No datatype registered to serialization code ${ExtUtil.printBytes(tag)}"
                    )

                return ExtWrapBase(classNameToKClass(className))
            }
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeTag(out: PlatformDataOutputStream, o: Any) {
            var obj = o
            if (obj is ExternalizableWrapper && obj !is ExtWrapBase) {
                out.write(PrototypeFactory.getWrapperTag(), 0, PrototypeFactory.getClassHashSize())
                ExtUtil.writeNumeric(out, WRAPPER_CODES[obj::class]!!.toLong())
                obj.metaWriteExternal(out)
            } else {
                var type: KClass<*>? = null

                if (obj is ExtWrapBase) {
                    val extType = obj
                    if (extType.`val` != null) {
                        obj = extType.`val`!!
                    } else {
                        type = extType.type
                    }
                }
                if (type == null) {
                    type = obj::class
                }

                val tag = PrototypeFactory.getClassHashForType(type!!)
                out.write(tag, 0, tag.size)
            }
        }
    }
}
