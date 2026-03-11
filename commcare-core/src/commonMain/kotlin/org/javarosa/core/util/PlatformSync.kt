package org.javarosa.core.util

/**
 * Cross-platform synchronization.
 * On JVM, delegates to kotlin.synchronized.
 * On iOS, no-op (single-threaded execution).
 */
expect inline fun <R> platformSynchronized(lock: Any, block: () -> R): R
