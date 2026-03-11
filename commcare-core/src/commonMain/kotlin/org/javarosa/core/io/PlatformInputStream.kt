@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Platform-abstracted input stream for reading binary data.
 * On JVM, this is a typealias to java.io.InputStream.
 * On iOS, this is a ByteArray-backed implementation.
 *
 * Do not construct directly — use [createByteArrayInputStream] factory.
 */
expect abstract class PlatformInputStream() {
    @Throws(PlatformIOException::class)
    abstract fun read(): Int
    @Throws(PlatformIOException::class)
    open fun read(b: ByteArray): Int
    @Throws(PlatformIOException::class)
    open fun read(b: ByteArray, off: Int, len: Int): Int
    @Throws(PlatformIOException::class)
    open fun available(): Int
    @Throws(PlatformIOException::class)
    open fun skip(n: Long): Long
    @Throws(PlatformIOException::class)
    open fun close()
}

/**
 * Create a PlatformInputStream backed by the given ByteArray.
 */
expect fun createByteArrayInputStream(data: ByteArray): PlatformInputStream
