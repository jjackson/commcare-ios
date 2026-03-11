package org.javarosa.core.util.externalizable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Cross-platform serialization round-trip tests.
 * These verify that Externalizable objects can be serialized/deserialized
 * across JVM and iOS using PlatformDataInputStream/PlatformDataOutputStream.
 */
class SerializationRoundTripTest {

    /**
     * Simple Externalizable for testing — serializes two fields.
     */
    class SimpleData : Externalizable {
        var name: String = ""
        var value: Int = 0

        constructor()

        constructor(name: String, value: Int) {
            this.name = name
            this.value = value
        }

        override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
            name = `in`.readUTF()
            value = `in`.readInt()
        }

        override fun writeExternal(out: PlatformDataOutputStream) {
            out.writeUTF(name)
            out.writeInt(value)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SimpleData) return false
            return name == other.name && value == other.value
        }

        override fun hashCode(): Int = name.hashCode() * 31 + value
    }

    @Test
    fun testSimpleExternalizableRoundTrip() {
        val original = SimpleData("test", 42)

        // Serialize
        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        // Deserialize
        val restored = SimpleData()
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals(original.name, restored.name)
        assertEquals(original.value, restored.value)
        assertEquals(original, restored)
    }

    @Test
    fun testExternalizableWithNestedData() {
        // Serialize multiple fields of various types
        val out = PlatformDataOutputStream()
        out.writeUTF("header")
        out.writeInt(3) // count
        for (i in 0 until 3) {
            out.writeUTF("item_$i")
            out.writeInt(i * 10)
        }
        out.writeBoolean(true) // footer flag
        val bytes = out.toByteArray()

        // Deserialize
        val inp = PlatformDataInputStream(bytes)
        assertEquals("header", inp.readUTF())
        val count = inp.readInt()
        assertEquals(3, count)
        for (i in 0 until count) {
            assertEquals("item_$i", inp.readUTF())
            assertEquals(i * 10, inp.readInt())
        }
        assertEquals(true, inp.readBoolean())
    }

    @Test
    fun testIntEncodingRoundTrip() {
        // Test ExtWrapIntEncodingUniform which is already in commonMain
        val encoding = ExtWrapIntEncodingUniform(42L)

        val out = PlatformDataOutputStream()
        encoding.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapIntEncodingUniform()
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals(42L, restored.`val`)
    }

    @Test
    fun testSmallIntEncodingRoundTrip() {
        // Test ExtWrapIntEncodingSmall
        val encoding = ExtWrapIntEncodingSmall(100L)

        val out = PlatformDataOutputStream()
        encoding.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapIntEncodingSmall()
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals(100L, restored.`val`)
    }

    @Test
    fun testSmallIntEncodingOverflow() {
        // Values outside the small range should use 4-byte encoding
        val encoding = ExtWrapIntEncodingSmall(1000L)

        val out = PlatformDataOutputStream()
        encoding.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapIntEncodingSmall()
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals(1000L, restored.`val`)
    }

    @Test
    fun testNegativeIntEncoding() {
        val encoding = ExtWrapIntEncodingUniform(-42L)

        val out = PlatformDataOutputStream()
        encoding.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapIntEncodingUniform()
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals(-42L, restored.`val`)
    }

    @Test
    fun testPrototypeFactoryCompanionMethods() {
        // Test the newly-exposed companion methods
        val hashSize = PrototypeFactory.getClassHashSize()
        assertNotNull(hashSize)

        val wrapperTag = PrototypeFactory.getWrapperTag()
        assertEquals(hashSize, wrapperTag.size)
        // Wrapper tag should be all 0xFF bytes
        for (b in wrapperTag) {
            assertEquals(0xFF.toByte(), b)
        }

        // compareHash should work correctly
        val a = byteArrayOf(1, 2, 3, 4)
        val b = byteArrayOf(1, 2, 3, 4)
        val c = byteArrayOf(1, 2, 3, 5)
        assertEquals(true, PrototypeFactory.compareHash(a, b))
        assertEquals(false, PrototypeFactory.compareHash(a, c))
    }

    @Test
    fun testUnicodeSerializationRoundTrip() {
        val original = SimpleData("日本語テスト 🌍", 999)

        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = SimpleData()
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals(original, restored)
    }
}
