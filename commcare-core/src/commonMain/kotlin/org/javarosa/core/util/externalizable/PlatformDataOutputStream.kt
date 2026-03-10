package org.javarosa.core.util.externalizable

/**
 * Platform-abstracted data output stream for serializing binary data to an in-memory buffer.
 * On JVM, delegates to java.io.DataOutputStream wrapping ByteArrayOutputStream.
 * On iOS, uses manual big-endian encoding.
 *
 * All multi-byte values use big-endian byte order to match Java's DataOutputStream format.
 * Strings use Java's modified UTF-8 encoding (2-byte length prefix + modified UTF-8 bytes).
 */
expect class PlatformDataOutputStream() {
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
    fun toByteArray(): ByteArray
}
