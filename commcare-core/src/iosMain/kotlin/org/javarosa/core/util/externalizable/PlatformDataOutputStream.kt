package org.javarosa.core.util.externalizable

actual class PlatformDataOutputStream actual constructor() {
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

    actual fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }

    private fun encodeModifiedUtf8(s: String): ByteArray {
        // Java modified UTF-8:
        // - Null (0x0000) → 0xC0 0x80 (two bytes, NOT single zero byte)
        // - 0x0001-0x007F → single byte
        // - 0x0080-0x07FF → two bytes: 110xxxxx 10xxxxxx
        // - 0x0800-0xFFFF → three bytes: 1110xxxx 10xxxxxx 10xxxxxx
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
