package org.javarosa.core.util.externalizable

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream

actual class PlatformDataInputStream private constructor(internal val dis: DataInputStream) {

    /** CommonMain constructor: wrap a byte array. */
    actual constructor(data: ByteArray) : this(DataInputStream(ByteArrayInputStream(data)))

    /** JVM-only: wrap an existing DataInputStream (e.g., from InputStream-based sources). */
    constructor(inputStream: InputStream) : this(DataInputStream(inputStream))

    actual fun readByte(): Byte = dis.readByte()
    actual fun readInt(): Int = dis.readInt()
    actual fun readLong(): Long = dis.readLong()
    actual fun readChar(): Char = dis.readChar()
    actual fun readDouble(): Double = dis.readDouble()
    actual fun readBoolean(): Boolean = dis.readBoolean()
    actual fun readUTF(): String = dis.readUTF()
    actual fun readFully(b: ByteArray) = dis.readFully(b)
    actual fun read(b: ByteArray, off: Int, len: Int): Int = dis.read(b, off, len)
    actual fun available(): Int = dis.available()
    actual fun close() = dis.close()
}
