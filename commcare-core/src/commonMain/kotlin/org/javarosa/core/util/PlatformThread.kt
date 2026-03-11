package org.javarosa.core.util

/**
 * Platform-specific thread utilities.
 *
 * On JVM: delegates to java.lang.Thread APIs.
 * On iOS: provides appropriate native equivalents.
 */
expect object PlatformThread {
    /**
     * Check if the current thread has been interrupted.
     * Used for cooperative cancellation of long-running operations.
     */
    fun interrupted(): Boolean

    /**
     * Sleep the current thread for the given number of milliseconds.
     */
    fun sleep(millis: Long)

    /**
     * Start a new thread that executes the given block.
     * Used primarily for crash handling (throwing exceptions in a new thread).
     */
    fun startThread(block: () -> Unit)
}
