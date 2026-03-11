package org.javarosa.core.util.externalizable

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

    // --- Nullable read/write ---

    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readNullableString(`in`: PlatformDataInputStream, pf: PrototypeFactory): String?

    @Throws(PlatformIOException::class)
    fun writeNullable(out: PlatformDataOutputStream, value: Any?)

    // --- Comparison utilities ---

    fun arrayEquals(a: Array<Any?>, b: Array<Any?>, unwrap: Boolean): Boolean

    fun nullEquals(a: Any?, b: Any?, unwrap: Boolean): Boolean
}
