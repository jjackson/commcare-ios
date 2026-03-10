package org.javarosa.core.util.externalizable

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

actual class PlatformDataOutputStream {
    internal val dos: DataOutputStream
    private val baos: ByteArrayOutputStream?

    /** CommonMain constructor: write to an in-memory buffer. */
    actual constructor() {
        val buffer = ByteArrayOutputStream()
        this.baos = buffer
        this.dos = DataOutputStream(buffer)
    }

    /** JVM-only: wrap an existing OutputStream. toByteArray() will throw. */
    constructor(outputStream: OutputStream) {
        this.baos = null
        this.dos = DataOutputStream(outputStream)
    }

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
        return baos?.toByteArray() ?: throw UnsupportedOperationException(
            "toByteArray() not supported for stream-based PlatformDataOutputStream"
        )
    }
}
