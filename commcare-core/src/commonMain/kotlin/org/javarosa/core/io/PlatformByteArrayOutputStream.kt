@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * Cross-platform in-memory output stream that collects bytes into a byte array.
 * On JVM, wraps java.io.ByteArrayOutputStream. On iOS, uses a MutableList<Byte> buffer.
 */
expect class PlatformByteArrayOutputStream() {
    fun write(b: Int)
    fun write(b: ByteArray)
    fun write(b: ByteArray, off: Int, len: Int)
    fun toByteArray(): ByteArray
    fun size(): Int
    fun close()
}
