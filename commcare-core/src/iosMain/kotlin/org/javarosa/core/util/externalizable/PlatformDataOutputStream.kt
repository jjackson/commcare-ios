@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

actual class PlatformDataOutputStream {
    private val buffer = mutableListOf<Byte>()

    actual fun writeByte(v: Int) {
        buffer.add(v.toByte())
    }

    actual fun writeInt(v: Int) {
        buffer.add((v shr 24).toByte())
        buffer.add((v shr 16).toByte())
        buffer.add((v shr 8).toByte())
        buffer.add(v.toByte())
    }

    actual fun writeLong(v: Long) {
        writeInt((v shr 32).toInt())
        writeInt(v.toInt())
    }

    actual fun writeChar(v: Int) {
        buffer.add((v shr 8).toByte())
        buffer.add(v.toByte())
    }

    actual fun writeDouble(v: Double) {
        writeLong(v.toBits())
    }

    actual fun writeBoolean(v: Boolean) {
        buffer.add(if (v) 1.toByte() else 0.toByte())
    }

    actual fun writeUTF(s: String) {
        // Java modified UTF-8: encode chars, then write 2-byte length prefix + bytes
        val utfBytes = encodeModifiedUtf8(s)
        if (utfBytes.size > 65535) {
            throw RuntimeException("UTF string too long: ${utfBytes.size} bytes")
        }
        // 2-byte unsigned length prefix
        buffer.add((utfBytes.size shr 8).toByte())
        buffer.add(utfBytes.size.toByte())
        for (b in utfBytes) {
            buffer.add(b)
        }
    }

    actual fun write(b: ByteArray) {
        for (byte in b) {
            buffer.add(byte)
        }
    }

    actual fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            buffer.add(b[i])
        }
    }

    actual fun write(v: Int) {
        buffer.add(v.toByte())
    }

    actual fun flush() {
        // No buffering beyond our list — nothing to flush
    }

    actual fun close() {
        // No resources to release
    }

    fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }

    private fun encodeModifiedUtf8(s: String): ByteArray {
        val result = mutableListOf<Byte>()
        for (ch in s) {
            val c = ch.code
            when {
                c == 0 -> {
                    result.add(0xC0.toByte())
                    result.add(0x80.toByte())
                }
                c in 1..0x7F -> {
                    result.add(c.toByte())
                }
                c in 0x80..0x7FF -> {
                    result.add((0xC0 or (c shr 6)).toByte())
                    result.add((0x80 or (c and 0x3F)).toByte())
                }
                else -> {
                    result.add((0xE0 or (c shr 12)).toByte())
                    result.add((0x80 or ((c shr 6) and 0x3F)).toByte())
                    result.add((0x80 or (c and 0x3F)).toByte())
                }
            }
        }
        return result.toByteArray()
    }
}

actual fun serializeToBytes(block: (PlatformDataOutputStream) -> Unit): ByteArray {
    val stream = PlatformDataOutputStream()
    block(stream)
    return stream.toByteArray()
}
