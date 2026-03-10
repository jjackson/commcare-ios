@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * iOS implementation of PlatformByteArrayOutputStream.
 * Collects bytes into an in-memory buffer.
 */
actual class PlatformByteArrayOutputStream {
    private val buffer = mutableListOf<Byte>()

    actual fun write(b: Int) {
        buffer.add(b.toByte())
    }

    actual fun write(b: ByteArray) {
        buffer.addAll(b.toList())
    }

    actual fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            buffer.add(b[i])
        }
    }

    actual fun toByteArray(): ByteArray = buffer.toByteArray()

    actual fun size(): Int = buffer.size

    actual fun close() {}
}
