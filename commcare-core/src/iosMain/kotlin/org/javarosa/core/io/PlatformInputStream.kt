@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

actual abstract class PlatformInputStream {
    actual abstract fun read(): Int
    actual open fun read(b: ByteArray): Int = read(b, 0, b.size)
    actual open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val c = read()
        if (c == -1) return -1
        b[off] = c.toByte()
        var i = 1
        while (i < len) {
            val c2 = read()
            if (c2 == -1) break
            b[off + i] = c2.toByte()
            i++
        }
        return i
    }
    actual open fun available(): Int = 0
    actual open fun skip(n: Long): Long {
        var remaining = n
        while (remaining > 0) {
            if (read() == -1) break
            remaining--
        }
        return n - remaining
    }
    actual open fun close() {}
}

private class IosByteArrayInputStream(private val data: ByteArray) : PlatformInputStream() {
    private var pos = 0

    override fun read(): Int {
        return if (pos < data.size) data[pos++].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return -1
        val count = minOf(len, data.size - pos)
        data.copyInto(b, off, pos, pos + count)
        pos += count
        return count
    }

    override fun available(): Int = data.size - pos

    override fun skip(n: Long): Long {
        val toSkip = minOf(n, (data.size - pos).toLong()).toInt()
        pos += toSkip
        return toSkip.toLong()
    }
}

actual fun createByteArrayInputStream(data: ByteArray): PlatformInputStream =
    IosByteArrayInputStream(data)
