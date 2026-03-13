package org.javarosa.core.util.externalizable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for ExternalizableWrapper types.
 * Tests wrappers that don't require PrototypeFactory class registration.
 */
class TaggedSerializationTest {

    @Test
    fun testExtWrapNullableWithValueRoundTrip() {
        val original = ExtWrapNullable("hello")

        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapNullable(String::class)
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertEquals("hello", restored.`val`)
    }

    @Test
    fun testExtWrapNullableWithNullRoundTrip() {
        val original = ExtWrapNullable(null as String?)

        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapNullable(String::class)
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        assertNull(restored.`val`)
    }

    @Test
    fun testExtWrapListRoundTrip() {
        val items = ArrayList<Any>()
        items.add("alpha")
        items.add("beta")
        items.add("gamma")

        val original = ExtWrapList(items)

        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapList(String::class)
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        val list = restored.`val` as List<*>
        assertEquals(3, list.size)
        assertEquals("alpha", list[0])
        assertEquals("beta", list[1])
        assertEquals("gamma", list[2])
    }

    @Test
    fun testExtWrapListEmptyRoundTrip() {
        val original = ExtWrapList(ArrayList<Any>())

        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapList(String::class)
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        val list = restored.`val` as List<*>
        assertEquals(0, list.size)
    }

    @Test
    fun testExtWrapMapRoundTrip() {
        val map = LinkedHashMap<Any, Any>()
        map["key1"] = "value1"
        map["key2"] = "value2"

        val original = ExtWrapMap(map)

        val out = PlatformDataOutputStream()
        original.writeExternal(out)
        val bytes = out.toByteArray()

        val restored = ExtWrapMap(String::class, String::class)
        val inp = PlatformDataInputStream(bytes)
        restored.readExternal(inp, PrototypeFactory())

        val result = restored.`val` as Map<*, *>
        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
    }

    @Test
    fun testExtWrapIntEncodingUniformNegativeValues() {
        for (value in listOf(-1L, -100L, -10000L, Long.MIN_VALUE)) {
            val original = ExtWrapIntEncodingUniform(value)

            val out = PlatformDataOutputStream()
            original.writeExternal(out)
            val bytes = out.toByteArray()

            val restored = ExtWrapIntEncodingUniform()
            val inp = PlatformDataInputStream(bytes)
            restored.readExternal(inp, PrototypeFactory())

            assertEquals(value, restored.`val`, "Failed for value $value")
        }
    }

    @Test
    fun testExtWrapIntEncodingUniformLargeValues() {
        for (value in listOf(0L, 1L, 255L, 256L, 65535L, 65536L, Int.MAX_VALUE.toLong(), Long.MAX_VALUE)) {
            val original = ExtWrapIntEncodingUniform(value)

            val out = PlatformDataOutputStream()
            original.writeExternal(out)
            val bytes = out.toByteArray()

            val restored = ExtWrapIntEncodingUniform()
            val inp = PlatformDataInputStream(bytes)
            restored.readExternal(inp, PrototypeFactory())

            assertEquals(value, restored.`val`, "Failed for value $value")
        }
    }

    @Test
    fun testWriteTagWritesHashForPlainObject() {
        // writeTag for a String should write the class hash (32 bytes)
        val out = PlatformDataOutputStream()
        ExtWrapTagged.writeTag(out, "test")
        val bytes = out.toByteArray()

        assertEquals(PrototypeFactory.getClassHashSize(), bytes.size)

        // Verify the tag matches the expected hash
        val expectedHash = PrototypeFactory.getClassHashForType(String::class)
        for (i in bytes.indices) {
            assertEquals(expectedHash[i], bytes[i], "Hash mismatch at byte $i")
        }
    }

    @Test
    fun testWriteTagWritesWrapperTagForWrapper() {
        // writeTag for an ExtWrapNullable should write the wrapper tag + code
        val out = PlatformDataOutputStream()
        ExtWrapTagged.writeTag(out, ExtWrapNullable("test"))
        val bytes = out.toByteArray()

        // Should start with the wrapper tag (32 bytes of 0xFF)
        val wrapperTag = PrototypeFactory.getWrapperTag()
        assertTrue(bytes.size > wrapperTag.size, "Output should be larger than wrapper tag")
        for (i in wrapperTag.indices) {
            assertEquals(wrapperTag[i], bytes[i], "Wrapper tag mismatch at byte $i")
        }
    }
}
