@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import java.io.InputStream

/**
 * JVM implementation of PlatformInputStream.
 * Wraps java.io.InputStream.
 */
actual abstract class PlatformInputStream {
    actual open fun read(): Int = asJvmStream().read()
    actual open fun read(b: ByteArray): Int = asJvmStream().read(b)
    actual open fun read(b: ByteArray, off: Int, len: Int): Int = asJvmStream().read(b, off, len)
    actual open fun available(): Int = asJvmStream().available()
    actual open fun close() = asJvmStream().close()

    /** Access the underlying JVM InputStream. */
    abstract fun asJvmStream(): InputStream
}

/** Wraps an existing java.io.InputStream as a PlatformInputStream. */
class JvmInputStreamWrapper(private val stream: InputStream) : PlatformInputStream() {
    override fun asJvmStream(): InputStream = stream
}

/** Extension to wrap any java.io.InputStream as PlatformInputStream. */
fun InputStream.asPlatform(): PlatformInputStream = JvmInputStreamWrapper(this)
