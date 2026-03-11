@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Platform-abstracted data input stream for deserializing binary data.
 * On JVM, this is a typealias to java.io.DataInputStream.
 * On iOS, this is a manual big-endian decoder from ByteArray.
 *
 * All multi-byte values use big-endian byte order to match Java's DataInputStream format.
 * Strings use Java's modified UTF-8 encoding (2-byte length prefix + modified UTF-8 bytes).
 *
 * Do not construct directly in commonMain code — use [createDataInputStream] factory.
 */
expect class PlatformDataInputStream {
    fun readByte(): Byte
    fun readInt(): Int
    fun readLong(): Long
    fun readChar(): Char
    fun readDouble(): Double
    fun readBoolean(): Boolean
    fun readUTF(): String
    fun readFully(b: ByteArray)
    fun read(b: ByteArray, off: Int, len: Int): Int
    fun available(): Int
    fun close()
}

/**
 * Create a PlatformDataInputStream from a ByteArray.
 */
expect fun createDataInputStream(data: ByteArray): PlatformDataInputStream
