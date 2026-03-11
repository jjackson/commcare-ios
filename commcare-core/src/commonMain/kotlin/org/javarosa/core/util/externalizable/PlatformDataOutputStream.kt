@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Platform-abstracted data output stream for serializing binary data.
 * On JVM, this is a typealias to java.io.DataOutputStream.
 * On iOS, this is a manual big-endian encoder to an in-memory buffer.
 *
 * All multi-byte values use big-endian byte order to match Java's DataOutputStream format.
 * Strings use Java's modified UTF-8 encoding (2-byte length prefix + modified UTF-8 bytes).
 *
 * Do not construct directly in commonMain code — use [serializeToBytes] factory.
 */
expect class PlatformDataOutputStream {
    fun writeByte(v: Int)
    fun writeInt(v: Int)
    fun writeLong(v: Long)
    fun writeChar(v: Int)
    fun writeDouble(v: Double)
    fun writeBoolean(v: Boolean)
    fun writeUTF(s: String)
    fun write(b: ByteArray)
    fun write(b: ByteArray, off: Int, len: Int)
    fun write(v: Int)
    fun flush()
    fun close()
}

/**
 * Write to a PlatformDataOutputStream and return the resulting byte array.
 * On JVM, wraps a ByteArrayOutputStream+DataOutputStream.
 * On iOS, uses the in-memory buffer implementation.
 */
expect fun serializeToBytes(block: (PlatformDataOutputStream) -> Unit): ByteArray
