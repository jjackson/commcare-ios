@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * iOS implementation of PlatformOutputStream.
 * Base abstract class for output byte streams on iOS.
 */
actual abstract class PlatformOutputStream {
    actual open fun write(b: Int) { throw NotImplementedError("Subclass must override write(Int)") }
    actual open fun write(b: ByteArray) { write(b, 0, b.size) }
    actual open fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            write(b[i].toInt() and 0xFF)
        }
    }
    actual open fun flush() {}
    actual open fun close() {}
}
