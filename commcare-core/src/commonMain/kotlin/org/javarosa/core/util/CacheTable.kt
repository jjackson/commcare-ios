@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

/**
 * A cache table for storing key-value pairs with platform-specific memory management.
 *
 * On JVM: Uses WeakReference-backed storage with a background cleaner thread.
 * On iOS: Uses a simple HashMap (ARC handles memory management).
 */
expect open class CacheTable<T, K>() {
    open fun retrieve(key: T): K?
    open fun register(key: T, item: K)
    fun clear()
}
