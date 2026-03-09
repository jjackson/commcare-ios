package org.javarosa.core.util.externalizable

import org.javarosa.core.services.PrototypeManager
import org.javarosa.core.util.Interner
import org.javarosa.core.util.OrderedHashtable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.UTFDataFormatException
import java.util.Date
import java.util.Enumeration
import java.util.Hashtable
import java.util.Vector

class ExtUtil {
    companion object {
        private const val interning = true
        private var stringCache: Interner<String>? = null

        @JvmStatic
        fun serialize(o: Any): ByteArray {
            val baos = ByteArrayOutputStream()
            try {
                write(DataOutputStream(baos), o)
            } catch (ioe: IOException) {
                throw RuntimeException("IOException writing to ByteArrayOutputStream; shouldn't happen!")
            }
            return baos.toByteArray()
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
        @Throws(IOException::class)
        fun write(out: DataOutputStream, data: Any) {
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
                is Date -> writeDate(out, data)
                is ByteArray -> writeBytes(out, data)
                else -> throw ClassCastException("Not a serializable datatype: " + data.javaClass.name)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeNumeric(out: DataOutputStream, `val`: Long) {
            writeNumeric(out, `val`, ExtWrapIntEncodingUniform())
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeNumeric(out: DataOutputStream, `val`: Long, encoding: ExtWrapIntEncoding) {
            write(out, encoding.clone(`val`))
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeChar(out: DataOutputStream, `val`: Char) {
            out.writeChar(`val`.code)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeDecimal(out: DataOutputStream, `val`: Double) {
            out.writeDouble(`val`)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeBool(out: DataOutputStream, `val`: Boolean) {
            out.writeBoolean(`val`)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeString(out: DataOutputStream, `val`: String) {
            try {
                out.writeUTF(`val`)
            } catch (e: UTFDataFormatException) {
                val percentOversized =
                    ((`val`.toByteArray(Charsets.UTF_8).size / (Short.MAX_VALUE.toInt() * 2)) - 1) * 100
                throw SerializationLimitationException(
                    percentOversized,
                    e,
                    "Error while trying to write $`val` percentOversized: $percentOversized"
                )
            }
            // we could easily come up with more efficient default encoding for string
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeDate(out: DataOutputStream, `val`: Date) {
            writeNumeric(out, `val`.time)
            // time zone?
        }

        @JvmStatic
        @Throws(IOException::class)
        fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
            writeNumeric(out, bytes.size.toLong())
            if (bytes.isNotEmpty()) // i think writing zero-length array might close the stream
                out.write(bytes)
        }

        @JvmStatic
        @Throws(IOException::class, DeserializationException::class)
        fun read(
            `in`: DataInputStream,
            type: Class<*>,
            pf: PrototypeFactory?
        ): Any {
            return when {
                Externalizable::class.java.isAssignableFrom(type) -> {
                    val ext = PrototypeFactory.getInstance(type) as Externalizable
                    ext.readExternal(`in`, pf ?: defaultPrototypes())
                    ext
                }
                type == java.lang.Byte::class.java -> readByte(`in`)
                type == java.lang.Short::class.java -> readShort(`in`)
                type == java.lang.Integer::class.java -> readInt(`in`)
                type == java.lang.Long::class.java -> readNumeric(`in`)
                type == java.lang.Character::class.java -> readChar(`in`)
                type == java.lang.Float::class.java -> readDecimal(`in`).toFloat()
                type == java.lang.Double::class.java -> readDecimal(`in`)
                type == java.lang.Boolean::class.java -> readBool(`in`)
                type == String::class.java -> readString(`in`)
                type == Date::class.java -> readDate(`in`)
                type == ByteArray::class.java -> readBytes(`in`)
                else -> throw ClassCastException("Not a deserializable datatype: " + type.name)
            }
        }

        @JvmStatic
        @Throws(IOException::class, DeserializationException::class)
        fun read(
            `in`: DataInputStream,
            ew: ExternalizableWrapper,
            pf: PrototypeFactory?
        ): Any? {
            ew.readExternal(`in`, pf ?: defaultPrototypes())
            return ew.`val`
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readNumeric(`in`: DataInputStream): Long {
            return readNumeric(`in`, ExtWrapIntEncodingUniform())
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readNumeric(`in`: DataInputStream, encoding: ExtWrapIntEncoding): Long {
            try {
                return read(`in`, encoding, null) as Long
            } catch (de: DeserializationException) {
                throw RuntimeException("Shouldn't happen: Base-type encoding wrappers should never touch prototypes")
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readInt(`in`: DataInputStream): Int {
            return toInt(readNumeric(`in`))
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readLong(`in`: DataInputStream): Long {
            return readNumeric(`in`)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readShort(`in`: DataInputStream): Short {
            return toShort(readNumeric(`in`))
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readByte(`in`: DataInputStream): Byte {
            return toByte(readNumeric(`in`))
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readChar(`in`: DataInputStream): Char {
            return `in`.readChar()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readDecimal(`in`: DataInputStream): Double {
            return `in`.readDouble()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readBool(`in`: DataInputStream): Boolean {
            return `in`.readBoolean()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readString(`in`: DataInputStream): String {
            val s = `in`.readUTF()
            return if (interning && stringCache != null) stringCache!!.intern(s) else s
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readDate(`in`: DataInputStream): Date {
            return Date(readNumeric(`in`))
            // time zone?
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readBytes(`in`: DataInputStream): ByteArray {
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
        fun toInt(l: Long): Int {
            if (l < Int.MIN_VALUE || l > Int.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into int")
            return l.toInt()
        }

        @JvmStatic
        fun toShort(l: Long): Short {
            if (l < Short.MIN_VALUE || l > Short.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into short")
            return l.toShort()
        }

        @JvmStatic
        fun toByte(l: Long): Byte {
            if (l < Byte.MIN_VALUE || l > Byte.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into byte")
            return l.toByte()
        }

        @JvmStatic
        fun toLong(o: Any): Long {
            return when (o) {
                is Byte -> o.toLong()
                is Short -> o.toLong()
                is Int -> o.toLong()
                is Long -> o
                is Char -> o.code.toLong()
                else -> throw ClassCastException()
            }
        }

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
        fun <T> nullIfEmpty(v: Vector<T>?): Vector<T>? {
            return if (v == null) null else if (v.size == 0) null else v
        }

        @JvmStatic
        fun nullIfEmpty(h: Hashtable<*, *>?): Hashtable<*, *>? {
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
        fun emptyIfNull(v: Vector<*>?): Vector<*> {
            return v ?: Vector<Any?>()
        }

        @JvmStatic
        fun emptyIfNull(h: Hashtable<*, *>?): Hashtable<*, *> {
            return h ?: Hashtable<Any?, Any?>()
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
                    if (a is Vector<*>) {
                        return (b is Vector<*> && vectorEquals(a, b, unwrap))
                    } else if (a is Hashtable<*, *>) {
                        return (b is Hashtable<*, *> && hashtableEquals(a, b, unwrap))
                    }
                }
                return a == b
            }
        }

        @JvmStatic
        fun vectorEquals(a: Vector<*>, b: Vector<*>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else {
                for (i in 0 until a.size) {
                    if (!equals(a.elementAt(i), b.elementAt(i), unwrap)) {
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
        fun hashtableEquals(a: Hashtable<*, *>, b: Hashtable<*, *>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else if ((a is OrderedHashtable<*, *>) != (b is OrderedHashtable<*, *>)) {
                return false
            } else {
                val ea: Enumeration<*> = a.keys()
                while (ea.hasMoreElements()) {
                    val keyA = ea.nextElement()
                    if (!equals(a[keyA], b[keyA], unwrap)) {
                        return false
                    }
                }

                if (a is OrderedHashtable<*, *> && b is OrderedHashtable<*, *>) {
                    val eaOrdered: Enumeration<*> = a.keys()
                    val ebOrdered: Enumeration<*> = b.keys()

                    while (eaOrdered.hasMoreElements()) {
                        val keyA = eaOrdered.nextElement()
                        val keyB = ebOrdered.nextElement()

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
            val sb = StringBuffer()
            sb.append("[")
            for (i in data.indices) {
                var hex = Integer.toHexString(data[i].toInt())
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

        // **REMOVE THIS FUNCTION**
        // original deserialization API (whose limits made us make this whole new framework!); here for backwards compatibility
        @JvmStatic
        @Throws(IOException::class, DeserializationException::class)
        fun deserialize(data: ByteArray, type: Class<*>, pf: PrototypeFactory?): Any {
            return read(DataInputStream(ByteArrayInputStream(data)), type, pf)
        }

        @Suppress("unused")
        @JvmStatic
        fun attachCacheTable(stringCache: Interner<String>) {
            ExtUtil.stringCache = stringCache
        }
    }
}
