@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * iOS implementation of PlatformInputStream.
 * Base abstract class for input byte streams on iOS.
 */
actual abstract class PlatformInputStream {
    actual open fun read(): Int = throw NotImplementedError("Subclass must override read()")
    actual open fun read(b: ByteArray): Int = read(b, 0, b.size)
    actual open fun read(b: ByteArray, off: Int, len: Int): Int {
        var count = 0
        for (i in off until off + len) {
            val byte = read()
            if (byte == -1) return if (count == 0) -1 else count
            b[i] = byte.toByte()
            count++
        }
        return count
    }
    actual open fun available(): Int = 0
    actual open fun close() {}
}
