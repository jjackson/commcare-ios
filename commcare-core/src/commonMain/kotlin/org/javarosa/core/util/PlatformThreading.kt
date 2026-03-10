package org.javarosa.core.util

/**
 * Check if the current thread has been interrupted (clearing the interrupted flag).
 * On JVM, maps to Thread.interrupted(). On non-JVM platforms, returns false.
 */
expect fun platformIsThreadInterrupted(): Boolean

/**
 * Sleep the current thread for the given number of milliseconds.
 * On JVM, maps to Thread.sleep(). On non-JVM platforms, uses platform-specific delay.
 *
 * @throws InterruptedException (or platform equivalent) if the sleep is interrupted
 */
expect fun platformSleep(millis: Long)
