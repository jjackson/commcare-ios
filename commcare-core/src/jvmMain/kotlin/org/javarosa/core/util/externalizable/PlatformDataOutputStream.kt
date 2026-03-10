package org.javarosa.core.util.externalizable

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

actual class PlatformDataOutputStream actual constructor() {
    private val baos = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos)

    actual fun writeByte(v: Int) = dos.writeByte(v)
    actual fun writeInt(v: Int) = dos.writeInt(v)
    actual fun writeLong(v: Long) = dos.writeLong(v)
    actual fun writeChar(v: Int) = dos.writeChar(v)
    actual fun writeDouble(v: Double) = dos.writeDouble(v)
    actual fun writeBoolean(v: Boolean) = dos.writeBoolean(v)
    actual fun writeUTF(s: String) = dos.writeUTF(s)
    actual fun write(b: ByteArray) = dos.write(b)
    actual fun write(b: ByteArray, off: Int, len: Int) = dos.write(b, off, len)
    actual fun write(v: Int) = dos.write(v)
    actual fun flush() = dos.flush()
    actual fun close() = dos.close()

    actual fun toByteArray(): ByteArray {
        dos.flush()
        return baos.toByteArray()
    }
}
