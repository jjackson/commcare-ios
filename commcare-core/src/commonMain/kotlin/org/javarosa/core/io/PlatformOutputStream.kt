@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * Platform-abstracted output stream for writing binary data.
 * On JVM, this is a typealias to java.io.OutputStream.
 * On iOS, this is a growable ByteArray-backed implementation.
 *
 * Do not construct directly — use [createByteArrayOutputStream] factory.
 */
expect abstract class PlatformOutputStream() {
    abstract fun write(v: Int)
    open fun write(b: ByteArray)
    open fun write(b: ByteArray, off: Int, len: Int)
    open fun flush()
    open fun close()
}

/**
 * Create a PlatformOutputStream that collects bytes in memory.
 * Use [byteArrayOutputStreamToBytes] to retrieve the collected bytes.
 */
expect fun createByteArrayOutputStream(): PlatformOutputStream

/**
 * Extract the collected bytes from a PlatformOutputStream created by [createByteArrayOutputStream].
 */
expect fun byteArrayOutputStreamToBytes(stream: PlatformOutputStream): ByteArray
