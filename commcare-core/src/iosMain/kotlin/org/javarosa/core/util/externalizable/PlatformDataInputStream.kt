package org.javarosa.core.util.externalizable

actual class PlatformDataInputStream actual constructor(data: ByteArray) {
    private val buffer = data
    private var pos = 0

    actual fun readByte(): Byte {
        checkAvailable(1)
        return buffer[pos++]
    }

    actual fun readInt(): Int {
        checkAvailable(4)
        val b0 = buffer[pos++].toInt() and 0xFF
        val b1 = buffer[pos++].toInt() and 0xFF
        val b2 = buffer[pos++].toInt() and 0xFF
        val b3 = buffer[pos++].toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    actual fun readLong(): Long {
        checkAvailable(8)
        val hi = readInt().toLong() and 0xFFFFFFFFL
        val lo = readInt().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    actual fun readChar(): Char {
        checkAvailable(2)
        val b0 = buffer[pos++].toInt() and 0xFF
        val b1 = buffer[pos++].toInt() and 0xFF
        return ((b0 shl 8) or b1).toChar()
    }

    actual fun readDouble(): Double {
        return Double.fromBits(readLong())
    }

    actual fun readBoolean(): Boolean {
        checkAvailable(1)
        return buffer[pos++].toInt() != 0
    }

    actual fun readUTF(): String {
        // Java modified UTF-8: 2-byte length prefix (unsigned) + modified UTF-8 bytes
        checkAvailable(2)
        val utfLen = readUnsignedShort()
        checkAvailable(utfLen)
        val chars = CharArray(utfLen) // max possible chars
        var charCount = 0
        val start = pos
        val end = pos + utfLen

        while (pos < end) {
            val b = buffer[pos].toInt() and 0xFF
            if (b < 0x80) {
                // Single byte: 0xxxxxxx
                pos++
                chars[charCount++] = b.toChar()
            } else if (b and 0xE0 == 0xC0) {
                // Two bytes: 110xxxxx 10xxxxxx
                if (pos + 1 >= end) throw RuntimeException("Malformed modified UTF-8")
                val b2 = buffer[pos + 1].toInt() and 0xFF
                pos += 2
                chars[charCount++] = (((b and 0x1F) shl 6) or (b2 and 0x3F)).toChar()
            } else if (b and 0xF0 == 0xE0) {
                // Three bytes: 1110xxxx 10xxxxxx 10xxxxxx
                if (pos + 2 >= end) throw RuntimeException("Malformed modified UTF-8")
                val b2 = buffer[pos + 1].toInt() and 0xFF
                val b3 = buffer[pos + 2].toInt() and 0xFF
                pos += 3
                chars[charCount++] = (((b and 0x0F) shl 12) or ((b2 and 0x3F) shl 6) or (b3 and 0x3F)).toChar()
            } else {
                throw RuntimeException("Malformed modified UTF-8 at byte ${pos - start}")
            }
        }

        return String(chars, 0, charCount)
    }

    actual fun readFully(b: ByteArray) {
        checkAvailable(b.size)
        buffer.copyInto(b, 0, pos, pos + b.size)
        pos += b.size
    }

    actual fun read(b: ByteArray, off: Int, len: Int): Int {
        val remaining = buffer.size - pos
        if (remaining <= 0) return -1
        val toRead = minOf(len, remaining)
        buffer.copyInto(b, off, pos, pos + toRead)
        pos += toRead
        return toRead
    }

    actual fun available(): Int = buffer.size - pos

    actual fun close() {
        // No resources to release for in-memory buffer
    }

    private fun readUnsignedShort(): Int {
        val b0 = buffer[pos++].toInt() and 0xFF
        val b1 = buffer[pos++].toInt() and 0xFF
        return (b0 shl 8) or b1
    }

    private fun checkAvailable(n: Int) {
        if (pos + n > buffer.size) {
            throw RuntimeException("Unexpected end of stream: need $n bytes but only ${buffer.size - pos} available")
        }
    }
}
