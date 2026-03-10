package org.javarosa.core.util.externalizable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-platform tests for PlatformDataInputStream/PlatformDataOutputStream.
 * These run on both JVM and iOS to verify serialization binary compatibility.
 */
class PlatformDataStreamTest {

    @Test
    fun testByteRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeByte(0)
        out.writeByte(127)
        out.writeByte(-1)
        out.writeByte(255)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(0.toByte(), inp.readByte())
        assertEquals(127.toByte(), inp.readByte())
        assertEquals((-1).toByte(), inp.readByte())
        assertEquals((-1).toByte(), inp.readByte()) // 255 wraps to -1 as signed byte
        inp.close()
    }

    @Test
    fun testIntRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeInt(0)
        out.writeInt(1)
        out.writeInt(-1)
        out.writeInt(Int.MAX_VALUE)
        out.writeInt(Int.MIN_VALUE)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(0, inp.readInt())
        assertEquals(1, inp.readInt())
        assertEquals(-1, inp.readInt())
        assertEquals(Int.MAX_VALUE, inp.readInt())
        assertEquals(Int.MIN_VALUE, inp.readInt())
        inp.close()
    }

    @Test
    fun testLongRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeLong(0L)
        out.writeLong(1L)
        out.writeLong(-1L)
        out.writeLong(Long.MAX_VALUE)
        out.writeLong(Long.MIN_VALUE)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(0L, inp.readLong())
        assertEquals(1L, inp.readLong())
        assertEquals(-1L, inp.readLong())
        assertEquals(Long.MAX_VALUE, inp.readLong())
        assertEquals(Long.MIN_VALUE, inp.readLong())
        inp.close()
    }

    @Test
    fun testDoubleRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeDouble(0.0)
        out.writeDouble(1.5)
        out.writeDouble(-1.5)
        out.writeDouble(Double.MAX_VALUE)
        out.writeDouble(Double.MIN_VALUE)
        out.writeDouble(Double.NaN)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(0.0, inp.readDouble())
        assertEquals(1.5, inp.readDouble())
        assertEquals(-1.5, inp.readDouble())
        assertEquals(Double.MAX_VALUE, inp.readDouble())
        assertEquals(Double.MIN_VALUE, inp.readDouble())
        assertTrue(inp.readDouble().isNaN())
        inp.close()
    }

    @Test
    fun testBooleanRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeBoolean(true)
        out.writeBoolean(false)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(true, inp.readBoolean())
        assertEquals(false, inp.readBoolean())
        inp.close()
    }

    @Test
    fun testCharRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeChar('A'.code)
        out.writeChar('Z'.code)
        out.writeChar('\u00E9'.code) // é
        out.writeChar('\u4E16'.code) // 世
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals('A', inp.readChar())
        assertEquals('Z', inp.readChar())
        assertEquals('\u00E9', inp.readChar())
        assertEquals('\u4E16', inp.readChar())
        inp.close()
    }

    @Test
    fun testUTFRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeUTF("hello")
        out.writeUTF("")
        out.writeUTF("café")
        out.writeUTF("\u4E16\u754C") // 世界
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals("hello", inp.readUTF())
        assertEquals("", inp.readUTF())
        assertEquals("café", inp.readUTF())
        assertEquals("\u4E16\u754C", inp.readUTF())
        inp.close()
    }

    @Test
    fun testByteArrayRoundTrip() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 0, -1, -128, 127)
        val out = PlatformDataOutputStream()
        out.writeInt(original.size)
        out.write(original)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        val size = inp.readInt()
        val result = ByteArray(size)
        inp.readFully(result)
        assertEquals(original.toList(), result.toList())
        inp.close()
    }

    @Test
    fun testMixedTypesRoundTrip() {
        val out = PlatformDataOutputStream()
        out.writeInt(42)
        out.writeUTF("CommCare")
        out.writeBoolean(true)
        out.writeLong(1234567890123L)
        out.writeDouble(3.14159)
        out.writeChar('X'.code)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(42, inp.readInt())
        assertEquals("CommCare", inp.readUTF())
        assertEquals(true, inp.readBoolean())
        assertEquals(1234567890123L, inp.readLong())
        assertEquals(3.14159, inp.readDouble())
        assertEquals('X', inp.readChar())
        assertEquals(0, inp.available())
        inp.close()
    }

    @Test
    fun testAvailable() {
        val out = PlatformDataOutputStream()
        out.writeInt(1)
        out.writeInt(2)
        out.close()

        val inp = PlatformDataInputStream(out.toByteArray())
        assertEquals(8, inp.available())
        inp.readInt()
        assertEquals(4, inp.available())
        inp.readInt()
        assertEquals(0, inp.available())
        inp.close()
    }

    /**
     * Test that binary format matches Java's DataOutputStream.
     * This verifies cross-platform compatibility — data serialized on JVM
     * must be deserializable on iOS and vice versa.
     */
    @Test
    fun testBinaryFormatCompatibility() {
        val out = PlatformDataOutputStream()
        out.writeInt(0x01020304)
        out.close()
        val bytes = out.toByteArray()
        // Java DataOutputStream writes big-endian
        assertEquals(4, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
        assertEquals(0x03.toByte(), bytes[2])
        assertEquals(0x04.toByte(), bytes[3])
    }

    @Test
    fun testUTFBinaryFormat() {
        val out = PlatformDataOutputStream()
        out.writeUTF("AB")
        out.close()
        val bytes = out.toByteArray()
        // 2-byte length prefix (0x0002) + ASCII bytes
        assertEquals(4, bytes.size)
        assertEquals(0x00.toByte(), bytes[0]) // length high byte
        assertEquals(0x02.toByte(), bytes[1]) // length low byte
        assertEquals('A'.code.toByte(), bytes[2])
        assertEquals('B'.code.toByte(), bytes[3])
    }
}
