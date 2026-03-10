package org.javarosa.core.util.externalizable

import java.io.ByteArrayInputStream
import java.io.DataInputStream

actual class PlatformDataInputStream actual constructor(data: ByteArray) {
    private val dis = DataInputStream(ByteArrayInputStream(data))

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
