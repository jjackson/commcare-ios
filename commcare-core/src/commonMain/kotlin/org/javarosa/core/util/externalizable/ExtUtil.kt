@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import org.javarosa.core.model.utils.PlatformDate
import org.javarosa.core.util.Interner

expect class ExtUtil() {
    companion object {
        fun serialize(o: Any): ByteArray

        fun getSize(o: Any): Int

        fun defaultPrototypes(): PrototypeFactory

        @Throws(PlatformIOException::class)
        fun write(out: PlatformDataOutputStream, data: Any?)

        @Throws(PlatformIOException::class)
        fun writeNumeric(out: PlatformDataOutputStream, `val`: Long)

        @Throws(PlatformIOException::class)
        fun writeChar(out: PlatformDataOutputStream, `val`: Char)

        @Throws(PlatformIOException::class)
        fun writeDecimal(out: PlatformDataOutputStream, `val`: Double)

        @Throws(PlatformIOException::class)
        fun writeBool(out: PlatformDataOutputStream, `val`: Boolean)

        @Throws(PlatformIOException::class)
        fun writeString(out: PlatformDataOutputStream, `val`: String?)

        @Throws(PlatformIOException::class)
        fun writeDate(out: PlatformDataOutputStream, `val`: PlatformDate)

        @Throws(PlatformIOException::class)
        fun writeBytes(out: PlatformDataOutputStream, bytes: ByteArray)

        @Throws(PlatformIOException::class)
        fun readNumeric(`in`: PlatformDataInputStream): Long

        @Throws(PlatformIOException::class)
        fun readInt(`in`: PlatformDataInputStream): Int

        @Throws(PlatformIOException::class)
        fun readLong(`in`: PlatformDataInputStream): Long

        @Throws(PlatformIOException::class)
        fun readShort(`in`: PlatformDataInputStream): Short

        @Throws(PlatformIOException::class)
        fun readByte(`in`: PlatformDataInputStream): Byte

        @Throws(PlatformIOException::class)
        fun readChar(`in`: PlatformDataInputStream): Char

        @Throws(PlatformIOException::class)
        fun readDecimal(`in`: PlatformDataInputStream): Double

        @Throws(PlatformIOException::class)
        fun readBool(`in`: PlatformDataInputStream): Boolean

        @Throws(PlatformIOException::class)
        fun readString(`in`: PlatformDataInputStream): String

        @Throws(PlatformIOException::class)
        fun readDate(`in`: PlatformDataInputStream): PlatformDate

        @Throws(PlatformIOException::class)
        fun readBytes(`in`: PlatformDataInputStream): ByteArray

        fun toInt(l: Long): Int
        fun toShort(l: Long): Short
        fun toByte(l: Long): Byte
        fun toLong(o: Any): Long

        fun nullIfEmpty(ba: ByteArray?): ByteArray?
        fun nullIfEmpty(s: String?): String?
        fun <T> nullIfEmpty(v: ArrayList<T>?): ArrayList<T>?
        fun nullIfEmpty(h: HashMap<*, *>?): HashMap<*, *>?

        fun emptyIfNull(ba: ByteArray?): ByteArray
        fun emptyIfNull(s: String?): String
        fun emptyIfNull(v: ArrayList<*>?): ArrayList<*>
        fun emptyIfNull(h: HashMap<*, *>?): HashMap<*, *>

        fun unwrap(o: Any?): Any?

        fun equals(a: Any?, b: Any?, unwrap: Boolean): Boolean
        fun vectorEquals(a: ArrayList<*>, b: ArrayList<*>, unwrap: Boolean): Boolean
        fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean

        fun printBytes(data: ByteArray): String

        fun attachCacheTable(stringCache: Interner<String>)
    }
}
