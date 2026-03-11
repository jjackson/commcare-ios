@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * Platform-abstracted input stream for reading binary data.
 * On JVM, this is a typealias to java.io.InputStream.
 * On iOS, this is a ByteArray-backed implementation.
 *
 * Do not construct directly — use [createByteArrayInputStream] factory.
 */
expect abstract class PlatformInputStream() {
    abstract fun read(): Int
    open fun read(b: ByteArray): Int
    open fun read(b: ByteArray, off: Int, len: Int): Int
    open fun available(): Int
    open fun skip(n: Long): Long
    open fun close()
}

/**
 * Create a PlatformInputStream backed by the given ByteArray.
 */
expect fun createByteArrayInputStream(data: ByteArray): PlatformInputStream
