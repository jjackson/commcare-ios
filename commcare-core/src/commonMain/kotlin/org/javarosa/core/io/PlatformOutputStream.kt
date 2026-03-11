@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Platform-abstracted output stream for writing binary data.
 * On JVM, this is a typealias to java.io.OutputStream.
 * On iOS, this is a growable ByteArray-backed implementation.
 *
 * Do not construct directly — use [createByteArrayOutputStream] factory.
 */
expect abstract class PlatformOutputStream() {
    @Throws(PlatformIOException::class)
    abstract fun write(v: Int)
    @Throws(PlatformIOException::class)
    open fun write(b: ByteArray)
    @Throws(PlatformIOException::class)
    open fun write(b: ByteArray, off: Int, len: Int)
    @Throws(PlatformIOException::class)
    open fun flush()
    @Throws(PlatformIOException::class)
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
