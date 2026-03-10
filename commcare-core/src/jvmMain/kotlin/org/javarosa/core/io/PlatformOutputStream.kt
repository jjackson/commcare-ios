@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import java.io.OutputStream

/**
 * JVM implementation of PlatformOutputStream.
 * Wraps java.io.OutputStream.
 */
actual abstract class PlatformOutputStream {
    actual open fun write(b: Int) = asJvmStream().write(b)
    actual open fun write(b: ByteArray) = asJvmStream().write(b)
    actual open fun write(b: ByteArray, off: Int, len: Int) = asJvmStream().write(b, off, len)
    actual open fun flush() = asJvmStream().flush()
    actual open fun close() = asJvmStream().close()

    /** Access the underlying JVM OutputStream. */
    abstract fun asJvmStream(): OutputStream
}

/** Wraps an existing java.io.OutputStream as a PlatformOutputStream. */
class JvmOutputStreamWrapper(private val stream: OutputStream) : PlatformOutputStream() {
    override fun asJvmStream(): OutputStream = stream
}

/** Extension to wrap any java.io.OutputStream as PlatformOutputStream. */
fun OutputStream.asPlatform(): PlatformOutputStream = JvmOutputStreamWrapper(this)
