package org.javarosa.core.util.externalizable

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Tests that PlatformDataInputStream and PlatformDataOutputStream produce
 * byte-compatible output with java.io.DataInputStream/DataOutputStream.
 */
class PlatformStreamRoundTripTest {

    @Test
    fun testRoundTripByte() {
        val out = PlatformDataOutputStream()
        out.writeByte(0)
        out.writeByte(127)
        out.writeByte(-128)
        out.writeByte(255) // wraps to -1 as signed byte

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals(0.toByte(), `in`.readByte())
        assertEquals(127.toByte(), `in`.readByte())
        assertEquals((-128).toByte(), `in`.readByte())
        assertEquals((-1).toByte(), `in`.readByte())
        `in`.close()
    }

    @Test
    fun testRoundTripInt() {
        val out = PlatformDataOutputStream()
        out.writeInt(0)
        out.writeInt(1)
        out.writeInt(-1)
        out.writeInt(Int.MAX_VALUE)
        out.writeInt(Int.MIN_VALUE)
        out.writeInt(0x12345678)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals(0, `in`.readInt())
        assertEquals(1, `in`.readInt())
        assertEquals(-1, `in`.readInt())
        assertEquals(Int.MAX_VALUE, `in`.readInt())
        assertEquals(Int.MIN_VALUE, `in`.readInt())
        assertEquals(0x12345678, `in`.readInt())
        `in`.close()
    }

    @Test
    fun testRoundTripLong() {
        val out = PlatformDataOutputStream()
        out.writeLong(0L)
        out.writeLong(1L)
        out.writeLong(-1L)
        out.writeLong(Long.MAX_VALUE)
        out.writeLong(Long.MIN_VALUE)
        out.writeLong(0x123456789ABCDEF0L)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals(0L, `in`.readLong())
        assertEquals(1L, `in`.readLong())
        assertEquals(-1L, `in`.readLong())
        assertEquals(Long.MAX_VALUE, `in`.readLong())
        assertEquals(Long.MIN_VALUE, `in`.readLong())
        assertEquals(0x123456789ABCDEF0L, `in`.readLong())
        `in`.close()
    }

    @Test
    fun testRoundTripChar() {
        val out = PlatformDataOutputStream()
        out.writeChar('A'.code)
        out.writeChar('Z'.code)
        out.writeChar('\u00E9'.code) // e-acute
        out.writeChar('\u4E16'.code) // Chinese character

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals('A', `in`.readChar())
        assertEquals('Z', `in`.readChar())
        assertEquals('\u00E9', `in`.readChar())
        assertEquals('\u4E16', `in`.readChar())
        `in`.close()
    }

    @Test
    fun testRoundTripDouble() {
        val out = PlatformDataOutputStream()
        out.writeDouble(0.0)
        out.writeDouble(1.0)
        out.writeDouble(-1.0)
        out.writeDouble(Double.MAX_VALUE)
        out.writeDouble(Double.MIN_VALUE)
        out.writeDouble(Math.PI)
        out.writeDouble(Double.NaN)
        out.writeDouble(Double.POSITIVE_INFINITY)
        out.writeDouble(Double.NEGATIVE_INFINITY)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals(0.0, `in`.readDouble(), 0.0)
        assertEquals(1.0, `in`.readDouble(), 0.0)
        assertEquals(-1.0, `in`.readDouble(), 0.0)
        assertEquals(Double.MAX_VALUE, `in`.readDouble(), 0.0)
        assertEquals(Double.MIN_VALUE, `in`.readDouble(), 0.0)
        assertEquals(Math.PI, `in`.readDouble(), 0.0)
        assertTrue(java.lang.Double.isNaN(`in`.readDouble()))
        assertEquals(Double.POSITIVE_INFINITY, `in`.readDouble(), 0.0)
        assertEquals(Double.NEGATIVE_INFINITY, `in`.readDouble(), 0.0)
        `in`.close()
    }

    @Test
    fun testRoundTripBoolean() {
        val out = PlatformDataOutputStream()
        out.writeBoolean(true)
        out.writeBoolean(false)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertTrue(`in`.readBoolean())
        assertFalse(`in`.readBoolean())
        `in`.close()
    }

    @Test
    fun testRoundTripUTF() {
        val out = PlatformDataOutputStream()
        out.writeUTF("Hello, World!")
        out.writeUTF("")
        out.writeUTF("caf\u00E9")
        out.writeUTF("\u4E16\u754C") // "世界" (Chinese for "world")
        out.writeUTF("abc\u0000def") // embedded null (modified UTF-8 edge case)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals("Hello, World!", `in`.readUTF())
        assertEquals("", `in`.readUTF())
        assertEquals("caf\u00E9", `in`.readUTF())
        assertEquals("\u4E16\u754C", `in`.readUTF())
        assertEquals("abc\u0000def", `in`.readUTF())
        `in`.close()
    }

    @Test
    fun testRoundTripBytes() {
        val data = byteArrayOf(1, 2, 3, 4, 5, -1, -128, 127, 0)
        val out = PlatformDataOutputStream()
        out.write(data)

        val `in` = PlatformDataInputStream(out.toByteArray())
        val result = ByteArray(data.size)
        `in`.readFully(result)
        assertArrayEquals(data, result)
        `in`.close()
    }

    @Test
    fun testRoundTripPartialRead() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val out = PlatformDataOutputStream()
        out.write(data)

        val `in` = PlatformDataInputStream(out.toByteArray())
        val buf = ByteArray(3)
        val read = `in`.read(buf, 0, 3)
        assertEquals(3, read)
        assertEquals(10.toByte(), buf[0])
        assertEquals(20.toByte(), buf[1])
        assertEquals(30.toByte(), buf[2])
        assertEquals(2, `in`.available())
        `in`.close()
    }

    @Test
    fun testMixedTypes() {
        val out = PlatformDataOutputStream()
        out.writeInt(42)
        out.writeUTF("test")
        out.writeBoolean(true)
        out.writeDouble(3.14)
        out.writeLong(999999999999L)
        out.writeByte(0xFF)
        out.writeChar('X'.code)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals(42, `in`.readInt())
        assertEquals("test", `in`.readUTF())
        assertTrue(`in`.readBoolean())
        assertEquals(3.14, `in`.readDouble(), 0.0)
        assertEquals(999999999999L, `in`.readLong())
        assertEquals((-1).toByte(), `in`.readByte())
        assertEquals('X', `in`.readChar())
        assertEquals(0, `in`.available())
        `in`.close()
    }

    /**
     * Verify that Platform streams produce byte-identical output to java.io.Data*Stream.
     * This ensures cross-platform serialization compatibility.
     */
    @Test
    @Throws(Exception::class)
    fun testByteCompatibilityWithJavaIO() {
        // Write using java.io
        val baos = ByteArrayOutputStream()
        val javaDos = DataOutputStream(baos)
        javaDos.writeInt(42)
        javaDos.writeUTF("Hello")
        javaDos.writeBoolean(true)
        javaDos.writeDouble(Math.PI)
        javaDos.writeLong(Long.MAX_VALUE)
        javaDos.writeChar('Z'.code)
        javaDos.writeByte(99)
        javaDos.flush()
        val javaBytes = baos.toByteArray()

        // Write using Platform streams
        val platformDos = PlatformDataOutputStream()
        platformDos.writeInt(42)
        platformDos.writeUTF("Hello")
        platformDos.writeBoolean(true)
        platformDos.writeDouble(Math.PI)
        platformDos.writeLong(Long.MAX_VALUE)
        platformDos.writeChar('Z'.code)
        platformDos.writeByte(99)
        val platformBytes = platformDos.toByteArray()

        // Byte-identical output
        assertArrayEquals(javaBytes, platformBytes)

        // Read java.io bytes with Platform stream
        val platformDis = PlatformDataInputStream(javaBytes)
        assertEquals(42, platformDis.readInt())
        assertEquals("Hello", platformDis.readUTF())
        assertTrue(platformDis.readBoolean())
        assertEquals(Math.PI, platformDis.readDouble(), 0.0)
        assertEquals(Long.MAX_VALUE, platformDis.readLong())
        assertEquals('Z', platformDis.readChar())
        assertEquals(99.toByte(), platformDis.readByte())

        // Read Platform bytes with java.io
        val javaDis = DataInputStream(ByteArrayInputStream(platformBytes))
        assertEquals(42, javaDis.readInt())
        assertEquals("Hello", javaDis.readUTF())
        assertTrue(javaDis.readBoolean())
        assertEquals(Math.PI, javaDis.readDouble(), 0.0)
        assertEquals(Long.MAX_VALUE, javaDis.readLong())
        assertEquals('Z', javaDis.readChar())
        assertEquals(99.toByte(), javaDis.readByte())
    }

    @Test
    fun testWritePartialByteArray() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val out = PlatformDataOutputStream()
        out.write(data, 3, 4) // write bytes at indices 3,4,5,6

        val result = out.toByteArray()
        assertEquals(4, result.size)
        assertEquals(3.toByte(), result[0])
        assertEquals(4.toByte(), result[1])
        assertEquals(5.toByte(), result[2])
        assertEquals(6.toByte(), result[3])
    }

    @Test
    fun testWriteSingleByte() {
        val out = PlatformDataOutputStream()
        out.write(0xAB)

        val `in` = PlatformDataInputStream(out.toByteArray())
        assertEquals(0xAB.toByte(), `in`.readByte())
    }
}
