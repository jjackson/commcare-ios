package org.javarosa.core.util.externalizable

import org.javarosa.core.model.utils.PlatformDate

/**
 * Cross-platform serialization helpers that replace JVM-only ExtUtil calls.
 * These functions are wire-format compatible with the JVM ExtUtil/ExtWrap framework.
 *
 * On JVM, the actual implementation delegates to ExtUtil for binary compatibility.
 * On iOS, it implements the serialization protocol directly.
 */
expect object SerializationHelpers {

    // --- Primitive read/write ---

    @Throws(PlatformIOException::class)
    fun readNumeric(`in`: PlatformDataInputStream): Long

    @Throws(PlatformIOException::class)
    fun writeNumeric(out: PlatformDataOutputStream, v: Long)

    @Throws(PlatformIOException::class)
    fun readInt(`in`: PlatformDataInputStream): Int

    @Throws(PlatformIOException::class)
    fun readString(`in`: PlatformDataInputStream): String

    @Throws(PlatformIOException::class)
    fun writeString(out: PlatformDataOutputStream, s: String)

    @Throws(PlatformIOException::class)
    fun readBool(`in`: PlatformDataInputStream): Boolean

    @Throws(PlatformIOException::class)
    fun writeBool(out: PlatformDataOutputStream, b: Boolean)

    @Throws(PlatformIOException::class)
    fun readDecimal(`in`: PlatformDataInputStream): Double

    @Throws(PlatformIOException::class)
    fun writeDecimal(out: PlatformDataOutputStream, d: Double)

    // --- Object write (dispatches by type) ---

    @Throws(PlatformIOException::class)
    fun write(out: PlatformDataOutputStream, data: Any)

    // --- Typed Externalizable read/write ---

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun <T : Externalizable> readExternalizable(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): T

    // --- Tagged (polymorphic) read/write ---

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readTagged(`in`: PlatformDataInputStream, pf: PrototypeFactory): Any

    @Throws(PlatformIOException::class)
    fun writeTagged(out: PlatformDataOutputStream, obj: Any)

    // --- List read/write ---

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readListPoly(`in`: PlatformDataInputStream, pf: PrototypeFactory): ArrayList<Any?>

    @Throws(PlatformIOException::class)
    fun writeListPoly(out: PlatformDataOutputStream, list: List<*>)

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun <T : Externalizable> readList(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): ArrayList<T>

    @Throws(PlatformIOException::class)
    fun writeList(out: PlatformDataOutputStream, list: List<*>)

    // --- Date read/write ---

    @Throws(PlatformIOException::class)
    fun readDate(`in`: PlatformDataInputStream): PlatformDate

    @Throws(PlatformIOException::class)
    fun writeDate(out: PlatformDataOutputStream, date: PlatformDate)

    // --- String list read ---

    @Throws(PlatformIOException::class)
    fun readStringList(`in`: PlatformDataInputStream): ArrayList<String>

    // --- Map read/write ---

    @Throws(PlatformIOException::class)
    fun readStringStringMap(`in`: PlatformDataInputStream): HashMap<String, String>

    @Throws(PlatformIOException::class)
    fun writeMap(out: PlatformDataOutputStream, map: HashMap<*, *>)

    // --- Nullable read/write ---

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readNullableString(`in`: PlatformDataInputStream, pf: PrototypeFactory): String?

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun <T : Externalizable> readNullableExternalizable(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): T?

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readNullableTagged(`in`: PlatformDataInputStream, pf: PrototypeFactory): Any?

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readNullableDate(`in`: PlatformDataInputStream): PlatformDate?

    @Throws(PlatformIOException::class)
    fun writeNullable(out: PlatformDataOutputStream, value: Any?)

    @Throws(PlatformIOException::class)
    fun writeNullableTagged(out: PlatformDataOutputStream, value: Any?)

    // --- Typed map read/write ---

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun <T : Externalizable> readStringExtMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): HashMap<String, T>

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readStringTaggedMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory
    ): HashMap<String, Any>

    @Throws(PlatformIOException::class)
    fun writeTaggedMap(out: PlatformDataOutputStream, map: HashMap<*, *>)

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun <T : Externalizable> readOrderedStringExtMap(
        `in`: PlatformDataInputStream,
        pf: PrototypeFactory,
        creator: () -> T
    ): LinkedHashMap<String, T>

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readOrderedStringStringMap(
        `in`: PlatformDataInputStream
    ): LinkedHashMap<String, String>

    // --- Comparison utilities ---

    fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean

    fun nullEquals(a: Any?, b: Any?, unwrap: Boolean): Boolean
}
