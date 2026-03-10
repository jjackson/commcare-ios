@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * Cross-platform abstraction for an output byte stream.
 * On JVM, wraps java.io.OutputStream. On iOS, wraps NSOutputStream or byte buffer.
 */
expect abstract class PlatformOutputStream() {
    open fun write(b: Int)
    open fun write(b: ByteArray)
    open fun write(b: ByteArray, off: Int, len: Int)
    open fun flush()
    open fun close()
}
