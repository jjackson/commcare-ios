package org.javarosa.core.util

/**
 * Cross-platform thread utilities.
 * On JVM, delegates to java.lang.Thread.
 * On iOS, provides single-threaded stubs.
 */
expect fun platformIsInterrupted(): Boolean
