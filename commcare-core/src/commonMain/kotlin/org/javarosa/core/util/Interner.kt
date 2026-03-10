package org.javarosa.core.util

/**
 * Cross-platform string interning interface.
 * JVM implementation uses WeakReference-based CacheTable.
 * iOS implementation can use a simple HashMap or no-op.
 */
interface Interner<K> {
    fun intern(k: K): K
}
