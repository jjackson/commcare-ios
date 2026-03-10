@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

/**
 * Cross-platform abstraction for an input byte stream.
 * On JVM, wraps java.io.InputStream. On iOS, wraps NSInputStream or byte buffer.
 */
expect abstract class PlatformInputStream() {
    open fun read(): Int
    open fun read(b: ByteArray): Int
    open fun read(b: ByteArray, off: Int, len: Int): Int
    open fun available(): Int
    open fun close()
}
