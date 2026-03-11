package org.javarosa.core.util

/**
 * A Cache Table is a store that can be used to maintain a cache of objects
 * keyed by a dynamic type.
 *
 * Note: In the original Java implementation, this used WeakReferences for GC-friendly
 * caching. For KMP compatibility, this now uses strong references with manual eviction.
 * Callers should use [clear] to release entries when the cache is no longer needed.
 *
 * @author ctsims
 */
open class CacheTable<T, K> {
    private var totalAdditions = 0

    var currentTable: HashMap<T, K> = HashMap()

    open fun retrieve(key: T): K? {
        return currentTable[key]
    }

    open fun register(key: T, item: K) {
        currentTable[key] = item
        totalAdditions++
    }

    fun clear() {
        currentTable.clear()
    }
}
