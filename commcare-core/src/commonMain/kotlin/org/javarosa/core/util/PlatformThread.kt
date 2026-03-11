package org.javarosa.core.util

/**
 * Cross-platform thread utilities.
 * On JVM, delegates to java.lang.Thread.
 * On iOS, provides single-threaded stubs.
 */
expect fun platformIsInterrupted(): Boolean

/**
 * Cross-platform thread sleep.
 * On JVM, delegates to Thread.sleep().
 * On iOS, uses platform.posix.usleep.
 */
expect fun platformSleep(millis: Long)

/**
 * Cross-platform crash thread.
 * On JVM, starts a new Thread that throws the exception.
 * On iOS, throws immediately (single-threaded).
 */
expect fun platformStartCrashThread(exception: RuntimeException)
