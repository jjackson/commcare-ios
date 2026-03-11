@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import org.javarosa.core.util.externalizable.PlatformIOException

actual abstract class PlatformOutputStream {
    @Throws(PlatformIOException::class)
    actual abstract fun write(v: Int)
    @Throws(PlatformIOException::class)
    actual open fun write(b: ByteArray) { write(b, 0, b.size) }
    @Throws(PlatformIOException::class)
    actual open fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            write(b[i].toInt())
        }
    }
    @Throws(PlatformIOException::class)
    actual open fun flush() {}
    @Throws(PlatformIOException::class)
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
