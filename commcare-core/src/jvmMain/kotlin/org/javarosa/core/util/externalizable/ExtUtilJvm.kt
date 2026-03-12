package org.javarosa.core.util.externalizable

import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.model.utils.PlatformDate
import kotlin.jvm.JvmStatic

/**
 * JVM-only extensions for ExtUtil providing backward-compatible Class<*> overloads.
 */
object ExtUtilJvm {
    /**
     * JVM backward-compatible overload accepting Class<*>.
     * Uses PrototypeFactory.getInstance(Class) for Externalizable types.
     */
    @JvmStatic
    @Throws(PlatformIOException::class, DeserializationException::class)
    fun read(
        `in`: PlatformDataInputStream,
        type: Class<*>,
        pf: PrototypeFactory?
    ): Any {
        return when (type.kotlin) {
            Byte::class -> ExtUtil.readByte(`in`)
            Short::class -> ExtUtil.readShort(`in`)
            Int::class -> ExtUtil.readInt(`in`)
            Long::class -> ExtUtil.readNumeric(`in`)
            Char::class -> ExtUtil.readChar(`in`)
            Float::class -> ExtUtil.readDecimal(`in`).toFloat()
            Double::class -> ExtUtil.readDecimal(`in`)
            Boolean::class -> ExtUtil.readBool(`in`)
            String::class -> ExtUtil.readString(`in`)
            PlatformDate::class -> ExtUtil.readDate(`in`)
            ByteArray::class -> ExtUtil.readBytes(`in`)
            else -> {
                val o = PrototypeFactory.getInstance(type)
                (o as Externalizable).readExternal(`in`, pf ?: ExtUtil.defaultPrototypes())
                o
            }
        }
    }

    /**
     * JVM backward-compatible deserialize accepting Class<*>.
     */
    @JvmStatic
    @Throws(PlatformIOException::class, DeserializationException::class)
    fun deserialize(data: ByteArray, type: Class<*>, pf: PrototypeFactory?): Any {
        return read(PlatformDataInputStream(data), type, pf)
    }

    /**
     * OrderedHashtable-aware hashtable equality check (JVM-only).
     * The commonMain version doesn't check ordered key equality.
     */
    @JvmStatic
    fun hashtableEqualsOrdered(a: HashMap<*, *>, b: HashMap<*, *>, unwrap: Boolean): Boolean {
        if (a.size != b.size) {
            return false
        } else if ((a is OrderedHashtable<*, *>) != (b is OrderedHashtable<*, *>)) {
            return false
        } else {
            val ea: Iterator<*> = a.keys.iterator()
            while (ea.hasNext()) {
                val keyA = ea.next()
                if (!ExtUtil.equals(a[keyA], b[keyA], unwrap)) {
                    return false
                }
            }

            if (a is OrderedHashtable<*, *> && b is OrderedHashtable<*, *>) {
                val eaOrdered: Iterator<*> = a.keys.iterator()
                val ebOrdered: Iterator<*> = b.keys.iterator()

                while (eaOrdered.hasNext()) {
                    val keyA = eaOrdered.next()
                    val keyB = ebOrdered.next()

                    if (keyA != keyB) {
                        return false
                    }
                }
            }

            return true
        }
    }
}
