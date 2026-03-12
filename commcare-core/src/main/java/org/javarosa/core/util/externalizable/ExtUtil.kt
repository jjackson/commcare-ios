package org.javarosa.core.util.externalizable

import org.javarosa.core.services.PrototypeManager
import org.javarosa.core.util.Interner
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.utils.PlatformDate
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

class ExtUtil {
    companion object {
        private const val interning = true
        private var stringCache: Interner<String>? = null

        @JvmStatic
        fun serialize(o: Any): ByteArray {
            val dos = PlatformDataOutputStream()
            try {
                write(dos, o)
            } catch (ioe: PlatformIOException) {
                throw RuntimeException("PlatformIOException writing to ByteArrayOutputStream; shouldn't happen!")
            }
            return dos.toByteArray()
        }

        @JvmStatic
        fun getSize(o: Any): Int {
            return serialize(o).size
        }

        @JvmStatic
        fun defaultPrototypes(): PrototypeFactory {
            return PrototypeManager.getDefault()!!
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun write(out: PlatformDataOutputStream, data: Any?) {
            if (data == null) throw NullPointerException("Cannot serialize null data")
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            when (data) {
                is Externalizable -> data.writeExternal(out)
                is Byte -> writeNumeric(out, data.toLong())
                is Short -> writeNumeric(out, data.toLong())
                is Int -> writeNumeric(out, data.toLong())
                is Long -> writeNumeric(out, data)
                is Char -> writeChar(out, data)
                is Float -> writeDecimal(out, data.toDouble())
                is Double -> writeDecimal(out, data)
                is Boolean -> writeBool(out, data)
                is String -> writeString(out, data)
                is PlatformDate -> writeDate(out, data)
                is ByteArray -> writeBytes(out, data)
                else -> throw ClassCastException("Not a serializable datatype: " + (data::class.qualifiedName ?: data::class.toString()))
            }
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeNumeric(out: PlatformDataOutputStream, `val`: Long) {
            writeNumeric(out, `val`, ExtWrapIntEncodingUniform())
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeNumeric(out: PlatformDataOutputStream, `val`: Long, encoding: ExtWrapIntEncoding) {
            write(out, encoding.clone(`val`))
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeChar(out: PlatformDataOutputStream, `val`: Char) {
            out.writeChar(`val`.code)
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeDecimal(out: PlatformDataOutputStream, `val`: Double) {
            out.writeDouble(`val`)
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeBool(out: PlatformDataOutputStream, `val`: Boolean) {
            out.writeBoolean(`val`)
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeString(out: PlatformDataOutputStream, `val`: String?) {
            try {
                out.writeUTF(`val`!!)
            } catch (e: PlatformIOException) {
                val percentOversized =
                    ((`val`!!.encodeToByteArray().size / (Short.MAX_VALUE.toInt() * 2)) - 1) * 100
                throw SerializationLimitationException(
                    percentOversized,
                    e,
                    "Error while trying to write $`val` percentOversized: $percentOversized"
                )
            }
            // we could easily come up with more efficient default encoding for string
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeDate(out: PlatformDataOutputStream, `val`: PlatformDate) {
            writeNumeric(out, `val`.time)
            // time zone?
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun writeBytes(out: PlatformDataOutputStream, bytes: ByteArray) {
            writeNumeric(out, bytes.size.toLong())
            if (bytes.isNotEmpty()) // i think writing zero-length array might close the stream
                out.write(bytes)
        }

        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun read(
            `in`: PlatformDataInputStream,
            type: KClass<*>,
            pf: PrototypeFactory?
        ): Any {
            return when (type) {
                Byte::class -> readByte(`in`)
                Short::class -> readShort(`in`)
                Int::class -> readInt(`in`)
                Long::class -> readNumeric(`in`)
                Char::class -> readChar(`in`)
                Float::class -> readDecimal(`in`).toFloat()
                Double::class -> readDecimal(`in`)
                Boolean::class -> readBool(`in`)
                String::class -> readString(`in`)
                PlatformDate::class -> readDate(`in`)
                ByteArray::class -> readBytes(`in`)
                else -> {
                    // Must be an Externalizable type — create via JVM reflection
                    val o = type.java.getDeclaredConstructor().newInstance()
                    (o as Externalizable).readExternal(`in`, pf ?: defaultPrototypes())
                    o
                }
            }
        }

        /**
         * JVM backward-compatible overload accepting Class<*>.
         * Uses Class.newInstance() directly for Externalizable types (matching original behavior).
         */
        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun read(
            `in`: PlatformDataInputStream,
            type: Class<*>,
            pf: PrototypeFactory?
        ): Any {
            return when (type.kotlin) {
                Byte::class -> readByte(`in`)
                Short::class -> readShort(`in`)
                Int::class -> readInt(`in`)
                Long::class -> readNumeric(`in`)
                Char::class -> readChar(`in`)
                Float::class -> readDecimal(`in`).toFloat()
                Double::class -> readDecimal(`in`)
                Boolean::class -> readBool(`in`)
                String::class -> readString(`in`)
                PlatformDate::class -> readDate(`in`)
                ByteArray::class -> readBytes(`in`)
                else -> {
                    val o = PrototypeFactory.getInstance(type)
                    (o as Externalizable).readExternal(`in`, pf ?: defaultPrototypes())
                    o
                }
            }
        }

        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun read(
            `in`: PlatformDataInputStream,
            ew: ExternalizableWrapper,
            pf: PrototypeFactory?
        ): Any? {
            ew.readExternal(`in`, pf ?: defaultPrototypes())
            return ew.`val`
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readNumeric(`in`: PlatformDataInputStream): Long {
            return readNumeric(`in`, ExtWrapIntEncodingUniform())
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readNumeric(`in`: PlatformDataInputStream, encoding: ExtWrapIntEncoding): Long {
            try {
                return read(`in`, encoding, null) as Long
            } catch (de: DeserializationException) {
                throw RuntimeException("Shouldn't happen: Base-type encoding wrappers should never touch prototypes")
            }
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readInt(`in`: PlatformDataInputStream): Int {
            return toInt(readNumeric(`in`))
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readLong(`in`: PlatformDataInputStream): Long {
            return readNumeric(`in`)
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readShort(`in`: PlatformDataInputStream): Short {
            return toShort(readNumeric(`in`))
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readByte(`in`: PlatformDataInputStream): Byte {
            return toByte(readNumeric(`in`))
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readChar(`in`: PlatformDataInputStream): Char {
            return `in`.readChar()
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readDecimal(`in`: PlatformDataInputStream): Double {
            return `in`.readDouble()
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readBool(`in`: PlatformDataInputStream): Boolean {
            return `in`.readBoolean()
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readString(`in`: PlatformDataInputStream): String {
            val s = `in`.readUTF()
            return if (interning && stringCache != null) stringCache!!.intern(s) else s
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readDate(`in`: PlatformDataInputStream): PlatformDate {
            return PlatformDate(readNumeric(`in`))
            // time zone?
        }

        @JvmStatic
        @Throws(PlatformIOException::class)
        fun readBytes(`in`: PlatformDataInputStream): ByteArray {
            val size = readNumeric(`in`).toInt()
            val bytes = ByteArray(size)
            var read = 0
            var toread = size
            while (read != size) {
                read = `in`.read(bytes, 0, toread)
                toread -= read
            }
            return bytes
        }

        @JvmStatic
        fun toInt(l: Long): Int = numericToInt(l)

        @JvmStatic
        fun toShort(l: Long): Short = numericToShort(l)

        @JvmStatic
        fun toByte(l: Long): Byte = numericToByte(l)

        @JvmStatic
        fun toLong(o: Any): Long = numericToLong(o)

        @JvmStatic
        fun nullIfEmpty(ba: ByteArray?): ByteArray? {
            return if (ba == null) null else if (ba.isEmpty()) null else ba
        }

        @JvmStatic
        fun nullIfEmpty(s: String?): String? {
            return if (s == null) null else if (s.isEmpty()) null else s
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> nullIfEmpty(v: ArrayList<T>?): ArrayList<T>? {
            return if (v == null) null else if (v.size == 0) null else v
        }

        @JvmStatic
        fun nullIfEmpty(h: HashMap<*, *>?): HashMap<*, *>? {
            return if (h == null) null else if (h.size == 0) null else h
        }

        @JvmStatic
        fun emptyIfNull(ba: ByteArray?): ByteArray {
            return ba ?: ByteArray(0)
        }

        @JvmStatic
        fun emptyIfNull(s: String?): String {
            return s ?: ""
        }

        @JvmStatic
        fun emptyIfNull(v: ArrayList<*>?): ArrayList<*> {
            return v ?: ArrayList<Any?>()
        }

        @JvmStatic
        fun emptyIfNull(h: HashMap<*, *>?): HashMap<*, *> {
            return h ?: HashMap<Any?, Any?>()
        }

        @JvmStatic
        fun unwrap(o: Any?): Any? {
            return if (o is ExternalizableWrapper) o.baseValue() else o
        }

        @JvmStatic
        fun equals(a: Any?, b: Any?, unwrap: Boolean): Boolean {
            @Suppress("NAME_SHADOWING")
            var a = a
            @Suppress("NAME_SHADOWING")
            var b = b
            if (unwrap) {
                a = if (a != null) unwrap(a) else null
                b = if (b != null) unwrap(b) else null
            }

            if (a == null) {
                return b == null
            } else {
                if (unwrap) {
                    if (a is ArrayList<*>) {
                        return (b is ArrayList<*> && vectorEquals(a, b, unwrap))
                    } else if (a is HashMap<*, *>) {
                        return (b is HashMap<*, *> && hashtableEquals(a, b, unwrap))
                    }
                }
                return a == b
            }
        }

        @JvmStatic
        fun vectorEquals(a: ArrayList<*>, b: ArrayList<*>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else {
                for (i in 0 until a.size) {
                    if (!equals(a[i], b[i], unwrap)) {
                        return false
                    }
                }
                return true
            }
        }

        @JvmStatic
        fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else {
                for (i in a.indices) {
                    if (!equals(a[i], b[i], unwrap)) {
                        return false
                    }
                }
                return true
            }
        }

        @JvmStatic
        fun hashtableEquals(a: HashMap<*, *>, b: HashMap<*, *>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else if ((a is OrderedHashtable<*, *>) != (b is OrderedHashtable<*, *>)) {
                return false
            } else {
                val ea: Iterator<*> = a.keys.iterator()
                while (ea.hasNext()) {
                    val keyA = ea.next()
                    if (!equals(a[keyA], b[keyA], unwrap)) {
                        return false
                    }
                }

                if (a is OrderedHashtable<*, *> && b is OrderedHashtable<*, *>) {
                    val eaOrdered: Iterator<*> = a.keys.iterator()
                    val ebOrdered: Iterator<*> = b.keys.iterator()

                    while (eaOrdered.hasNext()) {
                        val keyA = eaOrdered.next()
                        val keyB = ebOrdered.next()

                        if (keyA != keyB) { // must use built-in equals for keys, as that's what hashtable uses
                            return false
                        }
                    }
                }

                return true
            }
        }

        @JvmStatic
        fun printBytes(data: ByteArray): String {
            val sb = StringBuilder()
            sb.append("[")
            for (i in data.indices) {
                var hex = data[i].toInt().toString(16)
                if (hex.length == 1)
                    hex = "0$hex"
                else
                    hex = hex.substring(hex.length - 2)
                sb.append(hex)
                if (i < data.size - 1) {
                    if ((i + 1) % 30 == 0)
                        sb.append("\n ")
                    else if ((i + 1) % 10 == 0)
                        sb.append("  ")
                    else
                        sb.append(" ")
                }
            }
            sb.append("]")
            return sb.toString()
        }

        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun deserialize(data: ByteArray, type: KClass<*>, pf: PrototypeFactory?): Any {
            return read(PlatformDataInputStream(data), type, pf)
        }

        /**
         * JVM backward-compatible overload accepting Class<*>.
         */
        @JvmStatic
        @Throws(PlatformIOException::class, DeserializationException::class)
        fun deserialize(data: ByteArray, type: Class<*>, pf: PrototypeFactory?): Any {
            return read(PlatformDataInputStream(data), type, pf)
        }

        @Suppress("unused")
        @JvmStatic
        fun attachCacheTable(stringCache: Interner<String>) {
            ExtUtil.stringCache = stringCache
        }
    }
}
