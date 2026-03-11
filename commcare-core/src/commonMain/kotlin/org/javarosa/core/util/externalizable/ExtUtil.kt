package org.javarosa.core.util.externalizable

import org.javarosa.core.util.Interner
import org.javarosa.core.util.OrderedMap
import org.javarosa.core.model.utils.PlatformDate

class ExtUtil {
    companion object {
        private const val interning = true
        private var stringCache: Interner<String>? = null

        fun serialize(o: Any): ByteArray {
            return serializeToBytes { dos -> write(dos, o) }
        }

        fun getSize(o: Any): Int {
            return serialize(o).size
        }

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
                else -> throw ClassCastException("Not a serializable datatype: " + data::class.simpleName)
            }
        }

        @Throws(PlatformIOException::class)
        fun writeNumeric(out: PlatformDataOutputStream, `val`: Long) {
            writeNumeric(out, `val`, ExtWrapIntEncodingUniform())
        }

        @Throws(PlatformIOException::class)
        fun writeNumeric(out: PlatformDataOutputStream, `val`: Long, encoding: ExtWrapIntEncoding) {
            write(out, encoding.clone(`val`))
        }

        @Throws(PlatformIOException::class)
        fun writeChar(out: PlatformDataOutputStream, `val`: Char) {
            out.writeChar(`val`.code)
        }

        @Throws(PlatformIOException::class)
        fun writeDecimal(out: PlatformDataOutputStream, `val`: Double) {
            out.writeDouble(`val`)
        }

        @Throws(PlatformIOException::class)
        fun writeBool(out: PlatformDataOutputStream, `val`: Boolean) {
            out.writeBoolean(`val`)
        }

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
        }

        @Throws(PlatformIOException::class)
        fun writeDate(out: PlatformDataOutputStream, `val`: PlatformDate) {
            writeNumeric(out, `val`.getTime())
        }

        @Throws(PlatformIOException::class)
        fun writeBytes(out: PlatformDataOutputStream, bytes: ByteArray) {
            writeNumeric(out, bytes.size.toLong())
            if (bytes.isNotEmpty())
                out.write(bytes)
        }

        @Throws(PlatformIOException::class, DeserializationException::class)
        fun read(
            `in`: PlatformDataInputStream,
            ew: ExternalizableWrapper,
            pf: PrototypeFactory?
        ): Any? {
            ew.readExternal(`in`, pf ?: defaultPrototypes())
            return ew.`val`
        }

        @Throws(PlatformIOException::class)
        fun readNumeric(`in`: PlatformDataInputStream): Long {
            return readNumeric(`in`, ExtWrapIntEncodingUniform())
        }

        @Throws(PlatformIOException::class)
        fun readNumeric(`in`: PlatformDataInputStream, encoding: ExtWrapIntEncoding): Long {
            try {
                return read(`in`, encoding, null) as Long
            } catch (de: DeserializationException) {
                throw RuntimeException("Shouldn't happen: Base-type encoding wrappers should never touch prototypes")
            }
        }

        @Throws(PlatformIOException::class)
        fun readInt(`in`: PlatformDataInputStream): Int {
            return toInt(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        fun readLong(`in`: PlatformDataInputStream): Long {
            return readNumeric(`in`)
        }

        @Throws(PlatformIOException::class)
        fun readShort(`in`: PlatformDataInputStream): Short {
            return toShort(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        fun readByte(`in`: PlatformDataInputStream): Byte {
            return toByte(readNumeric(`in`))
        }

        @Throws(PlatformIOException::class)
        fun readChar(`in`: PlatformDataInputStream): Char {
            return `in`.readChar()
        }

        @Throws(PlatformIOException::class)
        fun readDecimal(`in`: PlatformDataInputStream): Double {
            return `in`.readDouble()
        }

        @Throws(PlatformIOException::class)
        fun readBool(`in`: PlatformDataInputStream): Boolean {
            return `in`.readBoolean()
        }

        @Throws(PlatformIOException::class)
        fun readString(`in`: PlatformDataInputStream): String {
            val s = `in`.readUTF()
            return if (interning && stringCache != null) stringCache!!.intern(s) else s
        }

        @Throws(PlatformIOException::class)
        fun readDate(`in`: PlatformDataInputStream): PlatformDate {
            return PlatformDate(readNumeric(`in`))
        }

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

        fun toInt(l: Long): Int {
            if (l < Int.MIN_VALUE || l > Int.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into int")
            return l.toInt()
        }

        fun toShort(l: Long): Short {
            if (l < Short.MIN_VALUE || l > Short.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into short")
            return l.toShort()
        }

        fun toByte(l: Long): Byte {
            if (l < Byte.MIN_VALUE || l > Byte.MAX_VALUE)
                throw ArithmeticException("Value ($l) cannot fit into byte")
            return l.toByte()
        }

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

        fun nullIfEmpty(ba: ByteArray?): ByteArray? {
            return if (ba == null) null else if (ba.isEmpty()) null else ba
        }

        fun nullIfEmpty(s: String?): String? {
            return if (s == null) null else if (s.isEmpty()) null else s
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> nullIfEmpty(v: ArrayList<T>?): ArrayList<T>? {
            return if (v == null) null else if (v.size == 0) null else v
        }

        fun nullIfEmpty(h: HashMap<*, *>?): HashMap<*, *>? {
            return if (h == null) null else if (h.size == 0) null else h
        }

        fun emptyIfNull(ba: ByteArray?): ByteArray {
            return ba ?: ByteArray(0)
        }

        fun emptyIfNull(s: String?): String {
            return s ?: ""
        }

        fun emptyIfNull(v: ArrayList<*>?): ArrayList<*> {
            return v ?: ArrayList<Any?>()
        }

        fun emptyIfNull(h: HashMap<*, *>?): HashMap<*, *> {
            return h ?: HashMap<Any?, Any?>()
        }

        fun unwrap(o: Any?): Any? {
            return if (o is ExternalizableWrapper) o.baseValue() else o
        }

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

        fun hashtableEquals(a: HashMap<*, *>, b: HashMap<*, *>, unwrap: Boolean): Boolean {
            if (a.size != b.size) {
                return false
            } else if ((a is OrderedMap) != (b is OrderedMap)) {
                return false
            } else {
                val ea: Iterator<*> = a.keys.iterator()
                while (ea.hasNext()) {
                    val keyA = ea.next()
                    if (!equals(a[keyA], b[keyA], unwrap)) {
                        return false
                    }
                }

                if (a is OrderedMap && b is OrderedMap) {
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

        fun printBytes(data: ByteArray): String {
            val sb = StringBuilder()
            sb.append("[")
            for (i in data.indices) {
                var hex = (data[i].toInt() and 0xFF).toString(16)
                if (hex.length == 1)
                    hex = "0$hex"
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

        @Suppress("unused")
        fun attachCacheTable(stringCache: Interner<String>) {
            ExtUtil.stringCache = stringCache
        }
    }
}
