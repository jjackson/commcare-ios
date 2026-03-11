package org.javarosa.core.io

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformStreamTest {

    @Test
    fun testByteArrayInputStreamReadAll() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createByteArrayInputStream(data)
        val result = ByteArray(5)
        val bytesRead = stream.read(result)
        assertEquals(5, bytesRead)
        assertEquals(data.toList(), result.toList())
        stream.close()
    }

    @Test
    fun testByteArrayInputStreamReadSingle() {
        val data = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val stream = createByteArrayInputStream(data)
        assertEquals(0xAB, stream.read())
        assertEquals(0xCD, stream.read())
        assertEquals(-1, stream.read())
        stream.close()
    }

    @Test
    fun testByteArrayInputStreamAvailable() {
        val data = byteArrayOf(1, 2, 3)
        val stream = createByteArrayInputStream(data)
        assertEquals(3, stream.available())
        stream.read()
        assertEquals(2, stream.available())
        stream.close()
    }

    @Test
    fun testByteArrayInputStreamSkip() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createByteArrayInputStream(data)
        val skipped = stream.skip(3)
        assertEquals(3, skipped)
        assertEquals(4, stream.read())
        stream.close()
    }

    @Test
    fun testByteArrayOutputStreamRoundTrip() {
        val stream = createByteArrayOutputStream()
        stream.write(42)
        stream.write(byteArrayOf(1, 2, 3))
        stream.write(byteArrayOf(10, 20, 30, 40, 50), 1, 3)
        val result = byteArrayOutputStreamToBytes(stream)
        assertEquals(listOf<Byte>(42, 1, 2, 3, 20, 30, 40), result.toList())
        stream.close()
    }

    @Test
    fun testInputOutputStreamCopy() {
        val sourceData = byteArrayOf(10, 20, 30, 40, 50)
        val input = createByteArrayInputStream(sourceData)
        val output = createByteArrayOutputStream()

        val buffer = ByteArray(3)
        var count = input.read(buffer)
        while (count != -1) {
            output.write(buffer, 0, count)
            count = input.read(buffer)
        }

        val result = byteArrayOutputStreamToBytes(output)
        assertEquals(sourceData.toList(), result.toList())
        input.close()
        output.close()
    }
}
