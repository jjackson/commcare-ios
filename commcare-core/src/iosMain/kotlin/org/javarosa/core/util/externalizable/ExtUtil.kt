@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import org.javarosa.core.services.PrototypeManager
import org.javarosa.core.util.Interner
import org.javarosa.core.model.utils.PlatformDate

actual class ExtUtil {
    actual companion object {
        private const val interning = true
        private var stringCache: Interner<String>? = null

        actual fun serialize(o: Any): ByteArray {
            val pdos = PlatformDataOutputStream()
            try {
                write(pdos, o)
            } catch (ioe: PlatformIOException) {
                throw RuntimeException("PlatformIOException writing to ByteArrayOutputStream; shouldn't happen!")
            }
            return pdos.toByteArray()
        }

        actual fun getSize(o: Any): Int {
            return serialize(o).size
        }

        actual fun defaultPrototypes(): PrototypeFactory {
            return PrototypeManager.getDefault()!!
        }

        @Throws(PlatformIOException::class)
        actual fun write(out: PlatformDataOutputStream, data: Any?) {
            if (data == null) throw NullPointerException("Cannot serialize null data")
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
                else -> throw ClassCastException("Not a serializable datatype: " + (data::class.simpleName ?: "unknown"))
            }
        }

        @Throws(PlatformIOException::class)
        actual fun writeNumeric(out: PlatformDataOutputStream, `val`: Long) {
            // Inline ExtWrapIntEncodingUniform encoding: variable-length 7-bit chunks
            val l = `val`
            var sig = -1
            var k: Long
            do {
                sig++
                k = l shr (sig * 7)
            } while (k < (-1L shl 6) || k > (1L shl 6) - 1)

            for (i in sig downTo 0) {
                val chunk = ((l shr (i * 7)) and 0x7f).toByte()
                out.writeByte((if (i > 0) 0x80 else 0x00) or chunk.toInt())
            }
        }

        @Throws(PlatformIOException::class)
        actual fun writeChar(out: PlatformDataOutputStream, `val`: Char) {
            out.writeChar(`val`.code)
        }

        @Throws(PlatformIOException::class)
        actual fun writeDecimal(out: PlatformDataOutputStream, `val`: Double) {
            out.writeDouble(`val`)
        }

        @Throws(PlatformIOException::class)
        actual fun writeBool(out: PlatformDataOutputStream, `val`: Boolean) {
            out.writeBoolean(`val`)
        }

        @Throws(PlatformIOException::class)
        actual fun writeString(out: PlatformDataOutputStream, `val`: String?) {
            try {
                out.writeUTF(`val`!!)
            } catch (e: PlatformIOException) {
                val bytes = `val`!!.encodeToByteArray()
                val percentOversized =
                    ((bytes.size / (Short.MAX_VALUE.toInt() * 2)) - 1) * 100
                throw SerializationLimitationException(
                    percentOversized,
                    e,
                    "Error while trying to write $`val` percentOversized: $percentOversized"
                )
            }
        }

        @Throws(PlatformIOException::class)
        actual fun writeDate(out: PlatformDataOutputStream, `val`: PlatformDate) {
            writeNumeric(out, `val`.getTime())
        }

        @Throws(PlatformIOException::class)
        actual fun writeBytes(out: PlatformDataOutputStream, bytes: ByteArray) {
            writeNumeric(out, bytes.size.toLong())
            if (bytes.isNotEmpty())
                out.write(bytes)
        }

        @Throws(PlatformIOException::class)
        actual fun readNumeric(`in`: PlatformDataInputStream): Long {
            // Inline ExtWrapIntEncodingUniform decoding: variable-length 7-bit chunks
            var l: Long = 0
            var b: Byte
            var firstByte = true

            do {
                b = `in`.readByte()

                if (firstByte) {
                    firstByte = false
                    l = if (((b.toInt() shr 6) and 0x01) == 0) 0L else -1L
                }

                l = (l shl 7) or (b.toLong() and 0x7f)
            } while (((b.toInt() shr 7) and 0x01) == 1)

            return l
        }

        @Throws(PlatformIOException::class)
        actual fun readInt(`in`: PlatformDataInputStream): Int {
            return toInt(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        actual fun readLong(`in`: PlatformDataInputStream): Long {
            return readNumeric(`in`)
        }

        @Throws(PlatformIOException::class)
        actual fun readShort(`in`: PlatformDataInputStream): Short {
            return toShort(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        actual fun readByte(`in`: PlatformDataInputStream): Byte {
            return toByte(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        actual fun readChar(`in`: PlatformDataInputStream): Char {
            return `in`.readChar()
        }

        @Throws(PlatformIOException::class)
        actual fun readDecimal(`in`: PlatformDataInputStream): Double {
            return `in`.readDouble()
        }

        @Throws(PlatformIOException::class)
        actual fun readBool(`in`: PlatformDataInputStream): Boolean {
            return `in`.readBoolean()
        }

        @Throws(PlatformIOException::class)
        actual fun readString(`in`: PlatformDataInputStream): String {
            val s = `in`.readUTF()
            return if (interning && stringCache != null) stringCache!!.intern(s) else s
        }

        @Throws(PlatformIOException::class)
        actual fun readDate(`in`: PlatformDataInputStream): PlatformDate {
            return PlatformDate(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        actual fun readBytes(`in`: PlatformDataInputStream): ByteArray {
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

        actual fun toInt(l: Long): Int {
            if (l < Int.MIN_VALUE || l > Int.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into int")
            return l.toInt()
        }

        actual fun toShort(l: Long): Short {
            if (l < Short.MIN_VALUE || l > Short.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into short")
            return l.toShort()
        }

        actual fun toByte(l: Long): Byte {
            if (l < Byte.MIN_VALUE || l > Byte.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into byte")
            return l.toByte()
        }

        actual fun toLong(o: Any): Long {
            return when (o) {
                is Byte -> o.toLong()
                is Short -> o.toLong()
                is Int -> o.toLong()
                is Long -> o
                is Char -> o.code.toLong()
                else -> throw ClassCastException()
            }
        }

        actual fun nullIfEmpty(ba: ByteArray?): ByteArray? {
            return if (ba == null) null else if (ba.isEmpty()) null else ba
        }

        actual fun nullIfEmpty(s: String?): String? {
            return if (s == null) null else if (s.isEmpty()) null else s
        }

        @Suppress("UNCHECKED_CAST")
        actual fun <T> nullIfEmpty(v: ArrayList<T>?): ArrayList<T>? {
            return if (v == null) null else if (v.size == 0) null else v
        }

        actual fun nullIfEmpty(h: HashMap<*, *>?): HashMap<*, *>? {
            return if (h == null) null else if (h.size == 0) null else h
        }

        actual fun emptyIfNull(ba: ByteArray?): ByteArray {
            return ba ?: ByteArray(0)
        }

        actual fun emptyIfNull(s: String?): String {
            return s ?: ""
        }

        actual fun emptyIfNull(v: ArrayList<*>?): ArrayList<*> {
            return v ?: ArrayList<Any?>()
        }

        actual fun emptyIfNull(h: HashMap<*, *>?): HashMap<*, *> {
            return h ?: HashMap<Any?, Any?>()
        }

        actual fun unwrap(o: Any?): Any? {
            // On iOS, ExternalizableWrapper doesn't exist in this source set.
            // Just return the object as-is.
            return o
        }

        actual fun equals(a: Any?, b: Any?, unwrap: Boolean): Boolean {
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

        actual fun vectorEquals(a: ArrayList<*>, b: ArrayList<*>, unwrap: Boolean): Boolean {
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

        actual fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean {
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

        private fun hashtableEquals(a: HashMap<*, *>, b: HashMap<*, *>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else {
                val ea: Iterator<*> = a.keys.iterator()
                while (ea.hasNext()) {
                    val keyA = ea.next()
                    if (!equals(a[keyA], b[keyA], unwrap)) {
                        return false
                    }
                }
                return true
            }
        }

        actual fun printBytes(data: ByteArray): String {
            val sb = StringBuilder()
            sb.append("[")
            for (i in data.indices) {
                var hex = (data[i].toInt() and 0xff).toString(16)
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

        actual fun attachCacheTable(stringCache: Interner<String>) {
            ExtUtil.stringCache = stringCache
        }
    }
}
