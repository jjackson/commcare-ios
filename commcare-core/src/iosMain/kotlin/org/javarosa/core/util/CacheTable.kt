@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

/**
 * iOS implementation of CacheTable using a simple HashMap.
 * ARC handles memory management on iOS, so no WeakReference/cleaner thread needed.
 */
actual open class CacheTable<T, K> {
    private val table = HashMap<T, K>()

    actual open fun retrieve(key: T): K? {
        return table[key]
    }

    actual open fun register(key: T, item: K) {
        table[key] = item
    }

    actual fun clear() {
        table.clear()
    }
}
