package org.javarosa.core.util

/**
 * Cross-platform synchronized block.
 *
 * On JVM, delegates to kotlin.synchronized().
 * On iOS, uses PlatformLock (NSRecursiveLock).
 */
expect inline fun <R> platformSynchronized(lock: Any, block: () -> R): R
