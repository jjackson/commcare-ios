@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

actual abstract class PlatformOutputStream {
    actual abstract fun write(v: Int)
    actual open fun write(b: ByteArray) { write(b, 0, b.size) }
    actual open fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            write(b[i].toInt())
        }
    }
    actual open fun flush() {}
    actual open fun close() {}
}

private class IosByteArrayOutputStream : PlatformOutputStream() {
    private val buffer = ArrayList<Byte>()

    override fun write(v: Int) {
        buffer.add(v.toByte())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            buffer.add(b[i])
        }
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}

actual fun createByteArrayOutputStream(): PlatformOutputStream =
    IosByteArrayOutputStream()

actual fun byteArrayOutputStreamToBytes(stream: PlatformOutputStream): ByteArray =
    (stream as IosByteArrayOutputStream).toByteArray()
