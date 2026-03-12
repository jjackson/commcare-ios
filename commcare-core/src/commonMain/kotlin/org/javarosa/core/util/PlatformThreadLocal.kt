package org.javarosa.core.util

/**
 * Cross-platform thread-local storage.
 * On JVM, wraps java.lang.ThreadLocal for multi-tenant thread isolation.
 * On iOS, uses a simple property (single-threaded, no isolation needed).
 */
expect class PlatformThreadLocal<T>(initialValue: () -> T) {
    fun get(): T
    fun set(value: T)
}
