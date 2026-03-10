package org.javarosa.core.util.externalizable

/**
 * Platform-abstracted data input stream for deserializing binary data.
 * On JVM, delegates to java.io.DataInputStream. On iOS, uses manual big-endian decoding.
 *
 * All multi-byte values use big-endian byte order to match Java's DataInputStream format.
 * Strings use Java's modified UTF-8 encoding (2-byte length prefix + modified UTF-8 bytes).
 */
expect class PlatformDataInputStream(data: ByteArray) {
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
