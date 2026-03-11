package org.javarosa.core.util.externalizable

import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.model.utils.PlatformDate

/**
 * JVM-specific ExtUtil methods that require Class<*> for reflection-based
 * deserialization. The common ExtUtil methods are in commonMain.
 */
object JvmExtUtil {

    @JvmStatic
    @Throws(PlatformIOException::class, DeserializationException::class)
    fun read(
        `in`: PlatformDataInputStream,
        type: Class<*>,
        pf: PrototypeFactory?
    ): Any {
        return when {
            Externalizable::class.java.isAssignableFrom(type) -> {
                val ext = JvmPrototypeFactory.getInstance(type) as Externalizable
                ext.readExternal(`in`, pf ?: defaultPrototypes())
                ext
            }
            type == java.lang.Byte::class.java -> ExtUtil.readByte(`in`)
            type == java.lang.Short::class.java -> ExtUtil.readShort(`in`)
            type == java.lang.Integer::class.java -> ExtUtil.readInt(`in`)
            type == java.lang.Long::class.java -> ExtUtil.readNumeric(`in`)
            type == java.lang.Character::class.java -> ExtUtil.readChar(`in`)
            type == java.lang.Float::class.java -> ExtUtil.readDecimal(`in`).toFloat()
            type == java.lang.Double::class.java -> ExtUtil.readDecimal(`in`)
            type == java.lang.Boolean::class.java -> ExtUtil.readBool(`in`)
            type == String::class.java -> ExtUtil.readString(`in`)
            type == PlatformDate::class.java -> ExtUtil.readDate(`in`)
            type == ByteArray::class.java -> ExtUtil.readBytes(`in`)
            else -> throw ClassCastException("Not a deserializable datatype: " + type.name)
        }
    }

    // **REMOVE THIS FUNCTION**
    // original deserialization API; here for backwards compatibility
    @JvmStatic
    @Throws(PlatformIOException::class, DeserializationException::class)
    fun deserialize(data: ByteArray, type: Class<*>, pf: PrototypeFactory?): Any {
        return read(PlatformDataInputStream(createByteArrayInputStream(data)), type, pf)
    }
}
