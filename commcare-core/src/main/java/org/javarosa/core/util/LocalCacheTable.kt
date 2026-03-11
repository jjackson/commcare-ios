package org.javarosa.core.util

/**
 * A Local Cache Table is a store that can be used to maintain a cache of
 * objects keyed by a dynamic type.
 *
 * Local Cache Tables should be used for non-global caches. For global caches
 * use [CacheTable].
 *
 * Note: In the original Java implementation, this used WeakReferences for GC-friendly
 * caching. For KMP compatibility, this now uses strong references with manual eviction.
 * Callers should use [clear] to release entries when the cache is no longer needed.
 */
class LocalCacheTable<T, K> {
    private val currentTable = HashMap<T, K>()

    fun retrieve(key: T): K? {
        synchronized(this) {
            return currentTable[key]
        }
    }

    fun register(key: T, item: K) {
        synchronized(this) {
            currentTable[key] = item
        }
    }

    fun clear() {
        currentTable.clear()
    }
}
